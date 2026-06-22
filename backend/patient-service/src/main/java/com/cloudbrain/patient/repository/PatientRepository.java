package com.cloudbrain.patient.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PatientRepository {
    private final JdbcTemplate jdbc;
    public PatientRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<PatientProfile> find(String userId) {
        List<PatientProfile> rows = jdbc.query("select * from patient_profile where user_id = ?", (rs, row) ->
                new PatientProfile(rs.getString("user_id"), rs.getString("phone"), rs.getString("name"),
                        rs.getString("id_card"), rs.getString("gender"),
                        rs.getDate("birth_date") == null ? null : rs.getDate("birth_date").toLocalDate(),
                        rs.getBoolean("real_name_verified"),
                        rs.getObject("verified_at", OffsetDateTime.class)), userId);
        return rows.stream().findFirst();
    }

    public PatientProfile ensure(String userId, String phone, String name) {
        jdbc.update("""
                insert into patient_profile (user_id, phone, name) values (?, ?, ?)
                on conflict (user_id) do update set phone = excluded.phone, updated_at = now()
                """, userId, phone, name);
        return find(userId).orElseThrow();
    }

    public Optional<PatientProfile> findByPhone(String phone) {
        List<PatientProfile> rows=jdbc.query("select * from patient_profile where phone=?",(rs,row)->map(rs),phone);
        return rows.stream().findFirst();
    }

    public PatientProfile createOffline(String phone,String name) {
        String id="patient-offline-"+UUID.randomUUID();
        jdbc.update("insert into patient_profile(user_id,phone,name) values (?,?,?)",id,phone,name);
        return find(id).orElseThrow();
    }

    public PatientProfile verify(String userId, String name, String idCard, String gender, LocalDate birthDate) {
        jdbc.update("""
                update patient_profile set name = ?, id_card = ?, gender = ?, birth_date = ?,
                    real_name_verified = true, verified_at = now(), updated_at = now()
                where user_id = ?
                """, name, idCard, gender, birthDate, userId);
        return find(userId).orElseThrow();
    }

    private PatientProfile map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PatientProfile(rs.getString("user_id"),rs.getString("phone"),rs.getString("name"),
                rs.getString("id_card"),rs.getString("gender"),
                rs.getDate("birth_date")==null?null:rs.getDate("birth_date").toLocalDate(),
                rs.getBoolean("real_name_verified"),rs.getObject("verified_at",OffsetDateTime.class));
    }

    public record PatientProfile(String userId, String phone, String name, String idCard, String gender,
            LocalDate birthDate, boolean realNameVerified, OffsetDateTime verifiedAt) {}
}
