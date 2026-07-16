package com.mediconnect.ctf.repository;

import com.mediconnect.ctf.entity.CtfSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CtfSettingRepository extends JpaRepository<CtfSetting, String> {
}
