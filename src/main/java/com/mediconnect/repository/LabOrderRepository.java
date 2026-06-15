package com.mediconnect.repository;

import com.mediconnect.entity.LabOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabOrderRepository extends JpaRepository<LabOrder, Long> {
    List<LabOrder> findAllByOrderByCreatedAtDesc();
    List<LabOrder> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<LabOrder> findByStatusOrderByCreatedAtDesc(String status);
    List<LabOrder> findByPatientIdAndStatusOrderByCreatedAtDesc(Long patientId, String status);
}
