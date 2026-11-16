package com.example.sales.search.domain;

/// A piece of a user guide that is embedded on its own.
///
/// @param index   position within the guide, from 0
/// @param section the guide heading the chunk starts under
public record GuideChunk(int index, String section, String content) {

    public int wordCount() {
        return content.isBlank() ? 0 : content.strip().split("\\s+").length;
    }
}
