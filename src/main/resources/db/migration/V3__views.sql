-- Catalog read model: product with category, flattened dimensions and stock.
create view v_product_catalog as
select p.id,
       p.sku,
       p.name,
       p.brand,
       p.description,
       p.price,
       p.currency,
       p.rating,
       p.tags,
       p.image_urls,
       (p.dimensions).width   as width_cm,
       (p.dimensions).height  as height_cm,
       (p.dimensions).depth   as depth_cm,
       p.attributes,
       p.guide_sections,
       p.version,
       p.created_at,
       p.updated_at,
       c.id                   as category_id,
       c.slug                 as category_slug,
       c.name                 as category_name,
       coalesce(i.quantity, 0)     as stock,
       coalesce(i.quantity, 0) > 0 as in_stock
from products p
         join categories c on c.id = p.category_id
         left join inventory i on i.product_id = p.id;

-- Order read model: order with flattened shipping address and items as a JSON array.
create view v_order_summary as
select o.id,
       o.user_id,
       u.username,
       o.status,
       o.total,
       o.currency,
       (o.shipping_address).line1        as ship_line1,
       (o.shipping_address).line2        as ship_line2,
       (o.shipping_address).city         as ship_city,
       (o.shipping_address).region       as ship_region,
       (o.shipping_address).postal_code  as ship_postal_code,
       (o.shipping_address).country_code as ship_country_code,
       o.status_history,
       coalesce((select jsonb_agg(jsonb_build_object(
                                          'productId', oi.product_id,
                                          'productName', oi.product_name,
                                          'unitPrice', (oi.unit_price).amount,
                                          'currency', (oi.unit_price).currency,
                                          'quantity', oi.quantity)
                                  order by oi.product_name)
                 from order_items oi
                 where oi.order_id = o.id), '[]') as items,
       o.created_at,
       o.updated_at
from orders o
         join users u on u.id = o.user_id;

-- Admin statistics per category: counts, price range, distinct tags (lateral unnest) and a JSON summary.
create view v_category_stats as
select c.id                         as category_id,
       c.slug,
       c.name,
       count(p.id)                  as product_count,
       min(p.price)                 as min_price,
       max(p.price)                 as max_price,
       round(avg(p.price), 2)       as avg_price,
       coalesce(sum(i.quantity), 0) as total_stock,
       t.tags,
       jsonb_build_object(
               'productCount', count(p.id),
               'priceRange', jsonb_build_object('min', min(p.price), 'max', max(p.price)),
               'avgRating', round(avg(p.rating), 2),
               'tagCount', cardinality(t.tags)) as summary
from categories c
         left join products p on p.category_id = c.id
         left join inventory i on i.product_id = p.id
         left join lateral (
    select coalesce(array_agg(distinct tag order by tag), '{}') as tags
    from products p2
             cross join lateral unnest(p2.tags) as tag
    where p2.category_id = c.id
    ) t on true
group by c.id, c.slug, c.name, t.tags;
