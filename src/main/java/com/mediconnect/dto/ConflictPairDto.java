package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictPairDto {
    private Long doctorId;
    private String doctorName;
    private Long firstId;
    private Long secondId;
    private String firstPatientName;
    private String secondPatientName;
    private LocalDateTime firstScheduledAt;
    private LocalDateTime secondScheduledAt;
}
