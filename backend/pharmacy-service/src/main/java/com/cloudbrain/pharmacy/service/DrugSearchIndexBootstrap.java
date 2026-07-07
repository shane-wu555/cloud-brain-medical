package com.cloudbrain.pharmacy.service;

import com.cloudbrain.pharmacy.repository.PharmacyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DrugSearchIndexBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(DrugSearchIndexBootstrap.class);

    private final PharmacyRepository repository;
    private final DrugSearchIndexService searchIndexService;

    public DrugSearchIndexBootstrap(PharmacyRepository repository, DrugSearchIndexService searchIndexService) {
        this.repository = repository;
        this.searchIndexService = searchIndexService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshDrugIndex() {
        try {
            int indexed = searchIndexService.reindex(repository.drugs(null, null));
            if (indexed > 0) {
                logger.info("Indexed {} drugs into Elasticsearch", indexed);
            }
        } catch (Exception error) {
            logger.warn("Drug Elasticsearch bootstrap indexing skipped: {}", error.getMessage());
        }
    }
}
