package com.example.sales.catalog.domain;

/// Sort orders of the product list. Every order ends with the id, so pages are stable.
public enum ProductSort {
    NAME,
    PRICE,
    PRICE_DESC,
    RATING,
    NEWEST;

    public String orderBy() {
        return switch (this) {
            case NAME -> "name, id";
            case PRICE -> "price, id";
            case PRICE_DESC -> "price desc, id";
            case RATING -> "rating desc nulls last, id";
            case NEWEST -> "created_at desc, id desc";
        };
    }
}
