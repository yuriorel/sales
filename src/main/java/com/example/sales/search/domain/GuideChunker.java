package com.example.sales.search.domain;

import java.util.List;
import java.util.stream.Gatherer;

/// Splits a Markdown user guide into overlapping chunks of about [#CHUNK_SIZE]
/// characters. Each chunk starts with the last [#OVERLAP] characters of the previous
/// one, so a sentence cut at a boundary is still embedded with its context.
public final class GuideChunker {

    public static final int CHUNK_SIZE = 1000;
    public static final int OVERLAP = 150;

    private GuideChunker() {
    }

    public static List<GuideChunk> chunk(String guide) {
        return guide.lines().gather(chunks(CHUNK_SIZE, OVERLAP)).toList();
    }

    /// A custom stream gatherer: consumes guide lines and emits chunks. Lines are
    /// never split, so a single line longer than `size` becomes one oversized chunk.
    static Gatherer<String, ?, GuideChunk> chunks(int size, int overlap) {
        class State {
            final StringBuilder text = new StringBuilder();
            String section = "";
            String chunkSection = "";
            int index;
            /// whether the buffer holds text beyond the overlap carried over from the previous chunk
            boolean fresh;
        }
        return Gatherer.ofSequential(
                State::new,
                Gatherer.Integrator.ofGreedy((state, line, downstream) -> {
                    if (state.fresh && state.text.length() + line.length() + 1 > size) {
                        downstream.push(new GuideChunk(state.index++, state.chunkSection, state.text.toString().strip()));
                        var tail = tail(state.text, overlap);
                        state.text.setLength(0);
                        state.text.append(tail);
                        state.fresh = false;
                    }
                    if (line.startsWith("#")) {
                        state.section = line.replaceFirst("^#+\\s*", "").strip();
                    }
                    if (!state.fresh && !line.isBlank()) {
                        state.chunkSection = state.section;
                        state.fresh = true;
                    }
                    state.text.append(line).append('\n');
                    return true;
                }),
                (state, downstream) -> {
                    if (state.fresh) {
                        downstream.push(new GuideChunk(state.index, state.chunkSection, state.text.toString().strip()));
                    }
                });
    }

    /// The last `overlap` characters of `text`, starting at a word boundary.
    private static String tail(CharSequence text, int overlap) {
        if (text.length() <= overlap) {
            return text.toString();
        }
        var start = text.length() - overlap;
        while (start < text.length() && !Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        return text.subSequence(start, text.length()).toString().stripLeading();
    }
}
