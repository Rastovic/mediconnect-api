package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// [A05] No auth filter applied when building this DTO — all users' activity is returned
//        to any caller regardless of role or ownership.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentEventDto {
    private String type;         // APPOINTMENT, LAB_RESULT, MESSAGE, PRESCRIPTION
    private String description;
    private String timestamp;    // ISO-8601 string
}
