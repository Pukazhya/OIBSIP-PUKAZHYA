package com.pukazhya.oibsip.task2;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Main.java
 * Number Guessing Lab - by PUKAZHYA
 *
 * UI and controller. Uses GameEngine for game logic.
 *
 * Save as: src/main/java/com/pukazhya/oibsip/task2/Main.java
 */
public class Main {

    // Leaderboard file
    private static final Path LEADERBOARD_FILE = Paths.get("task2_leaderboard.csv");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // Swing components (fields so lambdas don't capture local changing vars)
    private final JFrame frame = new JFrame("Online Number Guess Lab - by PUKAZHYA");
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    // Home controls
    private JTextField playerNameField;
    private JComboBox<String> difficultyBox;
    private JSpinner roundsSpinner, attemptsSpinner, timerSpinner;
    private JCheckBox lightThemeCheck;

    // Game controls
    private JLabel roundInfoLabel, rangeLabel, attemptsLabel, timerLabel, hintLabel, roundScoreLabel, totalScoreLabel;
    private JTextField guessField;
    private JTextArea historyArea;
    private JProgressBar attemptProgress;
    private JButton submitBtn, giveUpBtn, nextRoundBtn;

    // Leaderboard controls
    private JTable lbTable;
    private DefaultTableModel lbModel;

    // Theme
    private boolean lightTheme = false;

    // Engine
    private GameEngine engine;

    // Swing round timer
    private javax.swing.Timer swingTimer;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new Main().start();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, "Startup error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void start() {
        buildUI();
        applyTheme();
        frame.setVisible(true);
    }

    private void buildUI() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(980, 620);
        frame.setLocationRelativeTo(null);

        root.add(buildHomePanel(), "home");
        root.add(buildGamePanel(), "game");
        root.add(buildLeaderboardPanel(), "leaderboard");

        frame.setContentPane(root);
        frame.setJMenuBar(buildMenuBar());
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu app = new JMenu("App");
        JMenuItem home = new JMenuItem("Home"); home.addActionListener(e -> cards.show(root, "home"));
        JMenuItem lb = new JMenuItem("Leaderboard"); lb.addActionListener(e -> { loadLeaderboard(); cards.show(root, "leaderboard"); });
        JMenuItem exit = new JMenuItem("Exit"); exit.addActionListener(e -> System.exit(0));
        app.add(home); app.add(lb); app.addSeparator(); app.add(exit);
        mb.add(app);
        return mb;
    }

    private JPanel buildHomePanel() {
        JPanel p = new JPanel(new BorderLayout(12,12));
        p.setBorder(new EmptyBorder(12,12,12,12));

        JLabel title = new JLabel("Number Guess Lab - by PUKAZHYA");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        p.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setBorder(new EmptyBorder(10,10,10,10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8,8,8,8);
        c.anchor = GridBagConstraints.WEST;

        c.gridx=0; c.gridy=0; center.add(new JLabel("Player name:"), c);
        playerNameField = new JTextField(System.getProperty("user.name", "Player"), 16);
        c.gridx=1; center.add(playerNameField, c);

        c.gridx=0; c.gridy=1; center.add(new JLabel("Difficulty:"), c);
        difficultyBox = new JComboBox<>(new String[] {"Easy (1-50)","Medium (1-100)","Hard (1-500)"});
        difficultyBox.setSelectedIndex(1);
        c.gridx=1; center.add(difficultyBox, c);

        c.gridx=0; c.gridy=2; center.add(new JLabel("Rounds:"), c);
        roundsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        c.gridx=1; center.add(roundsSpinner, c);

        c.gridx=0; c.gridy=3; center.add(new JLabel("Attempts per round:"), c);
        attemptsSpinner = new JSpinner(new SpinnerNumberModel(8, 1, 100, 1));
        c.gridx=1; center.add(attemptsSpinner, c);

        c.gridx=0; c.gridy=4; center.add(new JLabel("Seconds per round:"), c);
        timerSpinner = new JSpinner(new SpinnerNumberModel(60, 10, 600, 5));
        c.gridx=1; center.add(timerSpinner, c);

        c.gridx=0; c.gridy=5; center.add(new JLabel("Theme:"), c);
        lightThemeCheck = new JCheckBox("Light theme (uncheck for dark)");
        lightThemeCheck.setSelected(false);
        lightThemeCheck.addActionListener(e -> {
            lightTheme = lightThemeCheck.isSelected();
            applyTheme();
        });
        c.gridx=1; center.add(lightThemeCheck, c);

        JPanel right = new JPanel(new GridLayout(3,1,10,10));
        JButton startBtn = new JButton("Start Game");
        startBtn.addActionListener(e -> startGame());
        JButton viewLB = new JButton("View Leaderboard");
        viewLB.addActionListener(e -> { loadLeaderboard(); cards.show(root, "leaderboard"); });
        right.add(startBtn); right.add(viewLB);

        p.add(center, BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private JPanel buildGamePanel() {
        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(new EmptyBorder(12,12,12,12));

        // Top
        JPanel top = new JPanel(new BorderLayout());
        roundInfoLabel = new JLabel("Round 0 / 0"); roundInfoLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        timerLabel = new JLabel("Time: 00:00"); timerLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        top.add(roundInfoLabel, BorderLayout.WEST); top.add(timerLabel, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);

        // Center
        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(6,6,6,6);
        c.gridx=0; c.gridy=0; c.gridwidth=2;
        rangeLabel = new JLabel("Range: -"); rangeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        center.add(rangeLabel, c);

        c.gridy=1; c.gridwidth=2;
        hintLabel = new JLabel("Hint: Ready"); center.add(hintLabel, c);

        c.gridy=2; c.gridwidth=1; center.add(new JLabel("Your guess:"), c);
        guessField = new JTextField(10); c.gridx=1; center.add(guessField, c);

        c.gridx=0; c.gridy=3;
        submitBtn = new JButton("Submit Guess");
        submitBtn.addActionListener(e -> submitGuess());
        center.add(submitBtn, c);

        nextRoundBtn = new JButton("Next Round"); nextRoundBtn.setEnabled(false);
        nextRoundBtn.addActionListener(e -> nextRound());
        c.gridx=1; center.add(nextRoundBtn, c);

        p.add(center, BorderLayout.CENTER);

        // Right panel: history + stats
        JPanel right = new JPanel(new BorderLayout(6,6));
        right.setPreferredSize(new Dimension(360,0));
        right.setBorder(new EmptyBorder(6,6,6,6));
        right.add(new JLabel("Round History"), BorderLayout.NORTH);
        historyArea = new JTextArea(); historyArea.setEditable(false); historyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        right.add(new JScrollPane(historyArea), BorderLayout.CENTER);

        JPanel stats = new JPanel(new GridLayout(4,1,6,6));
        attemptsLabel = new JLabel("Attempts: 0 / 0");
        attemptProgress = new JProgressBar(0,100);
        roundScoreLabel = new JLabel("Round Score: 0");
        totalScoreLabel = new JLabel("Total Score: 0");
        stats.add(attemptsLabel); stats.add(attemptProgress); stats.add(roundScoreLabel); stats.add(totalScoreLabel);
        right.add(stats, BorderLayout.SOUTH);

        p.add(right, BorderLayout.EAST);

        // Bottom actions
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        giveUpBtn = new JButton("Give Up");
        giveUpBtn.addActionListener(e -> giveUp());
        JButton quitBtn = new JButton("Quit to Home");
        quitBtn.addActionListener(e -> {
            int conf = JOptionPane.showConfirmDialog(frame, "Quit current game and return to home? Progress will be lost.", "Confirm", JOptionPane.YES_NO_OPTION);
            if (conf == JOptionPane.YES_OPTION) {
                stopTimer();
                cards.show(root, "home");
            }
        });
        bottom.add(giveUpBtn); bottom.add(quitBtn);
        p.add(bottom, BorderLayout.SOUTH);

        return p;
    }

    private JPanel buildLeaderboardPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        p.setBorder(new EmptyBorder(12,12,12,12));
        JLabel t = new JLabel("Leaderboard"); t.setFont(new Font("Segoe UI", Font.BOLD, 20));
        p.add(t, BorderLayout.NORTH);

        lbModel = new DefaultTableModel(new Object[]{"Rank","Name","Score","Date"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        lbTable = new JTable(lbModel); lbTable.setRowHeight(24);
        p.add(new JScrollPane(lbTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton back = new JButton("Back"); back.addActionListener(e -> cards.show(root, "home"));
        bottom.add(back);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    // START GAME
    private void startGame() {
        String player = playerNameField.getText().trim();
        if (player.length() < 2) { JOptionPane.showMessageDialog(frame, "Please enter your name (min 2 characters)."); return; }
        int diffIdx = difficultyBox.getSelectedIndex();
        int[] rng = difficultyRange(diffIdx);
        int rounds = (Integer) roundsSpinner.getValue();
        int attempts = (Integer) attemptsSpinner.getValue();
        int seconds = (Integer) timerSpinner.getValue();

        engine = new GameEngine(player, rng[0], rng[1], rounds, attempts, seconds);
        engine.startRound(); // create first round
        updateUIForRound();
        cards.show(root, "game");
        startTimer();
    }

    private int[] difficultyRange(int idx) {
        switch (idx) {
            case 0: return new int[] {1,50};
            case 2: return new int[] {1,500};
            default: return new int[] {1,100};
        }
    }

    // UI updates
    private void updateUIForRound() {
        if (engine == null) return;
        roundInfoLabel.setText(String.format("Round %d / %d", engine.getCurrentRoundIndex()+1, engine.getTotalRounds()));
        rangeLabel.setText(String.format("Range: %d - %d", engine.getMin(), engine.getMax()));
        attemptsLabel.setText(String.format("Attempts: %d / %d", engine.getAttemptsUsed(), engine.getMaxAttempts()));
        attemptProgress.setValue((int)(engine.getAttemptsUsed()*100.0/engine.getMaxAttempts()));
        roundScoreLabel.setText("Round Score: " + engine.getCurrentRoundScore());
        totalScoreLabel.setText("Total Score: " + engine.getTotalScore());
        historyArea.setText(engine.getRoundHistoryText());
        timerLabel.setText("Time: " + formatSeconds(engine.getRemainingSeconds()));
        submitBtn.setEnabled(!engine.isRoundFinished());
        nextRoundBtn.setEnabled(engine.isRoundFinished() && engine.hasNextRound());
        giveUpBtn.setEnabled(!engine.isRoundFinished());
        hintLabel.setText(engine.getLatestHint());
        guessField.requestFocusInWindow();
    }

    private void submitGuess() {
        if (engine == null) return;
        String txt = guessField.getText().trim();
        if (txt.isEmpty()) { JOptionPane.showMessageDialog(frame, "Type a number to guess."); return; }
        int val;
        try { val = Integer.parseInt(txt); } catch (NumberFormatException e) { JOptionPane.showMessageDialog(frame, "Enter a valid integer."); return; }
        GameEngine.GuessOutcome outcome = engine.submitGuess(val);
        historyArea.append(outcome.log + "\n");
        historyArea.setCaretPosition(historyArea.getDocument().getLength());
        updateUIForRound();
        if (outcome.correct) {
            stopTimer();
            JOptionPane.showMessageDialog(frame, String.format("Correct! +%d pts for this round.", outcome.pointsAwarded));
            if (engine.hasNextRound()) nextRoundBtn.setEnabled(true); else gameOver();
        } else if (outcome.roundFinished) {
            stopTimer();
            JOptionPane.showMessageDialog(frame, "Round finished. Secret: " + outcome.secret);
            if (engine.hasNextRound()) nextRoundBtn.setEnabled(true); else gameOver();
        } else {
            hintLabel.setText(outcome.log);
        }
        guessField.setText("");
    }

    private void giveUp() {
        if (engine == null) return;
        int conf = JOptionPane.showConfirmDialog(frame, "Give up this round? It will count as 0 points.", "Confirm", JOptionPane.YES_NO_OPTION);
        if (conf != JOptionPane.YES_OPTION) return;
        engine.giveUp();
        updateUIForRound();
        stopTimer();
        if (engine.hasNextRound()) nextRoundBtn.setEnabled(true); else gameOver();
    }

    private void nextRound() {
        if (engine == null) return;
        if (!engine.hasNextRound()) { gameOver(); return; }
        engine.advanceToNextRound();
        updateUIForRound();
        startTimer();
    }

    private void gameOver() {
        stopTimer();
        int total = engine.getTotalScore();
        String msg = String.format("Game Over!\nPlayer: %s\nTotal Score: %d\nRounds: %d", engine.getPlayerName(), total, engine.getTotalRounds());
        JOptionPane.showMessageDialog(frame, msg);
        saveLeaderboard(engine.getPlayerName(), total);
        loadLeaderboard();
        cards.show(root, "leaderboard");
    }

    // Timer handling (Swing Timer)
    private void startTimer() {
        stopTimer();
        if (engine == null) return;
        engine.resetRoundTimer();
        swingTimer = new javax.swing.Timer(1000, e -> {
            engine.decrementSecond();
            timerLabel.setText("Time: " + formatSeconds(engine.getRemainingSeconds()));
            if (engine.getRemainingSeconds() <= 0) {
                ((javax.swing.Timer)e.getSource()).stop();
                engine.timeoutRound();
                historyArea.append("Time's up - round forfeited.\n");
                updateUIForRound();
                JOptionPane.showMessageDialog(frame, "Time's up for this round.");
                if (engine.hasNextRound()) nextRoundBtn.setEnabled(true); else gameOver();
            }
        });
        swingTimer.setInitialDelay(0);
        swingTimer.start();
    }

    private void stopTimer() {
        if (swingTimer != null && swingTimer.isRunning()) swingTimer.stop();
    }

    // Leaderboard I/O
    private void saveLeaderboard(String name, int score) {
        try {
            String line = String.join(",", escape(name), String.valueOf(score), LocalDateTime.now().format(DATE_FMT));
            Files.write(LEADERBOARD_FILE, Collections.singletonList(line), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            // silent
        }
    }

    private void loadLeaderboard() {
        lbModel.setRowCount(0);
        if (!Files.exists(LEADERBOARD_FILE)) return;
        try {
            List<String> lines = Files.readAllLines(LEADERBOARD_FILE, StandardCharsets.UTF_8);
            List<LeaderEntry> list = new ArrayList<>();
            for (String ln : lines) {
                String[] p = ln.split(",", 3);
                if (p.length >= 3) list.add(new LeaderEntry(unescape(p[0]), Integer.parseInt(p[1]), p[2]));
            }
            list.sort((a,b)->Integer.compare(b.score, a.score));
            int rank = 1;
            for (LeaderEntry le : list) {
                lbModel.addRow(new Object[]{rank++, le.name, le.score, le.time});
            }
        } catch (IOException ex) {
            // ignore
        }
    }

    private String formatSeconds(int s) { int m = s/60; int sec = s%60; return String.format("%02d:%02d", m, sec); }
    private static String escape(String s) { return s.replace(",", " "); }
    private static String unescape(String s) { return s; }

    private void applyTheme() {
        Color bg = lightTheme ? Color.WHITE : new Color(34,36,40);
        Color panel = lightTheme ? new Color(245,245,245) : new Color(44,46,50);
        Color fg = lightTheme ? Color.DARK_GRAY : Color.WHITE;
        Color accent = lightTheme ? new Color(20,110,230) : new Color(80,160,255);

        frame.getContentPane().setBackground(bg);
        for (Component comp : getAllComponents(frame.getContentPane())) {
            styleComponent(comp, panel, fg, accent);
        }
        SwingUtilities.updateComponentTreeUI(frame);
    }

    private void styleComponent(Component comp, Color panel, Color fg, Color accent) {
        if (comp instanceof JPanel) comp.setBackground(panel);
        else if (comp instanceof JLabel) ((JLabel)comp).setForeground(fg);
        else if (comp instanceof JButton) {
            JButton b = (JButton)comp; b.setBackground(accent); b.setForeground(Color.WHITE); b.setFocusPainted(false);
        } else if (comp instanceof JTextField || comp instanceof JTextArea || comp instanceof JTable) {
            comp.setBackground(lightTheme ? Color.WHITE : new Color(28,28,30)); comp.setForeground(fg);
        } else if (comp instanceof JSpinner) {
            comp.setBackground(lightTheme ? Color.WHITE : new Color(46,46,48)); comp.setForeground(fg);
        }
        if (comp instanceof Container) for (Component c : ((Container)comp).getComponents()) styleComponent(c, panel, fg, accent);
    }

    private List<Component> getAllComponents(Container c) {
        List<Component> list = new ArrayList<>();
        for (Component comp : c.getComponents()) {
            list.add(comp);
            if (comp instanceof Container) list.addAll(getAllComponents((Container) comp));
        }
        return list;
    }

    // Simple holder for leaderboard rows
    private static class LeaderEntry { final String name; final int score; final String time; LeaderEntry(String n,int s,String t){name=n;score=s;time=t;} }
}
