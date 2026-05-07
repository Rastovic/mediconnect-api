package com.mediconnect.repository;

import com.mediconnect.entity.LabResult;
import com.mediconnect.enums.LabResultStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabResultRepository extends JpaRepository<LabResult, Long> {
    List<LabResult> findByPatientId(Long patientId);
    List<LabResult> findByLabTechId(Long labTechId);
    List<LabResult> findByStatus(LabResultStatus status);
    List<LabResult> findByPatientIdAndStatus(Long patientId, LabResultStatus status);
}
