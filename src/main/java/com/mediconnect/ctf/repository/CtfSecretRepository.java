package com.mediconnect.ctf.repository;

import com.mediconnect.ctf.entity.CtfSecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CtfSecretRepository extends JpaRepository<CtfSecret, Long> {
    Optional<CtfSecret> findByLabel(String label);
}
