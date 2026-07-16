package com.mediconnect.ctf.service;

import com.mediconnect.ctf.dto.CtfDtos;
import com.mediconnect.ctf.dto.CtfDtos.*;
import com.mediconnect.ctf.entity.CtfChallenge;
import com.mediconnect.ctf.entity.CtfProgress;
import com.mediconnect.ctf.entity.CtfSubmission;
import com.mediconnect.ctf.repository.CtfChallengeRepository;
import com.mediconnect.ctf.repository.CtfProgressRepository;
import com.mediconnect.ctf.repository.CtfSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Orchestrates the CTF game: listing, detail, submit, progress. */
@Service
@RequiredArgsConstructor
public class CtfService {

    private final CtfChallengeRepository challenges;
    private final CtfProgressRepository progressRepo;
    private final CtfSubmissionRepository submissions;
    private final CtfFlagService flags;
    private final CtfGatingService gating;

    /** Hint cost: each revealed hint costs 25% of the challenge value, floored at 25% retained. */
    private static final double HINT_COST_FRACTION = 0.25;
    private static final double MIN_RETAINED_FRACTION = 0.25;

    public List<ChallengeSummary> list(Long userId) {
        Map<Long, CtfProgress> byChallenge = progressByChallenge(userId);
        return challenges.findAllByOrderBySortOrderAsc().stream()
                .map(c -> {
                    CtfProgress p = byChallenge.get(c.getId());
                    return new ChallengeSummary(
                            c.getSlug(), c.getOwaspCategory(), c.getTitle(),
                            c.getDifficulty().name(), c.getPoints(),
                            Boolean.TRUE.equals(c.getBehavioral()),
                            statusOf(c, p),
                            p != null && Boolean.TRUE.equals(p.getSolved()),
                            p != null ? p.getAttempts() : 0,
                            p != null ? p.getHintsUsed() : 0);
                })
                .toList();
    }

    public ChallengeDetail detail(Long userId, String slug) {
        CtfChallenge c = requireChallenge(slug);
        CtfProgress p = progressRepo.findByUserIdAndChallengeId(userId, c.getId()).orElse(null);
        return new ChallengeDetail(
                c.getSlug(), c.getOwaspCategory(), c.getTitle(),
                c.getDifficulty().name(), c.getPoints(),
                Boolean.TRUE.equals(c.getBehavioral()),
                c.getObjective(), c.getTargetHint(),
                statusOf(c, p),
                p != null && Boolean.TRUE.equals(p.getSolved()),
                p != null ? p.getAttempts() : 0,
                p != null ? p.getHintsUsed() : 0);
    }

    @Transactional
    public SubmitResponse submit(Long userId, String slug, String submittedFlag) {
        CtfChallenge c = requireChallenge(slug);

        if (!gating.isOpen(c)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Challenge is locked");
        }

        boolean correct = flags.verify(c, submittedFlag);

        // Full attempt log (A09 counterpoint - good logging, no secrets stored).
        CtfSubmission sub = CtfSubmission.builder()
                .userId(userId)
                .challengeId(c.getId())
                .submittedFlagHash(flags.hashForLog(c.getFlagSalt(), submittedFlag))
                .correct(correct)
                .build();
        submissions.save(sub);

        CtfProgress p = progressRepo.findByUserIdAndChallengeId(userId, c.getId())
                .orElseGet(() -> CtfProgress.builder()
                        .userId(userId).challengeId(c.getId())
                        .solved(false).attempts(0).hintsUsed(0)
                        .build());
        p.setAttempts(p.getAttempts() + 1);

        boolean alreadySolved = Boolean.TRUE.equals(p.getSolved());
        int awarded = 0;
        String message;

        if (correct) {
            if (!alreadySolved) {
                p.setSolved(true);
                p.setSolvedAt(LocalDateTime.now());
                awarded = scoreFor(c, p.getHintsUsed());
                message = "Correct - flag captured.";
            } else {
                message = "Correct - already solved, no additional points.";
            }
        } else {
            message = "Incorrect flag.";
        }
        progressRepo.save(p);

        return new SubmitResponse(correct, Boolean.TRUE.equals(p.getSolved()), awarded, message);
    }

    /** Record one revealed hint; returns updated hints_used. */
    @Transactional
    public int revealHint(Long userId, String slug) {
        CtfChallenge c = requireChallenge(slug);
        CtfProgress p = progressRepo.findByUserIdAndChallengeId(userId, c.getId())
                .orElseGet(() -> CtfProgress.builder()
                        .userId(userId).challengeId(c.getId())
                        .solved(false).attempts(0).hintsUsed(0)
                        .build());
        p.setHintsUsed(p.getHintsUsed() + 1);
        progressRepo.save(p);
        return p.getHintsUsed();
    }

    @Transactional
    public void reset(Long userId, String slug) {
        CtfChallenge c = requireChallenge(slug);
        progressRepo.findByUserIdAndChallengeId(userId, c.getId())
                .ifPresent(progressRepo::delete);
    }

    /**
     * Full game reset (§12.4): wipe the caller's progress + submission history and
     * clear all in-memory behavioral marks so every challenge can be replayed from
     * a clean scoreboard. This resets the GAME state only; to also restore app data
     * mutated by destructive challenges (wiped logs, overwritten records) use the
     * `docker compose down -v && up` full re-seed, which reruns the idempotent seeds.
     */
    @Transactional
    public void resetAll(Long userId) {
        progressRepo.deleteAll(progressRepo.findByUserId(userId));
        submissions.deleteAll(submissions.findByUserIdOrderByCreatedAtDesc(userId));
        com.mediconnect.ctf.CtfBehaviorRegistry.clearAll();
    }

    public Progress progress(Long userId) {
        List<CtfChallenge> all = challenges.findAllByOrderBySortOrderAsc();
        Map<Long, CtfProgress> byChallenge = progressByChallenge(userId);

        Map<String, int[]> cat = new LinkedHashMap<>(); // [solved, total, earned, possible]
        int totalScore = 0, possible = 0, solvedCount = 0;
        List<HistoryEntry> history = new ArrayList<>();

        for (CtfChallenge c : all) {
            int[] agg = cat.computeIfAbsent(c.getOwaspCategory(), k -> new int[4]);
            agg[1]++;
            agg[3] += c.getPoints();
            possible += c.getPoints();

            CtfProgress p = byChallenge.get(c.getId());
            if (p != null && Boolean.TRUE.equals(p.getSolved())) {
                int earned = scoreFor(c, p.getHintsUsed());
                agg[0]++;
                agg[2] += earned;
                totalScore += earned;
                solvedCount++;
                history.add(new HistoryEntry(c.getSlug(), c.getTitle(),
                        c.getOwaspCategory(), earned, p.getSolvedAt()));
            }
        }

        List<CategoryProgress> cats = cat.entrySet().stream()
                .map(e -> new CategoryProgress(e.getKey(),
                        e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();

        history.sort(Comparator.comparing(HistoryEntry::solvedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return new Progress(totalScore, possible, solvedCount, all.size(), cats, history);
    }

    // --- helpers ---

    private Map<Long, CtfProgress> progressByChallenge(Long userId) {
        Map<Long, CtfProgress> m = new LinkedHashMap<>();
        for (CtfProgress p : progressRepo.findByUserId(userId)) {
            m.put(p.getChallengeId(), p);
        }
        return m;
    }

    private CtfDtos.Status statusOf(CtfChallenge c, CtfProgress p) {
        if (p != null && Boolean.TRUE.equals(p.getSolved())) return CtfDtos.Status.SOLVED;
        return gating.isOpen(c) ? CtfDtos.Status.OPEN : CtfDtos.Status.LOCKED;
    }

    /** Points after hint deductions, with a retained floor (plan §1). */
    private int scoreFor(CtfChallenge c, int hintsUsed) {
        int base = c.getPoints();
        double retained = 1.0 - (HINT_COST_FRACTION * Math.max(0, hintsUsed));
        retained = Math.max(MIN_RETAINED_FRACTION, retained);
        return (int) Math.round(base * retained);
    }

    private CtfChallenge requireChallenge(String slug) {
        return challenges.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown challenge"));
    }
}
