package com.mediconnect.repository;

import com.mediconnect.entity.TelemedicineSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelemedicineSessionRepository extends JpaRepository<TelemedicineSession, Long> {
    List<TelemedicineSession> findAllByOrderByScheduledAtDesc();
    List<TelemedicineSession> findByDoctorIdOrderByScheduledAtDesc(Long doctorId);
}
