package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A02] roomUrl + joinToken both returned in clear — UI embeds the URL in
//        an iframe so the token leaks via Referer to any third-party asset
//        the room page loads.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemedicineSessionDto {
    private Long id;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private String roomUrl;
    private String joinToken;
    // [A02] PHI in clear — also rendered on the public iCal feed's SUMMARY.
    private String reasonForVisit;
    private String status;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String recordingUrl;
    private String recordingPath;
    private String note;
    private LocalDateTime createdAt;
}
