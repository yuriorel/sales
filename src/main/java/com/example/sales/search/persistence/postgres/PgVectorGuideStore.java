package com.example.sales.search.persistence.postgres;

import com.example.sales.common.config.PostgresAdapter;
import com.example.sales.search.domain.GuideVectorStore;
import com.example.sales.search.domain.SearchHit;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/// [GuideVectorStore] on pgvector: `vector(1536)` column with an HNSW index, queried
/// with the cosine distance operator `<=>`.
@PostgresAdapter
class PgVectorGuideStore implements GuideVectorStore {

    /// chunks fetched before grouping them by product
    private static final int CHUNKS_PER_PRODUCT = 5;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    PgVectorGuideStore(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void replace(long productId, List<EmbeddedChunk> chunks) {
        jdbc.sql("delete from product_guide_chunks where product_id = :productId")
                .param("productId", productId)
                .update();
        for (var embedded : chunks) {
            var chunk = embedded.chunk();
            jdbc.sql("""
                            insert into product_guide_chunks (product_id, chunk_index, content, metadata, embedding)
                            values (:productId, :chunkIndex, :content, cast(:metadata as jsonb), :embedding)""")
                    .param("productId", productId)
                    .param("chunkIndex", chunk.index())
                    .param("content", chunk.content())
                    .param("metadata", json.writeValueAsString(Map.of("section", chunk.section(), "words", chunk.wordCount())))
                    .param("embedding", new PGvector(embedded.embedding()))
                    .update();
        }
    }

    @Override
    public List<ChunkMatch> nearestChunks(float[] query, int limit, Long productId) {
        // a separate statement per case: an "is null or" condition would keep the planner off the HNSW index
        var where = productId == null ? "" : "where g.product_id = :productId\n";
        return jdbc.sql("""
                        select g.product_id, p.name, g.chunk_index, g.metadata ->> 'section' as section, g.content,
                               g.embedding <=> :query as distance
                        from product_guide_chunks g
                                 join products p on p.id = g.product_id
                        """ + where + """
                        order by distance
                        limit :limit""")
                .params(productId == null ? Map.of() : Map.of("productId", productId))
                .param("query", new PGvector(query))
                .param("limit", limit)
                .query((rs, _) -> new ChunkMatch(
                        rs.getLong("product_id"),
                        rs.getString("name"),
                        rs.getInt("chunk_index"),
                        rs.getString("section"),
                        rs.getString("content"),
                        rs.getDouble("distance")))
                .list();
    }

    @Override
    public List<SearchHit> nearestProducts(float[] query, int limit) {
        // the inner query can use the HNSW index; grouping keeps each product's closest chunk
        return jdbc.sql("""
                        with nearest as (
                            select product_id, embedding <=> :query as distance
                            from product_guide_chunks
                            order by embedding <=> :query
                            limit :candidates)
                        select p.id, p.name, p.brand, p.price, p.currency, 1 - min(n.distance) as score
                        from nearest n
                                 join products p on p.id = n.product_id
                        group by p.id
                        order by score desc
                        limit :limit""")
                .param("query", new PGvector(query))
                .param("candidates", limit * CHUNKS_PER_PRODUCT)
                .param("limit", limit)
                .query((rs, _) -> new SearchHit(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("brand"),
                        rs.getBigDecimal("price"),
                        rs.getString("currency"),
                        rs.getDouble("score")))
                .list();
    }

    @Override
    public List<Long> productsWithoutChunks() {
        return jdbc.sql("""
                        select id from products p
                        where not exists (select 1 from product_guide_chunks g where g.product_id = p.id)
                        order by id""")
                .query(Long.class)
                .list();
    }
}
