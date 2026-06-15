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
public class ImagingFileDto {
    private Long id;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private String storedFilename;
    private String storagePath;
    private String contentType;
    private Long sizeBytes;
    // [A03] URL that was fetched server-side (when import-url was used).
    private String sourceUrl;
    private String note;
    private LocalDateTime createdAt;
}
