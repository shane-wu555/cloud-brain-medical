package com.cloudbrain.patient.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.patient.repository.PatientRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class PatientControllerTest {
    @Mock
    PatientRepository repository;

    @Test
    void addProfileNormalizesFieldsAndInfersBirthDate() {
        PatientController controller = new PatientController(repository);
        PatientRepository.PatientProfile profile = profile("patient-1");
        when(repository.createForAccount(
                eq("account-1"),
                eq("13800000000"),
                eq("Alice"),
                eq("ID_CARD"),
                eq("110105199001012420"),
                eq("FEMALE"),
                eq(LocalDate.of(1990, 1, 1))))
                .thenReturn(profile);

        PatientRepository.PatientProfile result = controller.addProfile(
                new PatientController.AddPatientRequest(
                        " Alice ",
                        " id_card ",
                        "110105199001012420",
                        " female ",
                        null),
                authentication("account-1", "13800000000"));

        assertThat(result).isSameAs(profile);
        verify(repository).createForAccount(
                "account-1",
                "13800000000",
                "Alice",
                "ID_CARD",
                "110105199001012420",
                "FEMALE",
                LocalDate.of(1990, 1, 1));
    }

    @Test
    void createOfflineInfersGenderAndBirthDateFromIdCard() {
        PatientController controller = new PatientController(repository);
        PatientRepository.PatientProfile profile = profile("patient-2");
        when(repository.createOffline(
                eq("ID_CARD"),
                eq("110105199001011235"),
                eq("Bob"),
                eq("13900000000"),
                eq("MALE"),
                eq(LocalDate.of(1990, 1, 1))))
                .thenReturn(profile);

        PatientRepository.PatientProfile result = controller.createOffline(
                new PatientController.OfflinePatientRequest(
                        "ID_CARD",
                        "110105199001011235",
                        " Bob ",
                        "13900000000",
                        null,
                        null));

        assertThat(result).isSameAs(profile);
        verify(repository).createOffline(
                "ID_CARD",
                "110105199001011235",
                "Bob",
                "13900000000",
                "MALE",
                LocalDate.of(1990, 1, 1));
    }

    @Test
    void searchByIdsDeduplicatesAndTrimsInput() {
        PatientController controller = new PatientController(repository);
        List<PatientRepository.PatientProfile> profiles = List.of(profile("patient-1"));
        when(repository.findByIds(List.of("a", "b"))).thenReturn(profiles);

        List<PatientRepository.PatientProfile> result = controller.search(" a, , b , a ", null, null);

        assertThat(result).isSameAs(profiles);
        verify(repository).findByIds(List.of("a", "b"));
    }

    @Test
    void searchWithoutCriteriaRejectsRequest() {
        PatientController controller = new PatientController(repository);

        assertThatThrownBy(() -> controller.search(" ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provide idNumber or phone to search patients");
    }

    @Test
    void searchByPhoneDelegatesToRepository() {
        PatientController controller = new PatientController(repository);
        List<PatientRepository.PatientProfile> profiles = List.of(profile("patient-3"));
        when(repository.findByPhone("13800000000")).thenReturn(profiles);

        List<PatientRepository.PatientProfile> result = controller.search(null, "13800000000", null);

        assertThat(result).isSameAs(profiles);
        verify(repository).findByPhone("13800000000");
    }

    @Test
    void bindDelegatesToRepository() {
        PatientController controller = new PatientController(repository);
        PatientRepository.PatientProfile profile = profile("patient-4");
        when(repository.bind("account-1", "patient-4")).thenReturn(profile);

        PatientRepository.PatientProfile result = controller.bind(
                new PatientController.BindPatientRequest("patient-4"),
                authentication("account-1", "13800000000"));

        assertThat(result).isSameAs(profile);
        verify(repository).bind("account-1", "patient-4");
    }

    @Test
    void legacyVerifyWithoutIdCardUsesOtherTypeAndGeneratedUnknownId() {
        PatientController controller = new PatientController(repository);
        PatientRepository.PatientProfile profile = profile("patient-5");
        when(repository.createForAccount(
                eq("account-1"),
                eq("13800000000"),
                eq("Legacy User"),
                eq("OTHER"),
                argThat(id -> id.startsWith("UNKNOWN-")),
                eq("UNKNOWN"),
                eq(null)))
                .thenReturn(profile);

        PatientRepository.PatientProfile result = controller.legacyVerify(
                new PatientController.LegacyRealNameRequest(" Legacy User ", " "),
                authentication("account-1", "13800000000"));

        assertThat(result).isSameAs(profile);
        verify(repository).createForAccount(
                eq("account-1"),
                eq("13800000000"),
                eq("Legacy User"),
                eq("OTHER"),
                argThat(id -> id.startsWith("UNKNOWN-")),
                eq("UNKNOWN"),
                eq(null));
    }

    @Test
    void createOfflineRejectsInvalidIdCardBirthDate() {
        PatientController controller = new PatientController(repository);

        assertThatThrownBy(() -> controller.createOffline(
                new PatientController.OfflinePatientRequest(
                        "ID_CARD",
                        "110105199013011235",
                        "Test",
                        null,
                        null,
                        null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid ID card birth date");
    }

    @Test
    void addProfileRejectsUnsupportedGender() {
        PatientController controller = new PatientController(repository);

        assertThatThrownBy(() -> controller.addProfile(
                new PatientController.AddPatientRequest(
                        "Alice",
                        "OTHER",
                        "P123456",
                        "robot",
                        null),
                authentication("account-1", "13800000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported gender");
    }

    private JwtAuthenticationToken authentication(String subject, String phone) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", subject)
                .claim("phone", phone)
                .build();
        return new JwtAuthenticationToken(jwt);
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
