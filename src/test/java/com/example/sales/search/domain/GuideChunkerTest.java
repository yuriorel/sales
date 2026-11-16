package com.example.sales.search.domain;

import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GuideChunkerTest {

    @Test
    void shortGuideIsOneChunk() {
        var chunks = GuideChunker.chunk("""
                # Speaker User Guide
                ## Overview
                A loud speaker.
                """);

        assertThat(chunks).containsExactly(new GuideChunk(0, "Speaker User Guide",
                "# Speaker User Guide\n## Overview\nA loud speaker."));
    }

    @Test
    void longGuideIsSplitIntoBoundedOverlappingChunks() {
        var guide = "## Setup\n" + sentences(1, 40) + "## Maintenance\n" + sentences(41, 80);

        var chunks = GuideChunker.chunk(guide);

        assertThat(chunks).hasSizeGreaterThan(3);
        assertThat(chunks).extracting(GuideChunk::index).containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content().length())
                .isLessThanOrEqualTo(GuideChunker.CHUNK_SIZE));
        for (var i = 1; i < chunks.size(); i++) {
            var previous = chunks.get(i - 1).content();
            var tail = previous.substring(previous.length() - 40);
            assertThat(chunks.get(i).content()).as("chunk %d starts with the end of chunk %d", i, i - 1).contains(tail);
        }
        assertThat(chunks.getFirst().section()).isEqualTo("Setup");
        assertThat(chunks.getLast().section()).isEqualTo("Maintenance");
        assertThat(String.join("\n", chunks.stream().map(GuideChunk::content).toList())).contains("Sentence 1 ", "Sentence 80 ");
    }

    @Test
    void lineLongerThanChunkSizeIsKeptWhole() {
        var longLine = "word ".repeat(400).strip();

        var chunks = GuideChunker.chunk("intro\n" + longLine + "\noutro");

        assertThat(chunks).extracting(GuideChunk::content).anySatisfy(content -> assertThat(content).contains(longLine));
    }

    @Test
    void emptyGuideHasNoChunks() {
        assertThat(GuideChunker.chunk("\n\n")).isEmpty();
    }

    @Test
    void wordCountCountsWhitespaceSeparatedWords() {
        assertThat(new GuideChunk(0, "", " one two\nthree ").wordCount()).isEqualTo(3);
    }

    private static String sentences(int from, int to) {
        return IntStream.rangeClosed(from, to)
                .mapToObj(i -> "Sentence %d explains one more step of using the product safely.".formatted(i))
                .collect(Collectors.joining("\n", "", "\n"));
    }
}
