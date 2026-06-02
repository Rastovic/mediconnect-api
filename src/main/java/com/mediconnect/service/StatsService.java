package com.mediconnect.service;

import com.mediconnect.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

// [A01] No authorization check — any authenticated (or unauthenticated) user
//        can retrieve aggregate statistics including patient/doctor counts.
@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository          userRepository;
    private final AppointmentRepository   appointmentRepository;
    private final LabResultRepository     labResultRepository;
    private final MessageRepository       messageRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PrescriptionRepository  prescriptionRepository;

    public Map<String, Object> getSummary() {
        var users        = userRepository.findAll();
        var appointments = appointmentRepository.findAll();
        var labResults   = labResultRepository.findAll();

        Map<String, Long> usersByRole = users.stream()
            .collect(Collectors.groupingBy(u -> u.getRole().name(), Collectors.counting()));

        Map<String, Long> appointmentsByStatus = appointments.stream()
            .collect(Collectors.groupingBy(a -> a.getStatus().name(), Collectors.counting()));

        Map<String, Long> labResultsByStatus = labResults.stream()
            .collect(Collectors.groupingBy(l -> l.getStatus().name(), Collectors.counting()));

        LocalDate today = LocalDate.now();
        long appointmentsToday = appointments.stream()
            .filter(a -> a.getRequestedDate() != null &&
                         a.getRequestedDate().toLocalDate().equals(today))
            .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalUsers",            users.size());
        summary.put("usersByRole",            usersByRole);
        summary.put("totalAppointments",      appointments.size());
        summary.put("appointmentsByStatus",   appointmentsByStatus);
        summary.put("appointmentsToday",      appointmentsToday);
        summary.put("totalLabResults",        labResults.size());
        summary.put("labResultsByStatus",     labResultsByStatus);
        summary.put("totalMessages",          messageRepository.count());
        summary.put("totalMedicalRecords",    medicalRecordRepository.count());
        summary.put("totalPrescriptions",     prescriptionRepository.count());
        return summary;
    }

    public Map<String, Object> getCharts() {
        var appointments = appointmentRepository.findAll();
        var messages     = messageRepository.findAll();

        // Appointments by month — last 6 calendar months
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);
        Map<String, Long> byMonth = appointments.stream()
            .filter(a -> a.getRequestedDate() != null)
            .collect(Collectors.groupingBy(
                a -> a.getRequestedDate().format(monthFmt),
                Collectors.counting()
            ));

        // Sort chronologically and take last 6 months
        List<Map<String, Object>> appointmentsByMonth = byMonth.entrySet().stream()
            .sorted(Comparator.comparing(e -> YearMonth.parse(e.getKey(), monthFmt)))
            .map(e -> {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("month", e.getKey());
                point.put("count", e.getValue());
                return point;
            })
            .collect(Collectors.toList());
        if (appointmentsByMonth.size() > 6) {
            appointmentsByMonth = appointmentsByMonth.subList(
                appointmentsByMonth.size() - 6, appointmentsByMonth.size());
        }

        // Appointments by status (for pie chart)
        List<Map<String, Object>> appointmentsByStatus = appointments.stream()
            .collect(Collectors.groupingBy(a -> a.getStatus().name(), Collectors.counting()))
            .entrySet().stream()
            .map(e -> {
                Map<String, Object> slice = new LinkedHashMap<>();
                slice.put("status", e.getKey());
                slice.put("count",  e.getValue());
                return slice;
            })
            .collect(Collectors.toList());

        // Messages per day — last 7 days
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
        LocalDate cutoff = LocalDate.now().minusDays(6);
        Map<LocalDate, Long> rawMsgCounts = messages.stream()
            .filter(m -> m.getSentAt() != null &&
                         !m.getSentAt().toLocalDate().isBefore(cutoff))
            .collect(Collectors.groupingBy(m -> m.getSentAt().toLocalDate(), Collectors.counting()));

        List<Map<String, Object>> messagesPerDay = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("day",   day.format(dayFmt));
            point.put("count", rawMsgCounts.getOrDefault(day, 0L));
            messagesPerDay.add(point);
        }

        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("appointmentsByMonth",  appointmentsByMonth);
        charts.put("appointmentsByStatus", appointmentsByStatus);
        charts.put("messagesPerDay",       messagesPerDay);
        return charts;
    }
}
