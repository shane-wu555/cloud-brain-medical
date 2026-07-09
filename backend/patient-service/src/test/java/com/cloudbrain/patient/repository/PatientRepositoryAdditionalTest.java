package com.cloudbrain.patient.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PatientRepositoryAdditionalTest {
    @Mock
    JdbcTemplate jdbcTemplate;

    @Test
    void findMapsProfileWithoutOptionalColumns() throws Exception {
        PatientRepository repository = new PatientRepository(jdbcTemplate);
        var createdAt = OffsetDateTime.of(2026, 7, 9, 10, 0, 0, 0, ZoneOffset.UTC);
        when(jdbcTemplate.query(
                eq("select * from patient where id = ?::uuid"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("patient-1")))
                .thenAnswer(invocation -> mapSingle(invocation, patientResultSet(
                        Map.of(
                                "id", "patient-1",
                                "phone", "13800000000",
                                "name", "Alice",
                                "id_number", "ID-1",
                                "gender", "FEMALE",
                                "birth_date", java.sql.Date.valueOf(LocalDate.of(1990, 1, 1)),
                                "created_at", createdAt),
                        "id", "phone", "name", "id_number", "gender", "birth_date", "created_at")));

        Optional<PatientRepository.PatientProfile> result = repository.find("patient-1");

        assertThat(result).isPresent();
        assertThat(result.get().accountId()).isNull();
        assertThat(result.get().idType()).isNull();
        assertThat(result.get().updatedAt()).isNull();
        assertThat(result.get().createdAt()).isEqualTo(createdAt);
    }

    @Test
    void findByAccountMapsOptionalAccountAndUpdatedColumns() throws Exception {
        PatientRepository repository = new PatientRepository(jdbcTemplate);
        var createdAt = OffsetDateTime.of(2026, 7, 9, 10, 0, 0, 0, ZoneOffset.UTC);
        var updatedAt = OffsetDateTime.of(2026, 7, 9, 11, 0, 0, 0, ZoneOffset.UTC);
        when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.contains("from patient p"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("account-1")))
                .thenAnswer(invocation -> mapSingle(invocation, patientResultSet(
                        Map.of(
                                "id", "patient-2",
                                "account_id", "account-1",
                                "phone", "13900000000",
                                "name", "Bob",
                                "id_type", "PASSPORT",
                                "id_number", "P123",
                                "gender", "MALE",
                                "birth_date", java.sql.Date.valueOf(LocalDate.of(1988, 5, 2)),
                                "created_at", createdAt,
                                "updated_at", updatedAt),
                        "id", "account_id", "phone", "name", "id_type", "id_number", "gender", "birth_date",
                        "created_at", "updated_at")));

        List<PatientRepository.PatientProfile> result = repository.findByAccount("account-1");

        assertThat(result).singleElement().satisfies(profile -> {
            assertThat(profile.accountId()).isEqualTo("account-1");
            assertThat(profile.idType()).isEqualTo("PASSPORT");
            assertThat(profile.updatedAt()).isEqualTo(updatedAt);
        });
    }

    @Test
    void findByIdNumberNormalizesInputBeforeQuery() throws Exception {
        PatientRepository repository = new PatientRepository(jdbcTemplate);
        when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.contains("where id_type = ? and id_number = ?"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("ID_CARD"),
                eq("AB123")))
                .thenAnswer(invocation -> mapSingle(invocation, patientResultSet(
                        Map.of(
                                "id", "patient-3",
                                "phone", "13700000000",
                                "name", "Carol",
                                "id_type", "ID_CARD",
                                "id_number", "AB123",
                                "gender", "FEMALE",
                                "birth_date", java.sql.Date.valueOf(LocalDate.of(1995, 6, 3)),
                                "created_at", OffsetDateTime.of(2026, 7, 9, 12, 0, 0, 0, ZoneOffset.UTC)),
                        "id", "phone", "name", "id_type", "id_number", "gender", "birth_date", "created_at")));

        List<PatientRepository.PatientProfile> result = repository.findByIdNumber("ID_CARD", "ab123");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).idNumber()).isEqualTo("AB123");
    }

    @Test
    void findByIdsDeduplicatesValuesBeforeQuerying() {
        PatientRepository repository = new PatientRepository(jdbcTemplate);
        List<PatientRepository.PatientProfile> profiles = List.of(profile("patient-1"), profile("patient-2"));
        when(jdbcTemplate.query(
                eq("select * from patient where id in (?::uuid,?::uuid) order by created_at desc"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("patient-1"),
                eq("patient-2"))).thenReturn(profiles);

        List<PatientRepository.PatientProfile> result = repository.findByIds(
                List.of("patient-1", "patient-2", "patient-1", " "));

        assertThat(result).isSameAs(profiles);
    }

    @Test
    void createForAccountInsertsNewOnlinePatientWhenIdentityIsNew() {
        PatientRepository repository = spy(new PatientRepository(jdbcTemplate));
        PatientRepository.PatientProfile created = profile("patient-created");
        doReturn(Optional.empty()).when(repository).findByIdentity("Alice", "FEMALE", "ID_CARD", "AB123");
        when(jdbcTemplate.queryForObject(
                "select count(*) from account_binding where account_id = ?",
                Integer.class,
                "account-1")).thenReturn(1);
        doReturn(Optional.of(created)).when(repository).find(anyString());

        PatientRepository.PatientProfile result = repository.createForAccount(
                "account-1",
                "13800000000",
                "Alice",
                "ID_CARD",
                "ab123",
                "FEMALE",
                LocalDate.of(1990, 1, 1));

        assertThat(result).isSameAs(created);
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("insert into patient") && sql.contains("created_source")),
                anyString(),
                eq("13800000000"),
                eq("Alice"),
                eq("ID_CARD"),
                eq("AB123"),
                eq("FEMALE"),
                eq(LocalDate.of(1990, 1, 1)));
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("insert into account_binding")),
                eq("account-1"),
                anyString());
    }

    @Test
    void createOfflineInsertsNewOfflinePatientWhenIdentityIsNew() {
        PatientRepository repository = spy(new PatientRepository(jdbcTemplate));
        PatientRepository.PatientProfile created = profile("patient-offline");
        doReturn(Optional.empty()).when(repository).findByIdentity("Bob", "MALE", "PASSPORT", "P123");
        doReturn(Optional.of(created)).when(repository).find(anyString());

        PatientRepository.PatientProfile result = repository.createOffline(
                "PASSPORT",
                "p123",
                "Bob",
                "13900000000",
                "MALE",
                LocalDate.of(1988, 5, 2));

        assertThat(result).isSameAs(created);
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("insert into patient") && sql.contains("'OFFLINE'")),
                anyString(),
                eq("13900000000"),
                eq("Bob"),
                eq("PASSPORT"),
                eq("P123"),
                eq("MALE"),
                eq(LocalDate.of(1988, 5, 2)));
    }

    @Test
    void bindRefreshesBindingWhenPatientAlreadyBelongsToAccount() {
        PatientRepository repository = spy(new PatientRepository(jdbcTemplate));
        PatientRepository.PatientProfile profile = profile("patient-1");
        doReturn(true).when(repository).owns("account-1", "patient-1");
        doReturn(Optional.of(profile)).when(repository).find("patient-1");

        PatientRepository.PatientProfile result = repository.bind("account-1", "patient-1");

        assertThat(result).isSameAs(profile);
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("insert into account_binding")),
                eq("account-1"),
                eq("patient-1"));
    }

    @Test
    void ownsReturnsFalseWhenDatabaseCountIsNull() {
        PatientRepository repository = new PatientRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(
                """
                select count(*) from account_binding
                where account_id = ? and patient_id = ?::uuid
                """,
                Integer.class,
                "account-1",
                "patient-1")).thenReturn(null);

        assertThat(repository.owns("account-1", "patient-1")).isFalse();
    }

    @SuppressWarnings("unchecked")
    private List<PatientRepository.PatientProfile> mapSingle(
            org.mockito.invocation.InvocationOnMock invocation,
            java.sql.ResultSet resultSet) throws Exception {
        var rowMapper = (org.springframework.jdbc.core.RowMapper<PatientRepository.PatientProfile>) invocation.getArgument(1);
        return List.of(rowMapper.mapRow(resultSet, 0));
    }

    private java.sql.ResultSet patientResultSet(Map<String, Object> values, String... columns) throws Exception {
        java.sql.ResultSet resultSet = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData metaData = org.mockito.Mockito.mock(java.sql.ResultSetMetaData.class);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(columns.length);
        for (int index = 0; index < columns.length; index += 1) {
            when(metaData.getColumnName(index + 1)).thenReturn(columns[index]);
        }
        when(resultSet.getString(anyString())).thenAnswer(invocation -> {
            Object value = values.get(invocation.getArgument(0, String.class));
            return value == null ? null : value.toString();
        });
        when(resultSet.getDate(anyString())).thenAnswer(invocation ->
                (java.sql.Date) values.get(invocation.getArgument(0, String.class)));
        org.mockito.Mockito.doAnswer(invocation -> values.get(invocation.getArgument(0, String.class)))
                .when(resultSet)
                .getObject(anyString(), org.mockito.ArgumentMatchers.<Class<?>>any());
        return resultSet;
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
