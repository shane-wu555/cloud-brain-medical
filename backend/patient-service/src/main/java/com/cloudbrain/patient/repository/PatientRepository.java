package com.cloudbrain.patient.repository;

import com.cloudbrain.patient.cache.PatientCacheService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 适配新 patient schema：
 *   patient_profile       → patient（id 改为 uuid）
 *   account_patient_binding → account_binding（bound_at → created_at）
 */
@Repository
public class PatientRepository {
    private static final int MAX_PATIENTS_PER_ACCOUNT = 5;
    private final JdbcTemplate jdbc;
    private final PatientCacheService cache;

    public PatientRepository(JdbcTemplate jdbc, PatientCacheService cache) {
        this.jdbc = jdbc;
        this.cache = cache;
    }

    public Optional<PatientProfile> find(String patientId) {
        return jdbc.query(
                "select * from patient where id = ?::uuid",
                (rs, row) -> map(rs), patientId)
                .stream().findFirst();
    }

    public List<PatientProfile> findByAccount(String accountId) {
        return jdbc.query("""
                select p.*, b.account_id as account_id
                from patient p
                join account_binding b on b.patient_id = p.id
                where b.account_id = ?
                order by b.created_at desc, p.created_at desc
                """, (rs, row) -> map(rs), accountId);
    }

    public List<PatientProfile> findByPhone(String phone) {
        return jdbc.query(
                "select * from patient where phone = ? order by created_at desc",
                (rs, row) -> map(rs), phone);
    }

    public List<PatientProfile> findByIdNumber(String idType, String idNumber) {
        return jdbc.query("""
                select * from patient
                where id_type = ? and id_number = ?
                order by created_at desc
                """, (rs, row) -> map(rs), idType, normalizeIdNumber(idNumber));
    }

    public List<PatientProfile> findByIds(List<String> patientIds) {
        List<String> normalizedIds = patientIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) return List.of();
        String placeholders = normalizedIds.stream()
                .map(id -> "?::uuid")
                .collect(Collectors.joining(","));
        return jdbc.query(
                "select * from patient where id in (%s) order by created_at desc".formatted(placeholders),
                (rs, row) -> map(rs),
                normalizedIds.toArray());
    }

    public Optional<PatientProfile> findByIdentity(String name, String gender, String idType, String idNumber) {
        return jdbc.query("""
                select * from patient
                where name = ? and gender = ? and id_type = ? and id_number = ?
                order by created_at asc limit 1
                """, (rs, row) -> map(rs), name, gender, idType, normalizeIdNumber(idNumber))
                .stream().findFirst();
    }

    public Optional<PatientProfile> bound(String accountId) {
        return jdbc.query("""
                select p.*, b.account_id as account_id
                from patient p
                join account_binding b on b.patient_id = p.id
                where b.account_id = ?
                order by b.created_at desc, p.created_at desc
                limit 1
                """, (rs, row) -> map(rs), accountId)
                .stream().findFirst();
    }

    public boolean owns(String accountId, String patientId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from account_binding
                where account_id = ? and patient_id = ?::uuid
                """, Integer.class, accountId, patientId);
        return count != null && count > 0;
    }

    @Transactional
    public PatientProfile createForAccount(
            String accountId, String phone, String name,
            String idType, String idNumber, String gender, LocalDate birthDate) {
        String normalizedIdNumber = normalizeIdNumber(idNumber);
        Optional<PatientProfile> existing = findByIdentity(name, gender, idType, normalizedIdNumber);
        if (existing.isPresent()) {
            ensureCanAdd(accountId, existing.get().id());
            upsertBinding(accountId, existing.get().id());
            return find(existing.get().id()).orElseThrow();
        }
        ensureCanAdd(accountId, null);
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into patient (id, phone, name, id_type, id_number, gender, birth_date, real_name_verified, verified_at, created_source)
                values (?::uuid, ?, ?, ?, ?, ?, ?, true, now(), 'ONLINE')
                """, id, phone == null ? "" : phone, name, idType, normalizedIdNumber, gender, birthDate);
        upsertBinding(accountId, id);
        cache.evictAccount(accountId);
        return find(id).orElseThrow();
    }

    @Transactional
    public PatientProfile createOffline(
            String idType, String idNumber, String name,
            String phone, String gender, LocalDate birthDate) {
        String normalizedIdNumber = normalizeIdNumber(idNumber);
        Optional<PatientProfile> existing = findByIdentity(name, gender, idType, normalizedIdNumber);
        if (existing.isPresent()) return existing.get();
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into patient (id, phone, name, id_type, id_number, gender, birth_date, real_name_verified, verified_at, created_source)
                values (?::uuid, ?, ?, ?, ?, ?, ?, true, now(), 'OFFLINE')
                """, id, phone == null ? "" : phone, name, idType, normalizedIdNumber, gender, birthDate);
        return find(id).orElseThrow();
    }

    public PatientProfile bind(String accountId, String patientId) {
        if (!owns(accountId, patientId)) throw new IllegalArgumentException("Patient is not added to this account");
        upsertBinding(accountId, patientId);
        cache.evictAccount(accountId);
        return find(patientId).orElseThrow();
    }

    public PatientAccountState accountState(String accountId) {
        return cache.getAccountState(accountId)
                .orElseGet(() -> {
                    PatientAccountState state = new PatientAccountState(
                            findByAccount(accountId), bound(accountId).orElse(null));
                    cache.putAccountState(accountId, state);
                    return state;
                });
    }

    private void ensureCanAdd(String accountId, String existingPatientId) {
        if (existingPatientId != null && owns(accountId, existingPatientId)) return;
        Integer count = jdbc.queryForObject(
                "select count(*) from account_binding where account_id = ?", Integer.class, accountId);
        if (count != null && count >= MAX_PATIENTS_PER_ACCOUNT)
            throw new IllegalStateException("One account can add at most 5 patients");
    }

    private void upsertBinding(String accountId, String patientId) {
        jdbc.update("""
                insert into account_binding (account_id, patient_id, is_default)
                values (?, ?::uuid, false)
                on conflict (account_id, patient_id) do nothing
                """, accountId, patientId);
    }

    private String normalizeIdNumber(String idNumber) { return idNumber.trim().toUpperCase(); }

    private PatientProfile map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PatientProfile(
                rs.getString("id"),
                hasColumn(rs, "account_id") ? rs.getString("account_id") : null,
                rs.getString("phone"),
                rs.getString("name"),
                hasColumn(rs, "id_type") ? rs.getString("id_type") : null,
                rs.getString("id_number"),
                rs.getString("gender"),
                rs.getDate("birth_date") == null ? null : rs.getDate("birth_date").toLocalDate(),
                rs.getObject("created_at", OffsetDateTime.class),
                hasColumn(rs, "updated_at") ? rs.getObject("updated_at", OffsetDateTime.class) : null);
    }

    private boolean hasColumn(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (column.equalsIgnoreCase(meta.getColumnName(i))) return true;
        }
        return false;
    }

    public record PatientProfile(
            String id, String accountId, String phone, String name,
            String idType, String idNumber, String gender, LocalDate birthDate,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        public String userId() { return id; }
    }
    public record PatientAccountState(List<PatientProfile> profiles, PatientProfile bound) {
        public boolean hasBoundPatient() { return bound != null; }
        public PatientProfile boundPatient() { return bound; }
    }
}
