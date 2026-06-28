package com.cloudbrain.patient.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PatientRepository {
    private static final int MAX_PATIENTS_PER_ACCOUNT = 5;
    private final JdbcTemplate jdbc;

    public PatientRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PatientProfile> find(String patientId) {
        List<PatientProfile> rows = jdbc.query("select * from patient_profile where id = ?", (rs, row) -> map(rs), patientId);
        return rows.stream().findFirst();
    }

    public List<PatientProfile> findByAccount(String accountId) {
        return jdbc.query("""
                select * from patient_profile
                where account_id = ?
                order by created_at desc
                """, (rs, row) -> map(rs), accountId);
    }

    public List<PatientProfile> findByPhone(String phone) {
        return jdbc.query("select * from patient_profile where phone = ? order by created_at desc", (rs, row) -> map(rs), phone);
    }

    public Optional<PatientProfile> findByIdNumber(String idType, String idNumber) {
        List<PatientProfile> rows = jdbc.query("""
                select * from patient_profile
                where id_type = ? and id_number = ?
                limit 1
                """, (rs, row) -> map(rs), idType, idNumber.trim().toUpperCase());
        return rows.stream().findFirst();
    }

    public Optional<PatientProfile> bound(String accountId) {
        List<PatientProfile> rows = jdbc.query("""
                select p.* from patient_profile p
                join account_patient_binding b on b.patient_id = p.id
                where b.account_id = ?
                """, (rs, row) -> map(rs), accountId);
        return rows.stream().findFirst();
    }

    public boolean owns(String accountId, String patientId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from patient_profile
                where id = ? and account_id = ?
                """, Integer.class, patientId, accountId);
        return count != null && count > 0;
    }

    @Transactional
    public PatientProfile createForAccount(
            String accountId,
            String phone,
            String name,
            String idType,
            String idNumber,
            String gender,
            LocalDate birthDate) {
        Integer count = jdbc.queryForObject("select count(*) from patient_profile where account_id = ?", Integer.class, accountId);
        if (count != null && count >= MAX_PATIENTS_PER_ACCOUNT) {
            throw new IllegalStateException("同一账号最多只能添加 5 个就诊人");
        }

        String id = "patient-" + UUID.randomUUID();
        try {
            jdbc.update("""
                    insert into patient_profile
                        (id, user_id, account_id, phone, name, id_type, id_number, id_card, gender, birth_date, real_name_verified, verified_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, now())
                    """, id, id, accountId, phone, name, idType, idNumber, "ID_CARD".equals(idType) ? idNumber : null, gender, birthDate);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("该证件已添加为就诊人");
        }

        if (count == null || count == 0) {
            bind(accountId, id);
        }
        return find(id).orElseThrow();
    }

    public PatientProfile createOffline(String idType, String idNumber, String name, String phone) {
        String id = "patient-offline-" + UUID.randomUUID();
        String normalizedId = idNumber.trim().toUpperCase();
        jdbc.update("""
                insert into patient_profile(id, user_id, phone, name, id_type, id_number, id_card, real_name_verified, verified_at)
                values (?, ?, ?, ?, ?, ?, ?, true, now())
                """, id, id,
                phone != null ? phone : "",
                name,
                idType,
                normalizedId,
                "ID_CARD".equals(idType) ? normalizedId : null);
        return find(id).orElseThrow();
    }

    public PatientProfile bind(String accountId, String patientId) {
        if (!owns(accountId, patientId)) {
            throw new IllegalArgumentException("就诊人不存在或不属于当前账号");
        }
        jdbc.update("""
                insert into account_patient_binding (account_id, patient_id)
                values (?, ?)
                on conflict (account_id) do update set patient_id = excluded.patient_id, bound_at = now()
                """, accountId, patientId);
        return find(patientId).orElseThrow();
    }

    public PatientAccountState accountState(String accountId) {
        List<PatientProfile> profiles = findByAccount(accountId);
        Optional<PatientProfile> bound = bound(accountId);
        if (profiles.size() == 1 && bound.isEmpty()) {
            PatientProfile profile = bind(accountId, profiles.get(0).id());
            return new PatientAccountState(profiles, profile);
        }
        return new PatientAccountState(profiles, bound.orElse(null));
    }

    private PatientProfile map(java.sql.ResultSet rs) throws java.sql.SQLException {
        String idType = hasColumn(rs, "id_type") ? rs.getString("id_type") : null;
        String idNumber = hasColumn(rs, "id_number") ? rs.getString("id_number") : rs.getString("id_card");
        return new PatientProfile(
                rs.getString("id"),
                hasColumn(rs, "account_id") ? rs.getString("account_id") : rs.getString("user_id"),
                rs.getString("phone"),
                rs.getString("name"),
                idType,
                idNumber,
                rs.getString("id_card"),
                rs.getString("gender"),
                rs.getDate("birth_date") == null ? null : rs.getDate("birth_date").toLocalDate(),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private boolean hasColumn(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (column.equalsIgnoreCase(meta.getColumnName(i))) return true;
        }
        return false;
    }

    public record PatientProfile(
            String id,
            String accountId,
            String phone,
            String name,
            String idType,
            String idNumber,
            String idCard,
            String gender,
            LocalDate birthDate,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        public String userId() {
            return id;
        }

        public boolean realNameVerified() {
            return true;
        }
    }

    public record PatientAccountState(List<PatientProfile> patients, PatientProfile boundPatient) {
        public boolean hasBoundPatient() {
            return boundPatient != null;
        }
    }
}
