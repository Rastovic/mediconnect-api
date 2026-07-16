package com.mediconnect.ctf.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Hidden secrets reachable only via an exploit (UNION SQLi dump, or an
 * auth-bypass that reaches an admin-gated endpoint). Created by migration V30/V31.
 */
@Entity
@Table(name = "ctf_secret")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CtfSecret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String label;

    @Column(nullable = false, length = 128)
    private String flag;

    @Column(name = "status_ok", length = 16)
    private String statusOk;
}
