package com.pukazhya.oibsip.task2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GameEngine.java
 * Pure logic for Number Guess Lab
 *
 * Save as: src/main/java/com/pukazhya/oibsip/task2/GameEngine.java
 */
public class GameEngine {

    private final String playerName;
    private final int min, max;
    private final int totalRounds;
    private final int maxAttempts;
    private final int secondsPerRound;

    private final Random rng = new Random();
    private final List<Round> rounds = new ArrayList<>();
    private int currentIndex = -1;
    private int remainingSeconds = 0;

    public GameEngine(String playerName, int min, int max, int totalRounds, int maxAttempts, int secondsPerRound) {
        this.playerName = playerName;
        this.min = min; this.max = max;
        this.totalRounds = totalRounds; this.maxAttempts = maxAttempts; this.secondsPerRound = secondsPerRound;
        for (int i = 0; i < totalRounds; i++) rounds.add(new Round(rng.nextInt(max - min + 1) + min, maxAttempts));
    }

    // lifecycle
    public void startRound() {
        if (currentIndex < 0) currentIndex = 0;
        remainingSeconds = secondsPerRound;
        rounds.get(currentIndex).start();
    }

    public void advanceToNextRound() {
        if (hasNextRound()) {
            currentIndex++;
            remainingSeconds = secondsPerRound;
            rounds.get(currentIndex).start();
        }
    }

    public boolean hasNextRound() { return currentIndex < totalRounds - 1; }
    public int getCurrentRoundIndex() { return currentIndex; }
    public int getTotalRounds() { return totalRounds; }
    public int getMin() { return min; }
    public int getMax() { return max; }
    public int getMaxAttempts() { return maxAttempts; }
    public String getPlayerName() { return playerName; }

    // timer helpers
    public void resetRoundTimer() { remainingSeconds = secondsPerRound; }
    public void decrementSecond() { remainingSeconds = Math.max(0, remainingSeconds - 1); }
    public int getRemainingSeconds() { return remainingSeconds; }
    public void timeoutRound() { rounds.get(currentIndex).forfeit(); }

    // get status / info
    public int getAttemptsUsed() { return rounds.get(currentIndex).attempts; }
    public boolean isRoundFinished() { return rounds.get(currentIndex).finished; }
    public int getCurrentRoundScore() { return rounds.get(currentIndex).points; }
    public int getTotalScore() { return rounds.stream().mapToInt(r -> r.points).sum(); }

    public String getRoundHistoryText() {
        Round r = rounds.get(currentIndex);
        StringBuilder sb = new StringBuilder();
        sb.append("Secret is hidden\n");
        int i = 1;
        for (Guess g : r.history) {
            sb.append(String.format("Guess %d -> %d (%s)\n", i++, g.value, g.note));
        }
        return sb.toString();
    }

    public void stopTimers() { /* UI timers live in Main */ }

    public void giveUp() { rounds.get(currentIndex).forfeit(); }

    public String getLatestHint() {
        Round r = rounds.get(currentIndex);
        if (r.history.isEmpty()) return "Ready";
        Guess last = r.history.get(r.history.size()-1);
        if (r.finished && r.points > 0) return "Correct — round finished";
        if (r.finished) return "Round finished";
        return last.note;
    }

    // Guess submission
    public GuessOutcome submitGuess(int guess) {
        Round r = rounds.get(currentIndex);
        if (r.finished) return new GuessOutcome(false, true, 0, r.secret, "Round already finished", r.attempts);

        r.attempts++;
        String note;
        if (!r.history.isEmpty()) {
            int last = r.history.get(r.history.size()-1).value;
            note = Math.abs(r.secret - guess) < Math.abs(r.secret - last) ? "Warmer" : "Colder";
        } else {
            note = "First try";
        }

        if (guess == r.secret) {
            r.finished = true;
            int base = Math.max(100, 300 - (max-min)/2);
            int attemptPenalty = (r.attempts - 1) * 20;
            int timeBonus = Math.max(0, remainingSeconds / 2);
            r.points = Math.max(0, base - attemptPenalty + timeBonus);
            r.history.add(new Guess(guess, "Correct"));
            return new GuessOutcome(true, true, r.points, r.secret, "Correct", r.attempts);
        } else {
            r.history.add(new Guess(guess, (guess < r.secret ? "Higher" : "Lower")));
            if (r.attempts >= r.maxAttempts) {
                r.finished = true;
                r.points = 0;
                return new GuessOutcome(false, true, 0, r.secret, "Attempts exhausted", r.attempts);
            } else {
                return new GuessOutcome(false, false, 0, r.secret, (guess < r.secret ? "Higher" : "Lower") , r.attempts);
            }
        }
    }

    // inner data types
    static class Round {
        final int secret;
        final int maxAttempts;
        int attempts = 0;
        int points = 0;
        boolean finished = false;
        final List<Guess> history = new ArrayList<>();
        Round(int secret, int maxAttempts) { this.secret = secret; this.maxAttempts = maxAttempts; }
        void start() { this.finished = false; this.attempts = 0; this.points = 0; history.clear(); }
        void forfeit() { this.finished = true; this.points = 0; }
    }

    static class Guess {
        final int value; final String note;
        Guess(int value, String note) { this.value = value; this.note = note; }
    }

    static class GuessOutcome {
        final boolean correct;
        final boolean roundFinished;
        final int pointsAwarded;
        final int secret;
        final String log;
        final int attemptsUsed;
        GuessOutcome(boolean correct, boolean roundFinished, int pointsAwarded, int secret, String log, int attemptsUsed) {
            this.correct = correct; this.roundFinished = roundFinished; this.pointsAwarded = pointsAwarded;
            this.secret = secret; this.log = log; this.attemptsUsed = attemptsUsed;
        }
    }
}
