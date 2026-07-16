package com.mediconnect.ctf.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Output/input DTOs for the CTF API. flagHash/flagSalt from the entity are
 * NEVER included here - the challenge is unsolvable if the hash leaks.
 */
public final class CtfDtos {

    private CtfDtos() {}

    public enum Status { LOCKED, OPEN, SOLVED }

    /** Row in the challenge list. */
    public record ChallengeSummary(
            String slug,
            String owaspCategory,
            String title,
            String difficulty,
            int points,
            boolean behavioral,
            Status status,
            boolean solved,
            int attempts,
            int hintsUsed) {}

    /** Challenge detail page. intendedPath (the solution) is intentionally omitted. */
    public record ChallengeDetail(
            String slug,
            String owaspCategory,
            String title,
            String difficulty,
            int points,
            boolean behavioral,
            String objective,
            String targetHint,
            Status status,
            boolean solved,
            int attempts,
            int hintsUsed) {}

    public record SubmitRequest(String flag) {}

    public record SubmitResponse(
            boolean correct,
            boolean solved,
            int pointsAwarded,
            String message) {}

    public record CategoryProgress(
            String owaspCategory,
            int solved,
            int total,
            int scoreEarned,
            int scorePossible) {}

    public record HistoryEntry(
            String slug,
            String title,
            String owaspCategory,
            int points,
            LocalDateTime solvedAt) {}

    public record Progress(
            int totalScore,
            int possibleScore,
            int solvedCount,
            int totalCount,
            List<CategoryProgress> categories,
            List<HistoryEntry> history) {}

    public record SettingEntry(String key, String value) {}

    public record SettingsView(List<SettingEntry> settings) {}
}
