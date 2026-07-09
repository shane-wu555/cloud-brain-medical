package com.cloudbrain.pharmacy.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryThresholdRefreshJobTest {
    @Mock
    InventoryDemandForecastService forecastService;

    @Test
    void refreshDelegatesToForecastService() {
        when(forecastService.refreshDynamicThresholds())
                .thenReturn(new InventoryDemandForecastService.ForecastRun(90, 14, 2, List.of()));
        InventoryThresholdRefreshJob job = new InventoryThresholdRefreshJob(forecastService);

        job.refresh();

        verify(forecastService).refreshDynamicThresholds();
    }
}
