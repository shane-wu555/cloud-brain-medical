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
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleControllerTest {
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
        controllers.clear();
    }

    @Test
    void listCreateAndPublishEndpointsMapSchedulesAndSlots() {
        ScheduleController controller = controller();
        DoctorCatalogRepository.Schedule schedule =
                schedule("schedule-1", "doctor-1", LocalDate.now().plusDays(1), MORNING);
        List<DoctorCatalogRepository.ScheduleTimeSlot> timeSlots =
                List.of(new DoctorCatalogRepository.ScheduleTimeSlot("slot-1", "schedule-1", LocalTime.of(8, 0), 10));

        when(repository.schedules(null, null))
                .thenReturn(List.of(
                        schedule,
                        schedule("schedule-2", "doctor-2", LocalDate.now().minusDays(1), AFTERNOON)));
        when(repository.timeSlots(List.of("schedule-1"))).thenReturn(timeSlots);
        when(repository.timeSlots("schedule-1")).thenReturn(timeSlots);
        when(repository.createSchedule("doctor-1", "dept-1", LocalDate.parse("2030-01-01"), MORNING, 20))
                .thenReturn(schedule);
        when(repository.outpatientRoomForDoctor("doctor-1"))
                .thenReturn(new DoctorCatalogRepository.OutpatientRoom("room-1", "dept-1", "Room 1", "Floor 1"));
        when(slotInventoryService.fetchSlots())
                .thenReturn(List.of(new SlotInventoryService.SlotDto("slot-1", 10, 2, 3, 5)));

        assertThat(controller.list(null, null, true)).hasSize(1);
        assertThat(controller.create(
                        new ScheduleController.CreateScheduleRequest("doctor-1", "dept-1", "2030-01-01", MORNING, 20)))
                .extracting(ScheduleController.ScheduleDto::id)
                .isEqualTo("schedule-1");
        assertThat(controller.publishAiSuggestion(
                        "suggestion-1",
                        new ScheduleController.PublishAiScheduleRequest(
                                "ai-record",
                                "doctor-1",
                                "Doctor",
                                "dept-1",
                                "room-x",
                                "Room X",
                                "2030-01-01",
                                MORNING,
                                20,
                                true)))
                .extracting(ScheduleController.ScheduleDto::id)
                .isEqualTo("schedule-1");

        assertThatThrownBy(() -> controller.publishAiSuggestion(
                        "suggestion-2",
                        new ScheduleController.PublishAiScheduleRequest(
                                "ai-record", null, "Doctor", "dept-1", "room-1", "Room 1", "2030-01-01", MORNING, 20, true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scheduleMutationEndpointsHandleSuspendRescheduleAndBatchPublish() {
        ScheduleController controller = controller();
        DoctorCatalogRepository.Schedule schedule =
                schedule("schedule-1", "doctor-1", LocalDate.parse("2030-01-01"), MORNING);
        List<DoctorCatalogRepository.ScheduleTimeSlot> timeSlots =
                List.of(new DoctorCatalogRepository.ScheduleTimeSlot("slot-1", "schedule-1", LocalTime.of(8, 0), 10));

        when(repository.timeSlots("schedule-1")).thenReturn(timeSlots);
        when(repository.suspendSchedule("schedule-1", "leave")).thenReturn(schedule);
        when(repository.reschedule("schedule-1", LocalDate.parse("2030-01-02"), AFTERNOON))
                .thenReturn(schedule("schedule-1", "doctor-1", LocalDate.parse("2030-01-02"), AFTERNOON));
        when(repository.outpatientRoomForDoctor("doctor-1"))
                .thenReturn(new DoctorCatalogRepository.OutpatientRoom("room-1", "dept-1", "Room 1", "Floor 1"));
        when(repository.createSchedule("doctor-1", "dept-1", LocalDate.parse("2030-01-01"), MORNING, 20))
                .thenReturn(schedule);
        when(slotInventoryService.fetchSlots())
                .thenReturn(List.of(new SlotInventoryService.SlotDto("slot-1", 10, 1, 2, 7)));

        assertThat(controller.suspend("schedule-1", new ScheduleController.SuspendRequest("leave")))
                .extracting(ScheduleController.ScheduleDto::id)
                .isEqualTo("schedule-1");
        assertThat(controller.reschedule("schedule-1", new ScheduleController.RescheduleRequest("2030-01-02", AFTERNOON)))
                .extracting(ScheduleController.ScheduleDto::workDate)
                .isEqualTo("2030-01-02");
        assertThat(controller.publishAiSuggestions(new ScheduleController.PublishAiScheduleBatchRequest(
                        "ai-record",
                        List.of(
                                new ScheduleController.PublishAiScheduleRequest(
                                        "ai-record",
                                        "doctor-1",
                                        "Doctor",
                                        "dept-1",
                                        "room-1",
                                        "Room 1",
                                        "2030-01-01",
                                        MORNING,
                                        20,
                                        true),
                                new ScheduleController.PublishAiScheduleRequest(
                                        "ai-record",
                                        "doctor-1",
                                        "Doctor",
                                        "dept-1",
                                        "room-1",
                                        "Room 1",
                                        "2030-01-01",
                                        MORNING,
                                        20,
                                        true)))))
                .hasSize(1);
        assertThat(controller.publishAiSuggestions(
                        new ScheduleController.PublishAiScheduleBatchRequest("ai-record", List.of())))
                .isEmpty();
        assertThatThrownBy(() -> controller.publishAiSuggestions(new ScheduleController.PublishAiScheduleBatchRequest(
                        "ai-record",
                        List.of(new ScheduleController.PublishAiScheduleRequest(
                                "ai-record",
                                "doctor-1",
                                "Doctor",
                                "dept-1",
                                "room-1",
                                "Room 1",
                                LocalDate.now().minusDays(1).toString(),
                                MORNING,
                                20,
                                true)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能早于今天");
        verify(slotInventoryService).syncSlotsBatch(any());
    }

    @Test
    void aiEndpointsCoverFallbackPreviewAndTaskAccess() {
        ScheduleController controller = controller();
        LocalDate start = LocalDate.now().plusDays(7);
        LocalDate end = start.plusDays(6);
        DoctorCatalogRepository.Department department =
                new DoctorCatalogRepository.Department("dept-1", "Internal", "desc");
        when(scheduleInsightService.current()).thenReturn(new ScheduleInsightService.ScheduleInsight(
                250, true, Map.of("doctor-1", 15), Map.of("dept-1", 20), Map.of(1, 25), "trained"));
        when(repository.schedulingDepartments()).thenReturn(List.of(department));
        when(repository.doctorEvents(start, end)).thenReturn(List.of());
        when(repository.schedules(null, "dept-1", start, end)).thenReturn(fullyCoveredSchedules(start, end));
        when(repository.outpatientRoomsWithDoctors("dept-1")).thenReturn(List.of());

        doReturn(aiPostSpec).when(aiClient).post();
        when(aiPostSpec.uri("/api/ai/schedule-suggestions")).thenReturn(aiBodySpec);
        when(aiBodySpec.contentType(any())).thenReturn(aiBodySpec);
        when(aiBodySpec.accept(any())).thenReturn(aiBodySpec);
        doReturn(aiBodySpec).when(aiBodySpec).body(any());
        when(aiBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("down"));

        ScheduleController.AiScheduleRequest request = new ScheduleController.AiScheduleRequest(
                List.of(new ScheduleController.AiDoctorCandidate(
                        "doctor-1", "Doctor", "Chief", "dept-1", "room-1", "Room 1", "Cardiology", 50, 20, List.of())),
                List.of(new ScheduleController.AiScheduleDemand("dept-1", "room-1", "Room 1", "2030-01-01", MORNING, 30, 20)),
                "");

        assertThat(controller.aiSuggestions(request).fallbackUsed()).isTrue();
        assertThat(controller.aiReplanPreview("dept-1", 24, true, 35, true, 25, false).model())
                .isEqualTo("not-required");

        ScheduleController.AiScheduleTask queued =
                controller.startAiReplanPreview("dept-1", 24, true, 35, true, 25, false);
        assertThat(queued.status()).isEqualTo("QUEUED");

        @SuppressWarnings("unchecked")
        Map<String, ScheduleController.AiScheduleTask> tasks =
                (Map<String, ScheduleController.AiScheduleTask>) ReflectionTestUtils.getField(controller, "aiTasks");
        ScheduleController.AiScheduleTask existing = ScheduleController.AiScheduleTask.queued("task-1");
        tasks.put("task-1", existing);
        assertThat(controller.aiReplanPreviewTask("task-1")).isEqualTo(existing);
        assertThatThrownBy(() -> controller.aiReplanPreviewTask("missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reflectionHelpersCoverPayloadGroupingAugmentationAndUtilityMethods() throws Exception {
        ScheduleController controller = controller();
        when(scheduleInsightService.current()).thenReturn(new ScheduleInsightService.ScheduleInsight(
                250, true, Map.of(), Map.of(), Map.of(), "trained"));

        ScheduleController.AiDoctorCandidate candidate = new ScheduleController.AiDoctorCandidate(
                "doctor-1",
                "Doctor",
                "Chief",
                "dept-1",
                "room-1",
                "Room 1",
                "Cardiology",
                50,
                20,
                List.of(new ScheduleController.DoctorUnavailableSlot("2030-01-01", MORNING, "LEAVE")));
        ScheduleController.AiScheduleDemand morning =
                new ScheduleController.AiScheduleDemand("dept-1", "room-1", "Room 1", "2030-01-01", MORNING, 30, 20);
        ScheduleController.AiScheduleDemand afternoon =
                new ScheduleController.AiScheduleDemand("dept-1", "room-1", "Room 1", "2030-01-01", AFTERNOON, 20, 15);

        ScheduleController.AiScheduleRequest normalized = (ScheduleController.AiScheduleRequest) invoke(
                controller,
                "normalizeAiScheduleRequest",
                new Class<?>[] {ScheduleController.AiScheduleRequest.class},
                new ScheduleController.AiScheduleRequest(
                        java.util.Arrays.asList(candidate, null),
                        List.of(
                                morning,
                                afternoon,
                                new ScheduleController.AiScheduleDemand("dept-1", "", "", "2030-01-01", "NIGHT", 5, 1)),
                        ""));
        assertThat(normalized.demands()).hasSize(2);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) invoke(
                controller, "aiSchedulePayload", new Class<?>[] {ScheduleController.AiScheduleRequest.class}, normalized);
        assertThat(payload).containsKeys("candidates", "demands", "backgroundSummary");

        ScheduleController.AiScheduleResponse mapped = (ScheduleController.AiScheduleResponse) invoke(
                controller,
                "mapAiScheduleResponse",
                new Class<?>[] {Map.class},
                Map.of(
                        "aiRecordId",
                        "ai-1",
                        "suggestions",
                        List.of(Map.of(
                                "suggestionId",
                                "s1",
                                "doctorId",
                                "doctor-1",
                                "doctorName",
                                "Doctor",
                                "departmentId",
                                "dept-1",
                                "roomId",
                                "room-1",
                                "roomName",
                                "Room 1",
                                "workDate",
                                "2030-01-01",
                                "period",
                                MORNING,
                                "capacity",
                                "18",
                                "requiresAdminConfirmation",
                                false)),
                        "knowledgeSources",
                        List.of(Map.of("type", "rag")),
                        "fallbackUsed",
                        true,
                        "backgroundSummary",
                        "bg"));
        assertThat(mapped.suggestions()).hasSize(1);

        ScheduleController.AiScheduleResponse fallback = (ScheduleController.AiScheduleResponse) invoke(
                controller,
                "fallbackAiSuggestions",
                new Class<?>[] {ScheduleController.AiScheduleRequest.class, String.class},
                new ScheduleController.AiScheduleRequest(List.of(candidate), List.of(morning, afternoon), "bg"),
                "notice");
        assertThat(fallback.suggestions()).isNotEmpty();

        ScheduleController.AiScheduleResponse augmented = (ScheduleController.AiScheduleResponse) invoke(
                controller,
                "augmentPartialAiSuggestions",
                new Class<?>[] {ScheduleController.AiScheduleRequest.class, ScheduleController.AiScheduleResponse.class},
                new ScheduleController.AiScheduleRequest(
                        List.of(
                                new ScheduleController.AiDoctorCandidate(
                                        "doctor-2", "Doctor 2", "Chief", "dept-1", "room-1", "Room 1", "Cardiology", 50, 20, List.of())),
                        List.of(morning, afternoon),
                        "bg"),
                new ScheduleController.AiScheduleResponse(
                        "ai-1",
                        List.of(new ScheduleController.AiScheduleSuggestion(
                                "s1", "doctor-2", "Doctor 2", "dept-1", "room-1", "Room 1", "2030-01-01", MORNING, 18, true)),
                        "provider",
                        "model",
                        false,
                        List.of(),
                        "bg"));
        assertThat(augmented.suggestions().size()).isGreaterThanOrEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Object> groups =
                (List<Object>) invoke(controller, "scheduleDemandGroups", new Class<?>[] {List.class}, List.of(morning, afternoon));
        assertThat(groups).hasSize(1);

        @SuppressWarnings("unchecked")
        List<ScheduleController.AiDoctorCandidate> available = (List<ScheduleController.AiDoctorCandidate>) invoke(
                controller,
                "availableCandidates",
                new Class<?>[] {List.class, ScheduleController.AiScheduleDemand.class, Set.class, Set.class},
                List.of(candidate),
                afternoon,
                Set.of(),
                Set.of());
        assertThat(available).containsExactly(candidate);

        assertThat((boolean) invoke(
                        controller,
                        "hasPublishRoomMismatch",
                        new Class<?>[] {String.class, DoctorCatalogRepository.OutpatientRoom.class},
                        "room-x",
                        new DoctorCatalogRepository.OutpatientRoom("room-1", "dept-1", "Room 1", "Floor 1")))
                .isTrue();
        assertThat((int) invoke(
                        controller,
                        "expectedVisits",
                        new Class<?>[] {LocalDate.class, String.class, int.class, boolean.class, int.class, boolean.class, int.class},
                        LocalDate.of(2030, 1, 1),
                        MORNING,
                        20,
                        true,
                        50,
                        true,
                        25))
                .isGreaterThan(20);

        @SuppressWarnings("unchecked")
        Map<String, ScheduleController.AiScheduleTask> tasks =
                (Map<String, ScheduleController.AiScheduleTask>) ReflectionTestUtils.getField(controller, "aiTasks");
        tasks.put(
                "old",
                new ScheduleController.AiScheduleTask(
                        "old",
                        "COMPLETED",
                        "done",
                        LocalDateTime.now().minusDays(1),
                        LocalDateTime.now().minusDays(1),
                        null));
        invoke(controller, "trimAiTasks", new Class<?>[0]);
        assertThat(tasks).containsKey("old");
    }

    @Test
    void badRequestAndShutdownAreSafe() {
        ScheduleController controller = controller();
        assertThat(controller.badRequest(new IllegalArgumentException("boom")))
                .containsEntry("message", "boom");
        controller.shutdown();
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
