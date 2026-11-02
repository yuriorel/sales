package com.example.sales.search.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/// Chunks and embeds product guides. The embedding pipeline is built in Phase 8.
@Service
public class GuideIndexer {

    private static final Logger log = LoggerFactory.getLogger(GuideIndexer.class);

    public void reindex(long productId) {
        log.info("Guide of product {} changed; re-embedding arrives in Phase 8", productId);
    }
}
