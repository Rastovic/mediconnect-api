package com.mediconnect.controller;

import com.mediconnect.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// [A01] No role check — PATIENT, DOCTOR, and unauthenticated callers can all
//        reach these endpoints and read aggregate system statistics.
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(statsService.getSummary());
    }

    @GetMapping("/charts")
    public ResponseEntity<Map<String, Object>> charts() {
        return ResponseEntity.ok(statsService.getCharts());
    }
}
