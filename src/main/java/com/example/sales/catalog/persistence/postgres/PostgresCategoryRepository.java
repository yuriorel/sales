package com.example.sales.catalog.persistence.postgres;

import com.example.sales.catalog.domain.Category;
import com.example.sales.catalog.domain.CategoryData;
import com.example.sales.catalog.domain.CategoryRepository;
import com.example.sales.catalog.domain.CategoryStats;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
class PostgresCategoryRepository implements CategoryRepository {

    private static final RowMapper<Category> CATEGORY = (rs, _) ->
            new Category(rs.getLong("id"), rs.getString("slug"), rs.getString("name"), rs.getString("description"));
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final JsonMapper json;

    PostgresCategoryRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<Category> findAll() {
        return jdbc.sql("select id, slug, name, description from categories order by name")
                .query(CATEGORY)
                .list();
    }

    @Override
    public Category create(CategoryData data) {
        return jdbc.sql("""
                        insert into categories (slug, name, description)
                        values (:slug, :name, :description)
                        returning id, slug, name, description""")
                .param("slug", data.slug())
                .param("name", data.name())
                .param("description", data.description(), Types.VARCHAR)
                .query(CATEGORY)
                .single();
    }

    @Override
    public Optional<Category> update(long id, CategoryData data) {
        return jdbc.sql("""
                        update categories
                           set slug = :slug, name = :name, description = :description
                         where id = :id
                        returning id, slug, name, description""")
                .param("id", id)
                .param("slug", data.slug())
                .param("name", data.name())
                .param("description", data.description(), Types.VARCHAR)
                .query(CATEGORY)
                .optional();
    }

    @Override
    public boolean delete(long id) {
        return jdbc.sql("delete from categories where id = :id").param("id", id).update() > 0;
    }

    @Override
    public List<CategoryStats> stats() {
        return jdbc.sql("""
                        select category_id, slug, name, product_count, min_price, max_price, avg_price,
                               total_stock, tags, summary
                          from v_category_stats
                         order by name""")
                .query((rs, _) -> new CategoryStats(
                        rs.getLong("category_id"),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getLong("product_count"),
                        rs.getBigDecimal("min_price"),
                        rs.getBigDecimal("max_price"),
                        rs.getBigDecimal("avg_price"),
                        rs.getLong("total_stock"),
                        Arrays.asList((String[]) rs.getArray("tags").getArray()),
                        json.readValue(rs.getString("summary"), JSON_OBJECT)))
                .list();
    }
}
