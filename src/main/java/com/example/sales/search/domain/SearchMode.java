package com.example.sales.search.domain;

/// How [SearchService] finds products.
public enum SearchMode {
    /// keywords in name, brand, description and user guide (`tsvector`)
    FULLTEXT,
    /// meaning of the query compared with the embedded user guides (pgvector)
    SEMANTIC,
    /// both, merged with Reciprocal Rank Fusion
    HYBRID
}
