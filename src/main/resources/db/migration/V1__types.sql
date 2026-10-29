-- Custom domains: reusable column types that carry their own constraints.

create domain email_address as citext
    check (value ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$');

create domain sku_code as varchar(32)
    check (value ~ '^[A-Z0-9-]{4,32}$');

create domain money_amount as numeric(12, 2)
    check (value >= 0);

create domain currency_code as char(3)
    check (value ~ '^[A-Z]{3}$');

create domain positive_qty as integer
    check (value > 0);

create domain rating as numeric(2, 1)
    check (value between 0 and 5);

create domain order_status as varchar(16)
    check (value in ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED'));

-- Composite types: structured values stored in a single column.

create type postal_address as (
    line1        varchar(200),
    line2        varchar(200),
    city         varchar(100),
    region       varchar(100),
    postal_code  varchar(20),
    country_code char(2)
);

create type dimensions_cm as (
    width  numeric(8, 2),
    height numeric(8, 2),
    depth  numeric(8, 2)
);

create type price_tag as (
    amount   money_amount,
    currency currency_code
);
