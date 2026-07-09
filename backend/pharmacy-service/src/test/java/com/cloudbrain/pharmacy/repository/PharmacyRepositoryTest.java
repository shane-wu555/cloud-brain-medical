package com.cloudbrain.pharmacy.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cloudbrain.pharmacy.entity.DrugReturnOrder;
import com.cloudbrain.pharmacy.entity.DrugReturnStatus;
import com.cloudbrain.pharmacy.entity.Prescription;
import com.cloudbrain.pharmacy.entity.PrescriptionItem;
import com.cloudbrain.pharmacy.entity.PrescriptionStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class PharmacyRepositoryTest {
    @Mock
    JdbcTemplate jdbc;

    @Test
    void drugsAddsKeywordAndStorageFilterAndMapsRows() throws Exception {
        ResultSet row = drugRow("drug-1", "DRUG-1", "Aspirin", 25, 10);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            Object[] args = java.util.Arrays.copyOfRange(invocation.getArguments(), 2, invocation.getArguments().length);
            assertThat(sql).contains("lower(d.drug_name) like ?");
            assertThat(sql).contains("and d.storage_condition = ?");
            assertThat(args).containsExactly("%aspirin%", "%aspirin%", "%aspirin%", "%aspirin%", "%aspirin%", "ROOM");
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        List<PharmacyRepository.Drug> result = repository.drugs(" aspirin ", "ROOM");

        assertThat(result).singleElement().satisfies(drug -> {
            assertThat(drug.id()).isEqualTo("drug-1");
            assertThat(drug.quantity()).isEqualTo(25);
        });
    }

    @Test
    void drugsByIdsReturnsEmptyWhenIdsBlank() {
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        assertThat(repository.drugsByIds(java.util.Arrays.asList("", " ", null))).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    void drugsByIdsRestoresRequestedOrder() throws Exception {
        ResultSet row1 = drugRow("drug-2", "DRUG-2", "Vitamin", 15, 5);
        ResultSet row2 = drugRow("drug-1", "DRUG-1", "Aspirin", 25, 10);
        doAnswer(invocation -> {
            Object[] args = java.util.Arrays.copyOfRange(invocation.getArguments(), 2, invocation.getArguments().length);
            assertThat(args).containsExactly("drug-1", "drug-2");
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row1, 0), mapper.mapRow(row2, 1));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        List<PharmacyRepository.Drug> result = repository.drugsByIds(List.of("drug-1", "drug-2", "drug-1"));

        assertThat(result).extracting(PharmacyRepository.Drug::id).containsExactly("drug-1", "drug-2");
    }

    @Test
    void drugThrowsWhenNoActiveDrugExists() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("drug-404"))).thenReturn(List.of());
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        assertThatThrownBy(() -> repository.drug("drug-404"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("drug does not exist or is inactive");
    }

    @Test
    void drugDemandObservationsClampLookbackAndMapRow() throws Exception {
        ResultSet row = demandRow("drug-1", "DRUG-1", "Aspirin", LocalDate.of(2026, 7, 9));
        doAnswer(invocation -> {
            assertThat((Integer) invocation.getArgument(2)).isEqualTo(1);
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), anyInt());
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        List<PharmacyRepository.DrugDemandObservation> result = repository.drugDemandObservations(0);

        assertThat(result).singleElement().satisfies(observation -> {
            assertThat(observation.drugCode()).isEqualTo("DRUG-1");
            assertThat(observation.demandDate()).isEqualTo(LocalDate.of(2026, 7, 9));
        });
    }

    @Test
    void updateWarningThresholdClampsNegativeValues() {
        when(jdbc.update(anyString(), eq(0), eq("drug-1"))).thenReturn(1);
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        int updated = repository.updateWarningThreshold("drug-1", -5);

        assertThat(updated).isEqualTo(1);
        verify(jdbc).update(anyString(), eq(0), eq("drug-1"));
    }

    @Test
    void insertPrescriptionWritesBaseAndEveryItem() {
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        repository.insertPrescription(prescription("pres-1", PrescriptionStatus.PENDING_PAYMENT, 2));

        verify(jdbc, times(3)).update(anyString(), any(Object[].class));
    }

    @Test
    void listBuildsFiltersPagingAndAttachesItems() throws Exception {
        ResultSet prescriptionRow = prescriptionRow("pres-1", "RX-1", PrescriptionStatus.PENDING_PAYMENT);
        ResultSet itemRow = itemRow("item-1", "pres-1", "drug-1");
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowMapper<?> mapper = invocation.getArgument(1);
            if (sql.startsWith("select * from prescription where 1 = 1")) {
                Object[] args = java.util.Arrays.copyOfRange(invocation.getArguments(), 2, invocation.getArguments().length);
                assertThat(args).containsExactly("patient-1", "PENDING_PAYMENT", "%Alice%", "%RX%", 20, 20);
                return List.of(mapper.mapRow(prescriptionRow, 0));
            }
            if (sql.contains("select * from prescription_item")) {
                return List.of(mapper.mapRow(itemRow, 0));
            }
            return List.of();
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        List<Prescription> result = repository.list("patient-1", "PENDING_PAYMENT", "Alice", "RX", 1, 20);

        assertThat(result).singleElement().satisfies(prescription -> {
            assertThat(prescription.patientId()).isEqualTo("patient-1");
            assertThat(prescription.items()).hasSize(1);
            assertThat(prescription.items().get(0).drugId()).isEqualTo("drug-1");
        });
    }

    @Test
    void listByStatusesBuildsStatusPlaceholdersAndPaging() throws Exception {
        ResultSet prescriptionRow = prescriptionRow("pres-1", "RX-1", PrescriptionStatus.CONFIRMED);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowMapper<?> mapper = invocation.getArgument(1);
            if (sql.startsWith("select * from prescription where 1 = 1")) {
                assertThat(sql).contains("status in (?,?)");
                Object[] args = java.util.Arrays.copyOfRange(invocation.getArguments(), 2, invocation.getArguments().length);
                assertThat(args).containsExactly("patient-1", "CONFIRMED", "PENDING_PAYMENT", 10, 0);
                return List.of(mapper.mapRow(prescriptionRow, 0));
            }
            if (sql.contains("select * from prescription_item")) {
                return List.of();
            }
            return List.of();
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        List<Prescription> result = repository.listByStatuses(
                "patient-1",
                List.of(PrescriptionStatus.CONFIRMED, PrescriptionStatus.PENDING_PAYMENT),
                null,
                null,
                0,
                10);

        assertThat(result).singleElement().satisfies(prescription -> assertThat(prescription.status()).isEqualTo(PrescriptionStatus.CONFIRMED));
    }

    @Test
    void deductStockReturnsBeforeAndAfterAndWritesFlow() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("drug-1"))).thenReturn(List.of(10));
        when(jdbc.update(anyString(), eq(3), eq("drug-1"), eq(3))).thenReturn(1);
        when(jdbc.update(anyString(), any(), eq("drug-1"), eq("pres-1"), eq("OUT"), eq(3), eq(10), eq(7), eq("pharmacist"), eq("prescription dispense")))
                .thenReturn(1);
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        PharmacyRepository.StockChange result = repository.deductStock("drug-1", "pres-1", 3, "pharmacist");

        assertThat(result.beforeQuantity()).isEqualTo(10);
        assertThat(result.afterQuantity()).isEqualTo(7);
    }

    @Test
    void deductStockThrowsWhenInventoryIsInsufficient() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("drug-1"))).thenReturn(List.of(10));
        when(jdbc.update(anyString(), eq(3), eq("drug-1"), eq(3))).thenReturn(0);
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        assertThatThrownBy(() -> repository.deductStock("drug-1", "pres-1", 3, "pharmacist"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("insufficient drug stock");
    }

    @Test
    void restoreStockAndAddStockReturnUpdatedStockChange() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("drug-1"))).thenReturn(List.of(10), List.of(12));
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        PharmacyRepository.StockChange restored = repository.restoreStock("drug-1", "pres-1", 2, "pharmacist", "return");
        PharmacyRepository.StockChange added = repository.addStock("drug-1", 5, "pharmacist", "purchase");

        assertThat(restored.afterQuantity()).isEqualTo(12);
        assertThat(added.afterQuantity()).isEqualTo(17);
    }

    @Test
    void createDrugReturnPersistsTotalAndReturnsLoadedOrder() {
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        PharmacyRepository repository = spy(new PharmacyRepository(jdbc));
        DrugReturnOrder loaded = order("return-1", DrugReturnStatus.RETURN_PENDING_REFUND);
        doReturn(loaded).when(repository).findDrugReturn(anyString());

        DrugReturnOrder result = repository.createDrugReturn(
                prescription("pres-1", PrescriptionStatus.PAID, 2),
                "doctor-1",
                "approved",
                "template-1",
                DrugReturnStatus.RETURN_PENDING_REFUND);

        assertThat(result).isSameAs(loaded);
        verify(jdbc).update(
                anyString(),
                anyString(),
                anyString(),
                eq("pres-1"),
                eq("patient-1"),
                eq("Patient"),
                eq("doctor-1"),
                eq("approved"),
                eq("template-1"),
                eq("RETURN_PENDING_REFUND"),
                eq(BigDecimal.valueOf(4)));
    }

    @Test
    void findDrugReturnThrowsWhenOrderMissing() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("return-404"))).thenReturn(List.of());
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        assertThatThrownBy(() -> repository.findDrugReturn("return-404"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("drug return does not exist");
    }

    @Test
    void completeDrugReturnAndMarkReturnRefundedReturnUpdateOutcome() {
        when(jdbc.update(anyString(), eq("cashier-1"), eq("refund-1"), eq("return-1"))).thenReturn(1);
        when(jdbc.update(anyString(), eq("pres-1"))).thenReturn(0);
        PharmacyRepository repository = new PharmacyRepository(jdbc);

        assertThat(repository.completeDrugReturn("return-1", "cashier-1", "refund-1")).isTrue();
        assertThat(repository.markReturnRefunded("pres-1")).isFalse();
    }

    @Test
    void stockChangeRecordIsConstructible() {
        PharmacyRepository.StockChange change = new PharmacyRepository.StockChange(10, 7);

        assertThat(change.beforeQuantity()).isEqualTo(10);
        assertThat(change.afterQuantity()).isEqualTo(7);
    }

    private ResultSet drugRow(String id, String code, String name, int quantity, int warningThreshold) throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getString("id")).thenReturn(id);
        when(rs.getString("code")).thenReturn(code);
        when(rs.getString("drug_name")).thenReturn(name);
        when(rs.getString("specification")).thenReturn("100mg");
        when(rs.getString("unit")).thenReturn("box");
        when(rs.getBigDecimal("unit_price")).thenReturn(BigDecimal.TEN);
        when(rs.getString("dosage_form")).thenReturn("tablet");
        when(rs.getString("storage_condition")).thenReturn("ROOM");
        when(rs.getInt("quantity")).thenReturn(quantity);
        when(rs.getInt("warning_threshold")).thenReturn(warningThreshold);
        return rs;
    }

    private ResultSet demandRow(String id, String code, String name, LocalDate date) throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getString("drug_id")).thenReturn(id);
        when(rs.getString("drug_code")).thenReturn(code);
        when(rs.getString("drug_name")).thenReturn(name);
        when(rs.getBigDecimal("unit_price")).thenReturn(BigDecimal.TEN);
        when(rs.getInt("quantity")).thenReturn(25);
        when(rs.getInt("warning_threshold")).thenReturn(10);
        when(rs.getObject("demand_date", LocalDate.class)).thenReturn(date);
        when(rs.getInt("dispensed_quantity")).thenReturn(3);
        return rs;
    }

    private ResultSet prescriptionRow(String id, String prescriptionNo, PrescriptionStatus status) throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getString("id")).thenReturn(id);
        when(rs.getString("prescription_no")).thenReturn(prescriptionNo);
        when(rs.getString("appointment_id")).thenReturn("appt-1");
        when(rs.getString("medical_record_id")).thenReturn("mr-1");
        when(rs.getString("patient_id")).thenReturn("patient-1");
        when(rs.getString("patient_name")).thenReturn("Patient");
        when(rs.getString("doctor_id")).thenReturn("doctor-1");
        when(rs.getString("diagnosis")).thenReturn("diagnosis");
        when(rs.getString("status")).thenReturn(status.name());
        when(rs.getBigDecimal("total_amount")).thenReturn(BigDecimal.valueOf(4));
        when(rs.getString("ai_record_id")).thenReturn("trace-1");
        when(rs.getString("ai_adoption_status")).thenReturn("FULL");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 7, 9, 10, 0)));
        when(rs.getTimestamp("dispensed_at")).thenReturn(null);
        when(rs.getTimestamp("returned_at")).thenReturn(null);
        when(rs.getString("dispensed_by")).thenReturn(null);
        when(rs.getString("returned_by")).thenReturn(null);
        return rs;
    }

    private ResultSet itemRow(String id, String prescriptionId, String drugId) throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getString("id")).thenReturn(id);
        when(rs.getString("prescription_id")).thenReturn(prescriptionId);
        when(rs.getString("drug_id")).thenReturn(drugId);
        when(rs.getString("drug_name")).thenReturn("Aspirin");
        when(rs.getInt("quantity")).thenReturn(2);
        when(rs.getString("dosage")).thenReturn("100mg");
        when(rs.getString("usage")).thenReturn("oral");
        when(rs.getString("frequency")).thenReturn("daily");
        when(rs.getInt("days")).thenReturn(7);
        when(rs.getBigDecimal("unit_price")).thenReturn(BigDecimal.ONE);
        when(rs.getBigDecimal("amount")).thenReturn(BigDecimal.valueOf(2));
        return rs;
    }

    private ResultSet drugReturnRow(String id, String prescriptionId, DrugReturnStatus status) throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        ResultSetMetaData metadata = org.mockito.Mockito.mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(9);
        when(metadata.getColumnLabel(1)).thenReturn("created_at");
        when(metadata.getColumnLabel(2)).thenReturn("verified_at");
        when(metadata.getColumnLabel(3)).thenReturn("completed_at");
        when(metadata.getColumnLabel(4)).thenReturn("pharmacist_id");
        when(metadata.getColumnLabel(5)).thenReturn("pharmacist_opinion");
        when(metadata.getColumnLabel(6)).thenReturn("cashier_id");
        when(metadata.getColumnLabel(7)).thenReturn("refund_order_id");
        when(metadata.getColumnLabel(8)).thenReturn("return_no");
        when(metadata.getColumnLabel(9)).thenReturn("prescription_no");
        when(metadata.getColumnName(anyInt())).thenAnswer(invocation -> metadata.getColumnLabel(invocation.getArgument(0)));
        when(rs.getMetaData()).thenReturn(metadata);
        when(rs.getString("id")).thenReturn(id);
        when(rs.getString("return_no")).thenReturn("RT-1");
        when(rs.getString("prescription_id")).thenReturn(prescriptionId);
        when(rs.getString("prescription_no")).thenReturn("RX-1");
        when(rs.getString("patient_id")).thenReturn("patient-1");
        when(rs.getString("patient_name")).thenReturn("Patient");
        when(rs.getString("doctor_id")).thenReturn("doctor-1");
        when(rs.getString("doctor_opinion")).thenReturn("approved");
        when(rs.getString("opinion_template")).thenReturn("template-1");
        when(rs.getString("status")).thenReturn(status.name());
        when(rs.getBigDecimal("total_amount")).thenReturn(BigDecimal.valueOf(4));
        when(rs.getString("pharmacist_id")).thenReturn("pharmacist-1");
        when(rs.getString("pharmacist_opinion")).thenReturn("ok");
        when(rs.getString("cashier_id")).thenReturn("cashier-1");
        when(rs.getString("refund_order_id")).thenReturn("refund-1");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 7, 9, 10, 0)));
        when(rs.getTimestamp("verified_at")).thenReturn(null);
        when(rs.getTimestamp("completed_at")).thenReturn(null);
        return rs;
    }

    private Prescription prescription(String id, PrescriptionStatus status, int itemCount) {
        List<PrescriptionItem> items = java.util.stream.IntStream.range(0, itemCount)
                .mapToObj(index -> new PrescriptionItem(
                        "item-" + index,
                        id,
                        "drug-" + index,
                        "Drug " + index,
                        2,
                        "100mg",
                        "oral",
                        "daily",
                        7,
                        null,
                        BigDecimal.ONE,
                        BigDecimal.valueOf(2)))
                .toList();
        return new Prescription(
                id,
                "RX-1",
                "appt-1",
                "mr-1",
                "patient-1",
                "Patient",
                "doctor-1",
                "diagnosis",
                status,
                BigDecimal.valueOf(itemCount * 2L),
                null,
                "trace-1",
                "FULL",
                null,
                LocalDateTime.of(2026, 7, 9, 10, 0),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                items);
    }

    private DrugReturnOrder order(String id, DrugReturnStatus status) {
        return new DrugReturnOrder(
                id,
                "RT-1",
                "pres-1",
                "RX-1",
                "patient-1",
                "Patient",
                "doctor-1",
                "approved",
                "template-1",
                status,
                BigDecimal.valueOf(4),
                "pharmacist-1",
                "ok",
                "cashier-1",
                "refund-1",
                LocalDateTime.of(2026, 7, 9, 10, 0),
                null,
                null,
                List.of());
    }
}
