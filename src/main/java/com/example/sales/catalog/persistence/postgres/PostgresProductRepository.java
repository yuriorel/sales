package com.example.sales.catalog.persistence.postgres;

import com.example.sales.catalog.domain.Dimensions;
import com.example.sales.catalog.domain.GuideSummary;
import com.example.sales.catalog.domain.Product;
import com.example.sales.catalog.domain.ProductData;
import com.example.sales.catalog.domain.ProductQuery;
import com.example.sales.catalog.domain.ProductRepository;
import com.example.sales.catalog.domain.ProductSummary;
import com.example.sales.common.api.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// [ProductRepository] on Postgres. Reads go through the `v_product_catalog` view.
/// Tags use the array operators `&&` (any) and `@>` (all), attributes use `jsonb`
/// containment `@>`, and dimensions are written as a `dimensions_cm` composite.
@Repository
class PostgresProductRepository implements ProductRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresProductRepository.class);
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };

    private static final String WRITABLE_VALUES = """
            :sku, :categoryId, :name, :brand, :description, :price, :currency, :rating,
            cast(:tags as text[]), cast(:imageUrls as text[]),
            case when :hasDimensions then row(:width, :height, :depth)::dimensions_cm end,
            cast(:attributes as jsonb), :userGuide, cast(:guideSections as text[])""";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    PostgresProductRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Page<ProductSummary> find(ProductQuery query) {
        var conditions = new ArrayList<String>();
        var params = new HashMap<String, Object>();
        if (query.category() != null) {
            conditions.add("category_slug = :category");
            params.put("category", query.category());
        }
        if (!query.tags().isEmpty()) {
            conditions.add(query.matchAll() ? "tags @> cast(:tags as text[])" : "tags && cast(:tags as text[])");
            params.put("tags", query.tags().toArray(String[]::new));
        }
        if (!query.attributes().isEmpty()) {
            conditions.add("attributes @> cast(:attributes as jsonb)");
            params.put("attributes", json.writeValueAsString(query.attributes()));
        }
        var where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        log.debug("Product query where={} params={}", where, params);

        var total = jdbc.sql("select count(*) from v_product_catalog" + where)
                .params(params)
                .query(Long.class)
                .single();
        var items = jdbc.sql("""
                        select id, sku, name, brand, price, currency, rating, tags, image_urls, category_slug, stock, in_stock
                          from v_product_catalog""" + where + """

                         order by %s
                         limit :size offset :offset""".formatted(query.sort().orderBy()))
                .params(params)
                .param("size", query.size())
                .param("offset", (long) query.page() * query.size())
                .query((rs, _) -> new ProductSummary(
                        rs.getLong("id"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getString("brand"),
                        rs.getBigDecimal("price"),
                        rs.getString("currency"),
                        rs.getBigDecimal("rating"),
                        textArray(rs, "tags"),
                        textArray(rs, "image_urls"),
                        rs.getString("category_slug"),
                        rs.getInt("stock"),
                        rs.getBoolean("in_stock")))
                .list();
        return Page.of(items, query.page(), query.size(), total);
    }

    @Override
    public Optional<Product> findById(long id) {
        return jdbc.sql("""
                        select id, sku, category_id, category_slug, category_name, name, brand, description,
                               price, currency, rating, tags, image_urls, width_cm, height_cm, depth_cm,
                               attributes, version, created_at, updated_at
                          from v_product_catalog
                         where id = :id""")
                .param("id", id)
                .query((rs, _) -> product(rs))
                .optional();
    }

    @Override
    public Optional<GuideSummary> guideSummary(long id) {
        return jdbc.sql("""
                        select guide_sections,
                               regexp_count(user_guide, '\\S+') as word_count,
                               (select count(*) from product_guide_chunks g where g.product_id = p.id) as chunk_count
                          from products p
                         where id = :id""")
                .param("id", id)
                .query((rs, _) -> new GuideSummary(
                        textArray(rs, "guide_sections"), rs.getInt("word_count"), rs.getInt("chunk_count")))
                .optional();
    }

    @Override
    public Optional<String> guide(long id) {
        return jdbc.sql("select user_guide from products where id = :id")
                .param("id", id)
                .query(String.class)
                .optional();
    }

    @Override
    public long create(ProductData data) {
        // one statement inserts the product and its inventory row
        return bind(jdbc.sql("""
                        with product as (
                            insert into products (sku, category_id, name, brand, description, price, currency, rating,
                                                  tags, image_urls, dimensions, attributes, user_guide, guide_sections)
                            values (%s)
                            returning id)
                        insert into inventory (product_id, quantity)
                        select id, 0 from product
                        returning product_id""".formatted(WRITABLE_VALUES)), data)
                .query(Long.class)
                .single();
    }

    @Override
    public boolean update(long id, ProductData data) {
        return bind(jdbc.sql("""
                        update products
                           set (sku, category_id, name, brand, description, price, currency, rating,
                                tags, image_urls, dimensions, attributes, user_guide, guide_sections) = (%s),
                               version = version + 1,
                               updated_at = now()
                         where id = :id""".formatted(WRITABLE_VALUES)), data)
                .param("id", id)
                .update() > 0;
    }

    @Override
    public boolean delete(long id) {
        return jdbc.sql("delete from products where id = :id").param("id", id).update() > 0;
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, ProductData data) {
        var dimensions = data.dimensions();
        return spec
                .param("sku", data.sku())
                .param("categoryId", data.categoryId())
                .param("name", data.name())
                .param("brand", data.brand(), Types.VARCHAR)
                .param("description", data.description())
                .param("price", data.price())
                .param("currency", data.currency())
                .param("rating", data.rating(), Types.NUMERIC)
                .param("tags", data.tags().toArray(String[]::new))
                .param("imageUrls", data.imageUrls().toArray(String[]::new))
                .param("hasDimensions", dimensions != null)
                .param("width", dimensions == null ? null : dimensions.width(), Types.NUMERIC)
                .param("height", dimensions == null ? null : dimensions.height(), Types.NUMERIC)
                .param("depth", dimensions == null ? null : dimensions.depth(), Types.NUMERIC)
                .param("attributes", json.writeValueAsString(data.attributes()))
                .param("userGuide", data.userGuide())
                .param("guideSections", data.guideSections().toArray(String[]::new));
    }

    private Product product(ResultSet rs) throws SQLException {
        var width = rs.getBigDecimal("width_cm");
        return new Product(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getLong("category_id"),
                rs.getString("category_slug"),
                rs.getString("category_name"),
                rs.getString("name"),
                rs.getString("brand"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getString("currency"),
                rs.getBigDecimal("rating"),
                textArray(rs, "tags"),
                textArray(rs, "image_urls"),
                width == null ? null : new Dimensions(width, rs.getBigDecimal("height_cm"), rs.getBigDecimal("depth_cm")),
                json.readValue(rs.getString("attributes"), JSON_OBJECT),
                rs.getLong("version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static List<String> textArray(ResultSet rs, String column) throws SQLException {
        return Arrays.asList((String[]) rs.getArray(column).getArray());
    }
}
