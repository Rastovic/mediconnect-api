package com.mediconnect.ctf.repository;

import com.mediconnect.ctf.entity.CtfSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CtfSubmissionRepository extends JpaRepository<CtfSubmission, Long> {

    List<CtfSubmission> findByUserIdOrderByCreatedAtDesc(Long userId);
}
