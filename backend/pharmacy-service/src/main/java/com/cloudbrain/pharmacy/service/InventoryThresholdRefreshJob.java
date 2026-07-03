package com.cloudbrain.pharmacy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "inventory.ai-forecast.enabled", havingValue = "true")
public class InventoryThresholdRefreshJob {
    private static final Logger log = LoggerFactory.getLogger(InventoryThresholdRefreshJob.class);
    private final InventoryDemandForecastService forecastService;

    public InventoryThresholdRefreshJob(InventoryDemandForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @Scheduled(
            fixedDelayString = "${inventory.ai-forecast.refresh-ms:86400000}",
            initialDelayString = "${inventory.ai-forecast.initial-delay-ms:30000}")
    public void refresh() {
        InventoryDemandForecastService.ForecastRun run = forecastService.refreshDynamicThresholds();
        log.info("AI inventory thresholds refreshed: drugs={}, updated={}",
                run.items().size(), run.updatedCount());
    }
}
