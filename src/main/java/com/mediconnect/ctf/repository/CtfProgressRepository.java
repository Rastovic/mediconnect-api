package com.mediconnect.ctf.repository;

import com.mediconnect.ctf.entity.CtfProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CtfProgressRepository extends JpaRepository<CtfProgress, Long> {

    List<CtfProgress> findByUserId(Long userId);

    Optional<CtfProgress> findByUserIdAndChallengeId(Long userId, Long challengeId);

    long countByUserIdAndSolvedTrue(Long userId);
}
