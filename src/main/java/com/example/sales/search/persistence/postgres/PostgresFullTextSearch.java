package com.example.sales.search.persistence.postgres;

import com.example.sales.common.config.PostgresAdapter;
import com.example.sales.search.domain.FullTextSearch;
import com.example.sales.search.domain.SearchHit;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

/// [FullTextSearch] on the generated, GIN-indexed `products.search_vector` column
/// (name weight A, brand B, description C, user guide D).
@PostgresAdapter
class PostgresFullTextSearch implements FullTextSearch {

    private final JdbcClient jdbc;

    PostgresFullTextSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SearchHit> search(String query, int limit) {
        return jdbc.sql("""
                        select p.id, p.name, p.brand, p.price, p.currency, ts_rank_cd(p.search_vector, q) as score
                        from products p,
                             websearch_to_tsquery('english', :query) q
                        where p.search_vector @@ q
                        order by score desc, p.id
                        limit :limit""")
                .param("query", query)
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
}
