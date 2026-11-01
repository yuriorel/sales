package com.example.sales.catalog.domain;

import com.example.sales.common.cache.CacheAside;
import com.example.sales.common.error.NotFoundException;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    final CategoryRepository repository = mock(CategoryRepository.class);
    final CacheAside cache = mock(CacheAside.class);
    final CategoryService service = new CategoryService(repository, cache);
    final CategoryData data = new CategoryData("audio", "Audio", "Speakers and headphones");

    @Test
    @SuppressWarnings("unchecked")
    void listIsReadThroughTheCache() {
        var categories = List.of(new Category(1, "audio", "Audio", null));
        when(cache.get(eq("category:all"), any(TypeReference.class), eq(CategoryService.TTL), any(Supplier.class)))
                .thenAnswer(call -> call.<Supplier<?>>getArgument(3).get());
        when(repository.findAll()).thenReturn(categories);

        assertThat(service.list()).isEqualTo(categories);
    }

    @Test
    void createEvictsTheList() {
        when(repository.create(data)).thenReturn(new Category(1, "audio", "Audio", null));

        service.create(data);

        verify(cache).evict("category:all");
    }

    @Test
    void updateOfUnknownCategoryIsNotFound() {
        when(repository.update(9, data)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(9, data))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Category '9' was not found");
        verify(cache, never()).evict(any(String[].class));
    }

    @Test
    void deleteEvictsTheList() {
        when(repository.delete(1)).thenReturn(true);

        service.delete(1);

        verify(cache).evict("category:all");
    }

    @Test
    void deleteOfUnknownCategoryIsNotFound() {
        when(repository.delete(9)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(9)).isInstanceOf(NotFoundException.class);
    }
}
