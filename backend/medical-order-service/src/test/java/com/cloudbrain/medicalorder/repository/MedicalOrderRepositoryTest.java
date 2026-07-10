package com.cloudbrain.medicalorder.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.cloudbrain.medicalorder.domain.MedicalOrder;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MedicalOrderRepositoryTest {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);

    @Test
    void createFindAndStateTransitionsDelegateToJdbc() {
        MedicalOrderRepository repository = Mockito.spy(new MedicalOrderRepository(jdbc));
        MedicalOrder order = order("order-1", "WAITING_TRIAGE", null);
        when(jdbc.queryForObject(contains("count(*) from medical_order"), eq(Integer.class), eq("appt-1"), eq("ITEM"))).thenReturn(1);
        when(jdbc.query(contains("where mo.id = ?::uuid"), any(RowMapper.class), eq("order-1"))).thenReturn(List.of(order));
        when(jdbc.query(contains("from medical_order mo"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(order));
        when(jdbc.update(contains("payment_status = 'PAID'"), eq("order-1"))).thenReturn(1);
        when(jdbc.update(contains("set status = 'CALLED'"), eq("order-1"), eq("room-1"))).thenReturn(1);
        when(jdbc.update(contains("set status = 'IN_PROGRESS'"), eq("staff-1"), eq("order-1"), eq("room-1"))).thenReturn(1);
        when(jdbc.update(contains("set status = 'COMPLETED'"), eq("summary"), eq("HUMAN"), eq(null), eq("staff-1"), eq("order-1"), eq("room-1"))).thenReturn(1);
        when(jdbc.update(contains("set status = 'REPORT_PENDING'"), eq("pending"), eq("staff-1"), eq("order-1"), eq("room-1"))).thenReturn(1);
        doAnswer(invocation -> null).when(jdbc).query(eq("select pg_advisory_xact_lock(hashtext(?))"), any(org.springframework.jdbc.core.RowCallbackHandler.class), eq("medical-order:room-1"));
        when(jdbc.queryForObject("select urgency from medical_order where id = ?::uuid", String.class, "order-1")).thenReturn("ROUTINE");
        when(jdbc.queryForObject(contains("select coalesce(max(queue_number), 0) + 1"), eq(Integer.class), eq("room-1"))).thenReturn(3, 5);
        when(jdbc.update(contains("set room_id = ?"), eq("room-1"), eq(3), eq("AI"), eq("matched"), eq("order-1"))).thenReturn(1);
        when(jdbc.update(contains("missed_count = missed_count + 1"), eq(5), eq("order-1"), eq("room-1"))).thenReturn(1);

        assertThat(repository.create(order)).isEqualTo(order);
        assertThat(repository.existsActiveOrder("appt-1", "ITEM")).isTrue();
        assertThat(repository.find("CHECK", "WAITING", "patient-1", "appt-1")).containsExactly(order);
        assertThat(repository.findById("order-1")).contains(order);
        assertThat(repository.markPaid("order-1")).isTrue();
        assertThat(repository.assign("order-1", "room-1", "AI", "matched")).isTrue();
        assertThat(repository.call("order-1", "room-1")).isTrue();
        assertThat(repository.start("order-1", "room-1", "staff-1")).isTrue();
        repository.moveToTail("order-1", "room-1");
        assertThat(repository.complete("order-1", "room-1", "staff-1", "summary", "HUMAN", null)).isTrue();
        assertThat(repository.markReportPending("order-1", "room-1", "staff-1", "pending")).isTrue();
    }

    @Test
    void roomCandidatesAndStaffRoomAndMapperCoverMapping() throws Exception {
        MedicalOrderRepository repository = new MedicalOrderRepository(jdbc);
        MedicalOrderRepository.RoomCandidate candidate = new MedicalOrderRepository.RoomCandidate("room-1", "Room 1", "CT", "Floor 1", "CT-1", 10, 2);
        MedicalOrderRepository.StaffRoom staffRoom = new MedicalOrderRepository.StaffRoom("staff-1", "room-1");
        when(jdbc.query(contains("from examination_room r"), any(RowMapper.class), eq("CHECK"), eq("ITEM"))).thenReturn(List.of(candidate));
        when(jdbc.query(contains("from staff_room_assignment"), any(RowMapper.class), eq("staff-1"))).thenReturn(List.of(staffRoom));

        assertThat(repository.roomCandidates("CHECK", "ITEM")).containsExactly(candidate);
        assertThat(repository.staffRoom("staff-1")).contains(staffRoom);

        Class<?> mapperClass = Class.forName("com.cloudbrain.medicalorder.repository.MedicalOrderRepository$Mapper");
        java.lang.reflect.Constructor<?> constructor = mapperClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        @SuppressWarnings("unchecked")
        RowMapper<MedicalOrder> mapper = (RowMapper<MedicalOrder>) constructor.newInstance();
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getString("id")).thenReturn("order-1");
        when(rs.getString("appointment_id")).thenReturn("appt-1");
        when(rs.getString("patient_id")).thenReturn("patient-1");
        when(rs.getString("patient_name")).thenReturn("Patient");
        when(rs.getString("ordering_doctor_id")).thenReturn("doctor-1");
        when(rs.getString("order_type")).thenReturn("CHECK");
        when(rs.getString("item_code")).thenReturn("ITEM");
        when(rs.getString("item_name")).thenReturn("Item");
        when(rs.getString("purpose")).thenReturn("purpose");
        when(rs.getString("body_part")).thenReturn("HEAD");
        when(rs.getBigDecimal("amount")).thenReturn(BigDecimal.TEN);
        when(rs.getString("payment_status")).thenReturn("PAID");
        when(rs.getString("status")).thenReturn("WAITING");
        when(rs.getString("room_id")).thenReturn("room-1");
        when(rs.getString("room_name")).thenReturn("Room 1");
        when(rs.getString("room_location")).thenReturn("Floor 1");
        when(rs.getString("executing_staff_id")).thenReturn("staff-1");
        when(rs.getObject("queue_number")).thenReturn(1);
        when(rs.getString("urgency")).thenReturn("ROUTINE");
        when(rs.getInt("missed_count")).thenReturn(0);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));

        assertThat(mapper.mapRow(rs, 0).id()).isEqualTo("order-1");
    }

    private MedicalOrder order(String id, String status, String roomId) {
        return new MedicalOrder(id, "appt-1", "patient-1", "Patient", "doctor-1", "CHECK", "ITEM", "Item", "purpose", "HEAD",
                BigDecimal.TEN, "PAID", status, roomId, roomId == null ? null : "Room 1", roomId == null ? null : "Floor 1", "staff-1", 1, "ROUTINE",
                "AI", "matched", 0, null, null, null, null, null, LocalDateTime.now(), null, null);
    }
}
