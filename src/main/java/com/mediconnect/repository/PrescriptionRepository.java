package com.mediconnect.repository;

import com.mediconnect.entity.Prescription;
import com.mediconnect.enums.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findByPatientId(Long patientId);
    List<Prescription> findByDoctorId(Long doctorId);
    List<Prescription> findByStatus(PrescriptionStatus status);
    List<Prescription> findByMedicalRecordId(Long medicalRecordId);
    List<Prescription> findByPharmacistId(Long pharmacistId);
}
