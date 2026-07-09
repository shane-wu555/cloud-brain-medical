package com.cloudbrain.patient.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PatientRepositoryTest {
    @Mock
    JdbcTemplate jdbcTemplate;

    @Test
    void findByIdsReturnsEmptyListWhenInputIsBlank() {
        PatientRepository repository = new PatientRepository(jdbcTemplate);

        assertThat(repository.findByIds(java.util.Arrays.asList("", " ", null))).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createForAccountBindsExistingPatientWhenIdentityMatches() {
        PatientRepository repository = spy(new PatientRepository(jdbcTemplate));
        PatientRepository.PatientProfile existing = profile("patient-1");
        doReturn(Optional.of(existing)).when(repository).findByIdentity("Alice", "FEMALE", "ID_CARD", "110105199001012420");
        doReturn(false).when(repository).owns("account-1", "patient-1");
        when(jdbcTemplate.queryForObject("select count(*) from account_binding where account_id = ?", Integer.class, "account-1"))
                .thenReturn(1);
        doReturn(Optional.of(existing)).when(repository).find("patient-1");

        PatientRepository.PatientProfile result = repository.createForAccount(
                "account-1",
                "13800000000",
                "Alice",
                "ID_CARD",
                "110105199001012420",
                "FEMALE",
                LocalDate.of(1990, 1, 1));

        assertThat(result).isSameAs(existing);
        verify(jdbcTemplate).update(
                """
                insert into account_binding (account_id, patient_id, is_default)
                values (?, ?::uuid, false)
                on conflict (account_id, patient_id) do nothing
                """,
                "account-1",
                "patient-1");
    }

    @Test
    void createForAccountRejectsWhenAccountAlreadyHasFivePatients() {
        PatientRepository repository = spy(new PatientRepository(jdbcTemplate));
        doReturn(Optional.empty()).when(repository).findByIdentity("Alice", "FEMALE", "ID_CARD", "110105199001012420");
        when(jdbcTemplate.queryForObject("select count(*) from account_binding where account_id = ?", Integer.class, "account-1"))
                .thenReturn(5);

        assertThatThrownBy(() -> repository.createForAccount(
                "account-1",
                "13800000000",
                "Alice",
                "ID_CARD",
                "110105199001012420",
                "FEMALE",
                LocalDate.of(1990, 1, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("One account can add at most 5 patients");
    }

    @Test
    void createOfflineReturnsExistingProfileWhenIdentityMatches() {
        PatientRepository repository = spy(new PatientRepository(jdbcTemplate));
        PatientRepository.PatientProfile existing = profile("patient-2");
        doReturn(Optional.of(existing)).when(repository).findByIdentity("Bob", "MALE", "ID_CARD", "110105199001011235");

        PatientRepository.PatientProfile result = repository.createOffline(
                "ID_CARD",
                "110105199001011235",
                "Bob",
                "13900000000",
                "MALE",
                LocalDate.of(1990, 1, 1));

        assertThat(result).isSameAs(existing);
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void bindRejectsWhenPatientDoesNotBelongToAccount() {
        PatientRepository repository = spy(new PatientRepository(jdbcTemplate));
        doReturn(false).when(repository).owns("account-1", "patient-1");

        assertThatThrownBy(() -> repository.bind("account-1", "patient-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Patient is not added to this account");
    }

    @Test
    void accountStateCombinesProfilesAndBoundPatient() {
        PatientRepository repository = spy(new PatientRepository(jdbcTemplate));
        PatientRepository.PatientProfile profile = profile("patient-1");
        doReturn(List.of(profile)).when(repository).findByAccount("account-1");
        doReturn(Optional.of(profile)).when(repository).bound("account-1");

        PatientRepository.PatientAccountState state = repository.accountState("account-1");

        assertThat(state.profiles()).containsExactly(profile);
        assertThat(state.boundPatient()).isEqualTo(profile);
        assertThat(state.hasBoundPatient()).isTrue();
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
