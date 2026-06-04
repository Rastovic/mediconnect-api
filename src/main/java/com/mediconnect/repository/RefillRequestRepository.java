package com.mediconnect.repository;

import com.mediconnect.entity.RefillRequest;
import com.mediconnect.enums.RefillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefillRequestRepository extends JpaRepository<RefillRequest, Long> {

    List<RefillRequest> findTop50ByStatusOrderByCreatedAtAsc(RefillStatus status);

    List<RefillRequest> findByPatientId(Long patientId);

    List<RefillRequest> findByStatus(RefillStatus status);
}
