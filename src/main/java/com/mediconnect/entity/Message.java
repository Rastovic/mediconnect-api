package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [A05] Injection / XSS: content polje pohranjen bez ikakve sanitizacije.
//        Vrijednost se vraća direktno u API odgovor — Stored XSS napad moguć
//        ako frontend prikazuje sadržaj bez HTML escaping-a.
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

    // [A05] Sirovi HTML/JS sadržaj — nema escaping-a ni sanitizacije
    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime sentAt;

    private LocalDateTime readAt;
}
