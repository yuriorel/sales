package com.example.sales.search.domain;

import java.util.List;

/// Persistence port for keyword search over product name, brand, description and guide.
public interface FullTextSearch {

    /// Best matches first.
    ///
    /// @param query web-search syntax: words, `"quoted phrases"`, `or`, `-excluded`
    List<SearchHit> search(String query, int limit);
}
