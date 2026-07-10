package com.cloudbrain.doctor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class SlotInventoryServiceTest {
    @Test
    void fetchSlotsReturnsRemoteDataOrEmptyList() {
        SlotInventoryService service = new SlotInventoryService("internal-key", "http://appointment");
        RestClient appointmentClient = Mockito.mock(RestClient.class);
        @SuppressWarnings("unchecked")
        RestClient.RequestHeadersUriSpec<?> getSpec = Mockito.mock(RestClient.RequestHeadersUriSpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);
        ReflectionTestUtils.setField(service, "appointmentClient", appointmentClient);

        doReturn(getSpec).when(appointmentClient).get();
        when(getSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(List.of(new SlotInventoryService.SlotDto("slot-1", 10, 1, 2, 7)), null)
                .thenThrow(new RestClientException("down"));

        assertThat(service.fetchSlots()).hasSize(1);
        assertThat(service.fetchSlots()).isEmpty();
        assertThat(service.fetchSlots()).isEmpty();
    }

    @Test
    void syncSlotAndBatchHandleSuccessGuardAndFailure() {
        SlotInventoryService service = new SlotInventoryService("internal-key", "http://appointment");
        RestClient appointmentClient = Mockito.mock(RestClient.class);
        RestClient.RequestBodyUriSpec postSpec = Mockito.mock(RestClient.RequestBodyUriSpec.class, Answers.RETURNS_SELF);
        RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);
        ReflectionTestUtils.setField(service, "appointmentClient", appointmentClient);

        when(appointmentClient.post()).thenReturn(postSpec);
        when(postSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity())
                .thenReturn(ResponseEntity.ok().build())
                .thenThrow(new RestClientException("down"))
                .thenReturn(ResponseEntity.ok().build())
                .thenThrow(new RestClientException("down"));

        service.syncSlot("slot-1", 20);
        service.syncSlot("slot-1", 20);
        service.syncSlotsBatch(null);
        service.syncSlotsBatch(List.of());
        service.syncSlotsBatch(List.of(java.util.Map.of("scheduleId", "slot-1", "capacity", 20)));
        service.syncSlotsBatch(List.of(java.util.Map.of("scheduleId", "slot-1", "capacity", 20)));

        verify(appointmentClient, times(4)).post();
        verify(postSpec, times(2)).body(java.util.Map.of("scheduleId", "slot-1", "capacity", 20));
        verify(postSpec, times(2)).body(List.of(java.util.Map.of("scheduleId", "slot-1", "capacity", 20)));
    }
}
