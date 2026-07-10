package com.cloudbrain.doctor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.doctor.repository.DoctorCatalogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogControllersTest {
    private static final String MORNING = "\u4e0a\u5348";

    @Mock
    DoctorCatalogRepository repository;

    @Mock
    JdbcTemplate jdbcTemplate;

    @Test
    void baseCatalogControllerDelegatesJdbcQueriesAndCreatesItems() {
        BaseCatalogController controller = new BaseCatalogController(jdbcTemplate);
        BaseCatalogController.PricedItem pricedItem =
                new BaseCatalogController.PricedItem("REG1", "Normal", BigDecimal.TEN);
        BaseCatalogController.NamedItem namedItem =
                new BaseCatalogController.NamedItem("SELF", "Self Pay");
        BaseCatalogController.MedicalItem defaultItem =
                new BaseCatalogController.MedicalItem("ITEM1", "X-Ray", "CHECK", BigDecimal.ONE);
        BaseCatalogController.MedicalItem filteredItem =
                new BaseCatalogController.MedicalItem("ITEM2", "CT", "CHECK", BigDecimal.valueOf(2));

        when(jdbcTemplate.query(contains("registration_level"), any(RowMapper.class)))
                .thenReturn(List.of(pricedItem));
        when(jdbcTemplate.query(contains("settlement_category"), any(RowMapper.class)))
                .thenReturn(List.of(namedItem));
        when(jdbcTemplate.query(contains("medical_item"), any(RowMapper.class)))
                .thenReturn(List.of(defaultItem));
        when(jdbcTemplate.query(
                        contains("category = ?"),
                        any(RowMapper.class),
                        any()))
                .thenReturn(List.of(filteredItem));

        assertThat(controller.registrationLevels()).containsExactly(pricedItem);
        assertThat(controller.createRegistrationLevel(pricedItem)).isEqualTo(pricedItem);
        assertThat(controller.settlementCategories()).containsExactly(namedItem);
        assertThat(controller.createSettlementCategory(namedItem)).isEqualTo(namedItem);
        assertThat(controller.medicalItems(null)).isNotNull();
        assertThat(controller.medicalItems("CHECK")).isNotNull();
    }

    @Test
    void departmentAndCatalogSearchControllersMapRepositoryResults() {
        DepartmentController departmentController = new DepartmentController(repository);
        CatalogSearchController catalogSearchController = new CatalogSearchController(repository);
        DoctorCatalogRepository.Department department =
                new DoctorCatalogRepository.Department("dept-1", "Internal", "desc");
        DoctorCatalogRepository.Doctor doctor = new DoctorCatalogRepository.Doctor(
                "doctor-1",
                "0001",
                "Doctor",
                "Chief",
                "dept-1",
                "Internal",
                "Cardiology",
                "OUTPATIENT_DOCTOR",
                "room-1",
                "Room 1");
        when(repository.departments()).thenReturn(List.of(department));
        when(repository.createDepartment("Internal", "desc")).thenReturn(department);
        when(repository.patientSearch("cardio", 20))
                .thenReturn(new DoctorCatalogRepository.PatientSearchResult(List.of(department), List.of(doctor)));

        assertThat(departmentController.list())
                .extracting(DepartmentController.DepartmentDto::id)
                .containsExactly("dept-1");
        assertThat(departmentController.create(
                        new DepartmentController.CreateDepartmentRequest("Internal", "desc")))
                .extracting(DepartmentController.DepartmentDto::name)
                .isEqualTo("Internal");
        assertThat(catalogSearchController.patientSearch("cardio", 20).doctors())
                .extracting(CatalogSearchController.DoctorDto::id)
                .containsExactly("doctor-1");
    }

    @Test
    void doctorControllerCoversCrudAndEvents() {
        DoctorController controller = new DoctorController(repository);
        DoctorCatalogRepository.Doctor doctor = new DoctorCatalogRepository.Doctor(
                "doctor-1",
                "0001",
                "Doctor",
                "Chief",
                "dept-1",
                "Internal",
                "Cardiology",
                "OUTPATIENT_DOCTOR",
                "room-1",
                "Room 1");
        DoctorCatalogRepository.DoctorEvent event = new DoctorCatalogRepository.DoctorEvent(
                "event-1",
                "doctor-1",
                "Doctor",
                "Internal",
                "LEAVE",
                List.of(LocalDate.now().plusDays(8)),
                List.of(MORNING),
                "note");
        when(repository.doctors("dept-1", true)).thenReturn(List.of(doctor));
        when(repository.createDoctor("0001", "Doctor", "Chief", "dept-1", "OUTPATIENT_DOCTOR", "Cardiology"))
                .thenReturn(doctor);
        when(repository.findDoctor("doctor-1")).thenReturn(doctor);
        when(repository.updateDoctor("doctor-1", "Doctor", "Chief", "dept-1", "Cardiology"))
                .thenReturn(doctor);
        when(repository.doctorEvents()).thenReturn(List.of(event));
        when(repository.createDoctorEvent("doctor-1", "LEAVE", event.dates(), event.periods(), "note"))
                .thenReturn(event);
        when(repository.updateDoctorEvent("event-1", "doctor-1", "LEAVE", event.dates(), event.periods(), "note"))
                .thenReturn(event);

        assertThat(controller.list("dept-1", true)).hasSize(1);
        assertThat(controller.create(new DoctorController.CreateDoctorRequest(
                        " 0001 ", "Doctor", "Chief", "dept-1", "", "Cardiology")))
                .extracting(DoctorController.DoctorDto::employeeNo)
                .isEqualTo("0001");
        assertThat(controller.detail("doctor-1")).extracting(DoctorController.DoctorDto::id).isEqualTo("doctor-1");
        assertThat(controller.update(
                        "doctor-1",
                        new DoctorController.UpdateDoctorRequest("Doctor", "Chief", "dept-1", "Cardiology")))
                .extracting(DoctorController.DoctorDto::id)
                .isEqualTo("doctor-1");
        assertThat(controller.events()).hasSize(1);
        assertThat(controller.createEvent(
                        new DoctorController.DoctorEventRequest("doctor-1", "LEAVE", event.dates(), event.periods(), "note")))
                .extracting(DoctorController.DoctorEventDto::id)
                .isEqualTo("event-1");
        assertThat(controller.updateEvent(
                        "event-1",
                        new DoctorController.DoctorEventRequest("doctor-1", "LEAVE", event.dates(), event.periods(), "note")))
                .extracting(DoctorController.DoctorEventDto::id)
                .isEqualTo("event-1");

        controller.deleteEvent("event-1");
        verify(repository).deleteDoctorEvent("event-1");

        assertThatThrownBy(() -> controller.create(
                        new DoctorController.CreateDoctorRequest(" ", "Doctor", "Chief", "dept-1", "ADMIN", "Cardiology")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doctorControllerDefaultsBlankRoleToOutpatientDoctor() {
        DoctorController controller = new DoctorController(repository);
        DoctorCatalogRepository.Doctor doctor = new DoctorCatalogRepository.Doctor(
                "doctor-2",
                "0002",
                "Doctor 2",
                "Attending",
                "dept-1",
                "Internal",
                "Cardiology",
                "OUTPATIENT_DOCTOR",
                "room-1",
                "Room 1");
        when(repository.createDoctor("0002", "Doctor 2", "Attending", "dept-1", "OUTPATIENT_DOCTOR", "Cardiology"))
                .thenReturn(doctor);

        assertThat(controller.create(new DoctorController.CreateDoctorRequest(
                        "0002", "Doctor 2", "Attending", "dept-1", " ", "Cardiology")))
                .extracting(DoctorController.DoctorDto::roleType)
                .isEqualTo("OUTPATIENT_DOCTOR");
    }

    @Test
    void internalDoctorOperationsControllerRequiresKey() {
        InternalDoctorOperationsController controller =
                new InternalDoctorOperationsController(repository, "internal-key");
        DoctorCatalogRepository.DoctorOperationsStats stats =
                new DoctorCatalogRepository.DoctorOperationsStats(2, 2, 3, 67);
        when(repository.doctorOperationsStats(any())).thenReturn(stats);

        assertThatThrownBy(() -> controller.today("bad-key"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.today("internal-key")).isEqualTo(stats);
    }
}
