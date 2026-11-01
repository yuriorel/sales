package com.example.sales.catalog.domain;

import java.util.List;
import java.util.Optional;

/// Persistence port for categories.
public interface CategoryRepository {

    List<Category> findAll();

    Category create(CategoryData data);

    Optional<Category> update(long id, CategoryData data);

    /// @return `false` when there is no such category
    boolean delete(long id);

    List<CategoryStats> stats();
}
