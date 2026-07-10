package com.cloudbrain.doctor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbrain.doctor.repository.DoctorCatalogRepository;
import com.cloudbrain.doctor.service.ScheduleInsightService;
import com.cloudbrain.doctor.service.SlotInventoryService;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@ExtendWith(MockitoExtension.class)
class ScheduleControllerBranchTest {
    private static final String MORNING = "\u4e0a\u5348";
    private static final String AFTERNOON = "\u4e0b\u5348";

    @Mock
    DoctorCatalogRepository repository;

    @Mock
    ScheduleInsightService scheduleInsightService;

    @Mock
    SlotInventoryService slotInventoryService;

    @Mock
    RestClient aiClient;

    @Mock
    RestClient.RequestBodyUriSpec aiPostSpec;

    @Mock(answer = Answers.RETURNS_SELF)
    RestClient.RequestBodySpec aiBodySpec;

    @Mock
    RestClient.ResponseSpec responseSpec;

    private final List<ScheduleController> controllers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        controllers.forEach(ScheduleController::shutdown);
    }

    @Test
    void helperMethodsCoverValueParsingSlotsAndDtoFallbacks() throws Exception {
        ScheduleController controller = controller();
        when(slotInventoryService.fetchSlots()).thenReturn(List.of(
                new SlotInventoryService.SlotDto(null, 1, 0, 0, 1),
                new SlotInventoryService.SlotDto(" ", 1, 0, 0, 1),
                new SlotInventoryService.SlotDto("slot-1", 10, 1, 2, 7),
                new SlotInventoryService.SlotDto("slot-1", 20, 1, 2, 17)));

        @SuppressWarnings("unchecked")
        Map<String, ScheduleController.SlotDto> slotsById =
                (Map<String, ScheduleController.SlotDto>) invoke(controller, "slotsById", new Class<?>[0]);
        assertThat(slotsById).containsOnlyKeys("slot-1");
        assertThat(invoke(controller, "periodLabel", new Class<?>[] {String.class}, "MORNING")).isEqualTo(MORNING);
        assertThat(invoke(controller, "periodLabel", new Class<?>[] {String.class}, "AFTERNOON")).isEqualTo(AFTERNOON);
        assertThat(invoke(controller, "periodLabel", new Class<?>[] {String.class}, "night")).isEqualTo("night");
        assertThat(invoke(controller, "stringValue", new Class<?>[] {Object.class}, new Object[] {null})).isNull();
        assertThat(invoke(controller, "intValue", new Class<?>[] {Object.class, int.class}, "12", 2)).isEqualTo(12);
        assertThat(invoke(controller, "intValue", new Class<?>[] {Object.class, int.class}, "bad", 2)).isEqualTo(2);
        assertThat(invoke(controller, "booleanValue", new Class<?>[] {Object.class, boolean.class}, null, true)).isEqualTo(true);
        assertThat(invoke(controller, "objectListSize", new Class<?>[] {Object.class}, "x")).isEqualTo(0);

        Map<String, ScheduleController.SlotDto> emptySlots = java.util.Collections.emptyMap();
        ScheduleController.ScheduleDto dto = (ScheduleController.ScheduleDto) invoke(
                controller,
                "dto",
                new Class<?>[] {DoctorCatalogRepository.Schedule.class, List.class, Map.class},
                schedule("schedule-1", "doctor-1", LocalDate.of(2030, 1, 1), "night"),
                List.of(
                        new DoctorCatalogRepository.ScheduleTimeSlot("slot-1", "schedule-1", LocalTime.of(8, 0), 8),
                        new DoctorCatalogRepository.ScheduleTimeSlot("slot-2", "schedule-1", LocalTime.of(8, 30), 8)),
                emptySlots);
        assertThat(dto.available()).isEqualTo(16);
        assertThat(dto.period()).isEqualTo("night");
    }

    @Test
    void aiHelpersCoverNullResponseExceptionAndForcePreviewBranches() throws Exception {
        ScheduleController controller = controller();
        when(scheduleInsightService.current()).thenReturn(new ScheduleInsightService.ScheduleInsight(
                100, false, Map.of(), Map.of(), Map.of(), "summary"));

        doReturn(aiPostSpec).when(aiClient).post();
        when(aiPostSpec.uri("/api/ai/schedule-suggestions")).thenReturn(aiBodySpec);
        when(aiBodySpec.contentType(any())).thenReturn(aiBodySpec);
        when(aiBodySpec.accept(any())).thenReturn(aiBodySpec);
        when(aiBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientResponseException(
                        "bad request",
                        HttpStatus.BAD_REQUEST.value(),
                        "bad request",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        null));

        assertThat(controller.aiSuggestions(null).fallbackUsed()).isTrue();

        ScheduleController.AiScheduleResponse emptyMapped = (ScheduleController.AiScheduleResponse) invoke(
                controller, "mapAiScheduleResponse", new Class<?>[] {Map.class}, new Object[] {null});
        assertThat(emptyMapped.suggestions()).isEmpty();

        LocalDate start = LocalDate.now().plusDays(7);
        LocalDate end = start.plusDays(6);
        DoctorCatalogRepository.Department department =
                new DoctorCatalogRepository.Department("dept-1", "Internal", "desc");
        when(repository.schedulingDepartments()).thenReturn(List.of(department));
        when(repository.doctorEvents(start, end)).thenReturn(List.of());
        when(repository.schedules(null, "dept-1", start, end)).thenReturn(fullyCoveredSchedules(start, end));
        when(repository.doctors("dept-1", false)).thenReturn(List.of());
        when(repository.outpatientRoomsWithDoctors("dept-1")).thenReturn(List.of());

        ScheduleController.AiScheduleResponse forced = controller.aiReplanPreview("dept-1", 24, true, 35, true, 25, true);
        assertThat(forced.suggestions()).isEmpty();
        assertThat(forced.provider()).isNull();
    }

    @Test
    void replanAndFallbackHelpersCoverMissingCoverageAndConflicts() throws Exception {
        ScheduleController controller = controller();
        LocalDate start = LocalDate.of(2030, 1, 1);
        LocalDate end = start.plusDays(1);
        DoctorCatalogRepository.Department department =
                new DoctorCatalogRepository.Department("dept-1", "Internal", "desc");
        DoctorCatalogRepository.OutpatientRoom room =
                new DoctorCatalogRepository.OutpatientRoom("room-1", "dept-1", "Room 1", "Floor 1");
        List<DoctorCatalogRepository.Schedule> schedules =
                List.of(schedule("s1", "doctor-1", start, MORNING));
        List<DoctorCatalogRepository.DoctorEvent> events =
                List.of(new DoctorCatalogRepository.DoctorEvent("event-1", "doctor-1", "Doctor", "Internal", "LEAVE", List.of(start), List.of(MORNING), "note"));

        when(repository.schedules(null, "dept-1", start, end)).thenReturn(schedules);
        when(repository.outpatientRoomsWithDoctors("dept-1")).thenReturn(List.of(room));

        @SuppressWarnings("unchecked")
        List<DoctorCatalogRepository.Department> needing = (List<DoctorCatalogRepository.Department>) invoke(
                controller,
                "departmentsNeedingReplan",
                new Class<?>[] {List.class, LocalDate.class, LocalDate.class, List.class},
                List.of(department),
                start,
                end,
                events);
        assertThat(needing).containsExactly(department);

        ScheduleController.AiDoctorCandidate unavailable = new ScheduleController.AiDoctorCandidate(
                "doctor-1", "Doctor", "", "dept-1", "room-1", "Room 1", "", 20, 10,
                List.of(new ScheduleController.DoctorUnavailableSlot("2030-01-01", MORNING, "LEAVE")));
        ScheduleController.AiDoctorCandidate available = new ScheduleController.AiDoctorCandidate(
                "doctor-2", "Doctor 2", "", "dept-1", "room-1", "Room 1", "", 20, 8, List.of());
        ScheduleController.AiScheduleDemand morning =
                new ScheduleController.AiScheduleDemand("dept-1", "room-1", "Room 1", "2030-01-01", MORNING, 30, 10);
        ScheduleController.AiScheduleDemand afternoon =
                new ScheduleController.AiScheduleDemand("dept-1", "room-1", "Room 1", "2030-01-01", AFTERNOON, 18, 8);

        ScheduleController.AiScheduleResponse fallback = (ScheduleController.AiScheduleResponse) invoke(
                controller,
                "fallbackAiSuggestions",
                new Class<?>[] {ScheduleController.AiScheduleRequest.class, String.class},
                new ScheduleController.AiScheduleRequest(List.of(unavailable, available), List.of(morning, afternoon), "bg"),
                "notice");
        assertThat(fallback.suggestions()).isNotEmpty();
        assertThat((boolean) invoke(
                        controller,
                        "preferSeniorOnWeekdayPeak",
                        new Class<?>[] {ScheduleController.AiScheduleDemand.class},
                        morning))
                .isTrue();
    }

    @Test
    void publishBatchSkipsInvalidAndConflictingSuggestionsAcrossDepartments() {
        ScheduleController controller = controller();
        when(repository.outpatientRoomForDoctor("doctor-1"))
                .thenReturn(new DoctorCatalogRepository.OutpatientRoom("room-1", "dept-1", "Room 1", "Floor 1"));
        when(repository.outpatientRoomForDoctor("doctor-2"))
                .thenReturn(new DoctorCatalogRepository.OutpatientRoom("room-2", "dept-2", "Room 2", "Floor 2"));
        when(repository.createSchedule("doctor-1", "dept-1", LocalDate.parse("2030-01-01"), MORNING, 20))
                .thenReturn(schedule("schedule-1", "doctor-1", LocalDate.parse("2030-01-01"), MORNING));
        when(repository.createSchedule("doctor-2", "dept-2", LocalDate.parse("2030-01-02"), AFTERNOON, 18))
                .thenReturn(schedule("schedule-2", "doctor-2", LocalDate.parse("2030-01-02"), AFTERNOON));
        when(repository.timeSlots("schedule-1"))
                .thenReturn(List.of(new DoctorCatalogRepository.ScheduleTimeSlot("slot-1", "schedule-1", LocalTime.of(8, 0), 10)));
        when(repository.timeSlots("schedule-2"))
                .thenReturn(List.of(new DoctorCatalogRepository.ScheduleTimeSlot("slot-2", "schedule-2", LocalTime.of(14, 0), 9)));

        List<ScheduleController.ScheduleDto> published = controller.publishAiSuggestions(
                new ScheduleController.PublishAiScheduleBatchRequest(
                        "ai-record",
                        List.of(
                                new ScheduleController.PublishAiScheduleRequest("ai", null, "Doctor", "dept-1", "room-1", "Room 1", "2030-01-01", MORNING, 20, true),
                                new ScheduleController.PublishAiScheduleRequest("ai", "doctor-1", "Doctor", "dept-1", "room-1", "Room 1", "2030-01-01", MORNING, 20, true),
                                new ScheduleController.PublishAiScheduleRequest("ai", "doctor-1", "Doctor", "dept-1", "room-1", "Room 1", "2030-01-01", MORNING, 20, true),
                                new ScheduleController.PublishAiScheduleRequest("ai", "doctor-2", "Doctor 2", "dept-2", "room-x", "Room X", "2030-01-02", AFTERNOON, 18, true))));

        assertThat(published).hasSize(2);
        verify(repository).deleteSchedulesForDepartmentWindow("dept-1", LocalDate.parse("2030-01-01"), LocalDate.parse("2030-01-01"));
        verify(repository).deleteSchedulesForDepartmentWindow("dept-2", LocalDate.parse("2030-01-02"), LocalDate.parse("2030-01-02"));
        verify(slotInventoryService).syncSlotsBatch(any());
    }

    @Test
    void augmentationAndTaskTrimCoverNoOpAndRemovalBranches() throws Exception {
        ScheduleController controller = controller();
        ScheduleController.AiScheduleDemand demand =
                new ScheduleController.AiScheduleDemand("dept-1", "room-1", "Room 1", "2030-01-01", MORNING, 20, 10);
        ScheduleController.AiScheduleSuggestion suggestion =
                new ScheduleController.AiScheduleSuggestion("s1", "doctor-1", "Doctor", "dept-1", "room-1", "Room 1", "2030-01-01", MORNING, 20, true);
        ScheduleController.AiScheduleResponse response =
                new ScheduleController.AiScheduleResponse("ai-1", List.of(suggestion), "provider", null, false, List.of(), "bg");

        ScheduleController.AiScheduleResponse unchanged = (ScheduleController.AiScheduleResponse) invoke(
                controller,
                "augmentPartialAiSuggestions",
                new Class<?>[] {ScheduleController.AiScheduleRequest.class, ScheduleController.AiScheduleResponse.class},
                new ScheduleController.AiScheduleRequest(List.of(), List.of(demand), "bg"),
                response);
        assertThat(unchanged).isEqualTo(response);

        @SuppressWarnings("unchecked")
        Map<String, ScheduleController.AiScheduleTask> tasks =
                (Map<String, ScheduleController.AiScheduleTask>) ReflectionTestUtils.getField(controller, "aiTasks");
        for (int i = 0; i < 101; i++) {
            tasks.put(
                    "task-" + i,
                    new ScheduleController.AiScheduleTask(
                            "task-" + i,
                            "COMPLETED",
                            "done",
                            LocalDateTime.now().minusMinutes(101 - i),
                            LocalDateTime.now().minusMinutes(101 - i),
                            null));
        }
        invoke(controller, "trimAiTasks", new Class<?>[0]);
        assertThat(tasks.size()).isLessThanOrEqualTo(100);
    }

    @Test
    void candidateSelectionHelpersCoverEmptyAndFilteredBranches() throws Exception {
        ScheduleController controller = controller();
        ScheduleController.AiDoctorCandidate candidate = new ScheduleController.AiDoctorCandidate(
                "doctor-1",
                "Doctor",
                "",
                "dept-1",
                "room-1",
                "Room 1",
                "",
                20,
                10,
                List.of(new ScheduleController.DoctorUnavailableSlot("2030-01-01", MORNING, "LEAVE")));
        ScheduleController.AiScheduleDemand demand =
                new ScheduleController.AiScheduleDemand("dept-1", "room-2", "Room 2", "2030-01-01", MORNING, 20, 10);

        @SuppressWarnings("unchecked")
        List<ScheduleController.AiDoctorCandidate> available = (List<ScheduleController.AiDoctorCandidate>) invoke(
                controller,
                "availableCandidates",
                new Class<?>[] {List.class, ScheduleController.AiScheduleDemand.class, java.util.Set.class, java.util.Set.class},
                List.of(candidate),
                demand,
                java.util.Set.of("doctor-1:2030-01-01:上午"),
                java.util.Set.of("room-1:2030-01-01:上午"));
        assertThat(available).isEmpty();

        assertThat(invoke(
                        controller,
                        "selectScheduleCandidate",
                        new Class<?>[] {List.class, ScheduleController.AiScheduleDemand.class, Map.class},
                        List.of(),
                        demand,
                        Map.of()))
                .isNull();
    }

    @Test
    void utilityBranchesCoverFalseAndDefaultPaths() throws Exception {
        ScheduleController controller = controller();
        DoctorCatalogRepository.Schedule schedule = schedule("schedule-1", "doctor-1", LocalDate.of(2030, 1, 1), MORNING);
        ScheduleController.AiDoctorCandidate candidate = new ScheduleController.AiDoctorCandidate(
                "doctor-1", "Doctor", "", "dept-1", "room-1", "Room 1", "", 20, 10, List.of());
        ScheduleController.AiScheduleDemand weekendDemand =
                new ScheduleController.AiScheduleDemand("dept-1", "room-1", "Room 1", "2030-01-05", MORNING, 10, 5);

        assertThat(invoke(controller, "hasPublishRoomMismatch", new Class<?>[] {String.class, DoctorCatalogRepository.OutpatientRoom.class},
                        "", new DoctorCatalogRepository.OutpatientRoom("room-1", "dept-1", "Room 1", "Floor 1")))
                .isEqualTo(false);
        assertThat(invoke(controller, "preferSeniorOnWeekdayPeak", new Class<?>[] {ScheduleController.AiScheduleDemand.class}, weekendDemand))
                .isEqualTo(false);
        assertThat(invoke(controller, "expectedVisits", new Class<?>[] {LocalDate.class, String.class, int.class, boolean.class, int.class, boolean.class, int.class},
                        LocalDate.of(2030, 1, 5), AFTERNOON, 20, false, 35, false, 25))
                .isEqualTo(15);
        assertThat(invoke(controller, "coversPeriod", new Class<?>[] {List.class, LocalDate.class, String.class},
                        List.of(schedule), LocalDate.of(2030, 1, 2), MORNING))
                .isEqualTo(false);
        assertThat(invoke(controller, "coversRoomPeriod", new Class<?>[] {List.class, String.class, LocalDate.class, String.class},
                        List.of(schedule), "room-x", LocalDate.of(2030, 1, 1), MORNING))
                .isEqualTo(false);
        assertThat(invoke(controller, "hasEventConflict", new Class<?>[] {List.class, List.class},
                        List.of(schedule),
                        List.of(new DoctorCatalogRepository.DoctorEvent("event-1", "doctor-2", "Doctor 2", "Internal", "LEAVE", List.of(LocalDate.of(2030, 1, 1)), List.of(MORNING), "note"))))
                .isEqualTo(false);
        invoke(controller, "syncSlotsBatch", new Class<?>[] {List.class}, new Object[] {null});
        invoke(controller, "syncSlotsBatch", new Class<?>[] {List.class}, List.of());
        verify(slotInventoryService, org.mockito.Mockito.never()).syncSlotsBatch(any());
        assertThat(invoke(controller, "hasUnavailableSlot", new Class<?>[] {ScheduleController.AiDoctorCandidate.class, String.class, String.class},
                        candidate, "2030-01-01", MORNING))
                .isEqualTo(false);
    }

    private ScheduleController controller() {
        ScheduleController controller =
                new ScheduleController(repository, scheduleInsightService, slotInventoryService, "internal-key", "http://ai");
        ReflectionTestUtils.setField(controller, "aiClient", aiClient);
        controllers.add(controller);
        return controller;
    }

    private DoctorCatalogRepository.Schedule schedule(String id, String doctorId, LocalDate date, String period) {
        return new DoctorCatalogRepository.Schedule(
                id, doctorId, "Doctor", "dept-1", "Internal", date, period, 20, "PUBLISHED", "room-1", "Room 1");
    }

    private List<DoctorCatalogRepository.Schedule> fullyCoveredSchedules(LocalDate start, LocalDate end) {
        List<DoctorCatalogRepository.Schedule> schedules = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            schedules.add(schedule("m-" + date, "doctor-1", date, MORNING));
            schedules.add(schedule("a-" + date, "doctor-1", date, AFTERNOON));
        }
        return schedules;
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
