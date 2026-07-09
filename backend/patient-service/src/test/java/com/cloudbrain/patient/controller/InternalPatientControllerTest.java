package com.cloudbrain.patient.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.patient.repository.PatientRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InternalPatientControllerTest {
    @Mock
    PatientRepository repository;

    @Test
    void verificationReturnsTrueWhenPatientExists() {
        InternalPatientController controller = new InternalPatientController(repository, "internal-key");
        when(repository.find("patient-1")).thenReturn(Optional.of(profile("patient-1")));

        assertThat(controller.verification("patient-1", "internal-key"))
                .isEqualTo(java.util.Map.of("realNameVerified", true));
    }

    @Test
    void ownershipDelegatesToRepository() {
        InternalPatientController controller = new InternalPatientController(repository, "internal-key");
        when(repository.owns("account-1", "patient-1")).thenReturn(true);

        assertThat(controller.ownership("patient-1", "account-1", "internal-key"))
                .isEqualTo(java.util.Map.of("owned", true));
        verify(repository).owns("account-1", "patient-1");
    }

    @Test
    void bindingReturnsBoundPatientId() {
        InternalPatientController controller = new InternalPatientController(repository, "internal-key");
        PatientRepository.PatientAccountState state = new PatientRepository.PatientAccountState(
                List.of(profile("patient-1")),
                profile("patient-1"));
        when(repository.accountState("account-1")).thenReturn(state);

        assertThat(controller.binding("account-1", "internal-key"))
                .isEqualTo(java.util.Map.of("hasBoundPatient", true, "boundPatientId", "patient-1"));
    }

    @Test
    void rejectsRequestWhenInternalApiKeyDoesNotMatch() {
        InternalPatientController controller = new InternalPatientController(repository, "internal-key");

        assertThatThrownBy(() -> controller.verification("patient-1", "wrong-key"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401));
    }

    private PatientRepository.PatientProfile profile(String id) {
        return new PatientRepository.PatientProfile(
                id,
                "account-1",
                "13800000000",
                "Patient",
                "ID_CARD",
                "110105199001012420",
                "FEMALE",
                LocalDate.of(1990, 1, 1),
                OffsetDateTime.of(2026, 7, 9, 10, 0, 0, 0, ZoneOffset.UTC),
                null);
    }
}
