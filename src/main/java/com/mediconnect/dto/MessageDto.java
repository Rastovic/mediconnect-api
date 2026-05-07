package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A05] XSS: content polje prima i vraća sirovi HTML/JS bez sanitizacije.
// [A07] Isti DTO za request i response — klijent može lažirati senderId i
//        slati poruke u ime drugog korisnika.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {
    private Long id;
    // [A07] Klijent može postaviti tuđi senderId — lažiranje pošiljatelja
    private Long senderId;
    private Long receiverId;
    // [A05] Sirovi HTML/JS sadržaj prihvata se i vraća bez obrade
    private String content;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
}
