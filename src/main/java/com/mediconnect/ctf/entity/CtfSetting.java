package com.mediconnect.ctf.entity;

import jakarta.persistence.*;
import lombok.*;

/** Instructor gating overrides (plan §5.2). Key/value. */
@Entity
@Table(name = "ctf_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CtfSetting {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", length = 512)
    private String value;
}
