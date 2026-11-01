package com.example.sales.catalog.domain;

import java.util.List;

/// What a product's user guide contains, without the text itself.
///
/// @param embeddedChunks number of guide chunks with embeddings (used by semantic search)
public record GuideSummary(List<String> sections, int wordCount, int embeddedChunks) {
}
