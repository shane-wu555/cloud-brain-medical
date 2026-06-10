package com.cloudbrain.appointment.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return Map.of(
                "todayAppointments", 46,
                "waitingVisits", 13,
                "activeDoctors", 18,
                "aiTriageCount", 31,
                "departmentLoads", List.of(
                        Map.of("name", "神经内科", "value", 18),
                        Map.of("name", "影像科", "value", 11),
                        Map.of("name", "全科医学", "value", 17)));
    }
}

