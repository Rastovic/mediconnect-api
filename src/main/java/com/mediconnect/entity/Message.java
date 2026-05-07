package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [A05] Injection / XSS: content field stored without any sanitization.
//        Value is returned directly in API responses — Stored XSS is possible
//        if the frontend renders the content without HTML escaping.
@Entity
@Table(name = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    @ToString.Exclude
    private User sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_id", nullable = false)
    @ToString.Exclude
    private User receiver;

    // [A05] Raw HTML/JS content — no escaping or sanitization applied
    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime sentAt;

    private LocalDateTime readAt;
}
