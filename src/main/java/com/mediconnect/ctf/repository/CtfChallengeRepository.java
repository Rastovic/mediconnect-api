package com.mediconnect.ctf.repository;

import com.mediconnect.ctf.entity.CtfChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CtfChallengeRepository extends JpaRepository<CtfChallenge, Long> {

    Optional<CtfChallenge> findBySlug(String slug);

    List<CtfChallenge> findAllByOrderBySortOrderAsc();
}
