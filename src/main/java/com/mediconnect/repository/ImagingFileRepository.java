package com.mediconnect.repository;

import com.mediconnect.entity.ImagingFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImagingFileRepository extends JpaRepository<ImagingFile, Long> {
    List<ImagingFile> findAllByOrderByCreatedAtDesc();
    List<ImagingFile> findByPatientIdOrderByCreatedAtDesc(Long patientId);
}
