package com.pukazhya.oibsip.task3;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Main.java
 *
 * PUKAZHYA P - Task 3 ATM Interface (GUI)
 *
 * Single-window Swing application:
 * - Login / Create account
 * - Dashboard: Deposit / Withdraw / Transfer / Export / Transaction history
 * - Admin view: list accounts (protected by passphrase)
 * - Persistent storage: accounts.dat (Java serialization)
 *
 * Place in: src/main/java/com/pukazhya/oibsip/task3/Main.java
 */
public class Main {

    private static final Path STORAGE = Paths.get("accounts.dat");

    private final JFrame frame = new JFrame("PUKAZHYA - ATM Interface (Task-3)");
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private final ATMService service;

    // Login UI
    private JTextField tfAccount;
    private JPasswordField pfPin;

    // Dashboard UI
    private JLabel lblWelcome;
    private JLabel lblBalance;
    private DefaultTableModel histModel;
    private JTable histTable;
    private BankAccount currentAccount;

    // Theme state
    private boolean darkMode = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Try Nimbus look & feel
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (Exception ignored) { }

            Main app = new Main();
            app.start();
        });
    }

    public Main() {
        this.service = new ATMService(STORAGE);
    }

    private void start() {
        seedDemoIfEmpty();
        buildUI();
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void seedDemoIfEmpty() {
        if (service.countAccounts() == 0) {
            service.createAccount("10001", "PUKAZHYA P", "1234", 15000.0);
            service.createAccount("10002", "ANJALI K", "4321", 8000.0);
        }
    }

    private void buildUI() {
        root.setBorder(new EmptyBorder(8,8,8,8));
        root.add(buildLoginPanel(), "login");
        root.add(buildDashboardPanel(), "dashboard");
        root.add(buildAdminPanel(), "admin");
        cards.show(root, "login");
    }

    private JPanel buildLoginPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8,8,8,8);
        gbc.fill = GridBagConstraints.BOTH;

        JPanel card = new JPanel(new BorderLayout(12,12));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.GRAY), new EmptyBorder(12,12,12,12)));

        JLabel title = new JLabel("PUKAZHYA ATM - Login", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        card.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints f = new GridBagConstraints();
        f.insets = new Insets(6,6,6,6);
        f.gridx = 0; f.gridy = 0; f.anchor = GridBagConstraints.WEST;
        form.add(new JLabel("Account ID:"), f);
        f.gridx = 1; f.weightx = 1.0; f.fill = GridBagConstraints.HORIZONTAL;
        tfAccount = new JTextField();
        form.add(tfAccount, f);

        f.gridx = 0; f.gridy = 1; f.weightx = 0; f.fill = GridBagConstraints.NONE;
        form.add(new JLabel("PIN (4 digits):"), f);
        f.gridx = 1; f.fill = GridBagConstraints.HORIZONTAL;
        pfPin = new JPasswordField();
        form.add(pfPin, f);

        f.gridy = 2; f.gridx = 0; f.gridwidth = 2;
        JCheckBox cbShow = new JCheckBox("Show PIN");
        cbShow.addActionListener(e -> pfPin.setEchoChar(cbShow.isSelected() ? (char)0 : '*'));
        form.add(cbShow, f);

        f.gridy = 3;
        JButton btnLogin = new JButton("Login");
        JButton btnCreate = new JButton("Create Account");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        actions.add(btnLogin); actions.add(btnCreate);
        form.add(actions, f);

        card.add(form, BorderLayout.CENTER);

        JLabel hint = new JLabel("Demo accounts: 10001 / 1234  and  10002 / 4321", SwingConstants.CENTER);
        hint.setBorder(new EmptyBorder(8,0,0,0));
        card.add(hint, BorderLayout.SOUTH);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.weighty = 1.0;
        p.add(card, gbc);

        btnLogin.addActionListener(e -> doLogin());
        btnCreate.addActionListener(e -> showCreateDialog());
        pfPin.addActionListener(e -> btnLogin.doClick());

        return p;
    }

    private JPanel buildDashboardPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        JPanel top = new JPanel(new BorderLayout());
        lblWelcome = new JLabel("Welcome");
        lblWelcome.setFont(lblWelcome.getFont().deriveFont(Font.BOLD, 16f));
        lblBalance = new JLabel("Balance: Rs.0.00");
        top.add(lblWelcome, BorderLayout.WEST);
        top.add(lblBalance, BorderLayout.EAST);

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnExport = new JButton("Export History");
        JButton btnTheme = new JButton("Toggle Theme");
        JButton btnAdmin = new JButton("Admin");
        JButton btnLogout = new JButton("Logout");
        topButtons.add(btnExport); topButtons.add(btnTheme); topButtons.add(btnAdmin); topButtons.add(btnLogout);
        p.add(top, BorderLayout.NORTH);
        p.add(topButtons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.34);

        JPanel ops = new JPanel();
        ops.setLayout(new BoxLayout(ops, BoxLayout.Y_AXIS));
        ops.setBorder(new EmptyBorder(8,12,8,12));
        JButton bDeposit = new JButton("Deposit");
        JButton bWithdraw = new JButton("Withdraw");
        JButton bTransfer = new JButton("Transfer");
        JButton bRefresh = new JButton("Refresh");
        JButton bExport = new JButton("Export to CSV");
        for (JButton b : Arrays.asList(bDeposit, bWithdraw, bTransfer, bRefresh, bExport)) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            ops.add(b);
            ops.add(Box.createVerticalStrut(8));
        }
        split.setLeftComponent(new JScrollPane(ops));

        String[] cols = new String[] {"ID", "DateTime", "Type", "Amount", "Note"};
        histModel = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        histTable = new JTable(histModel);
        histTable.setFillsViewportHeight(true);
        split.setRightComponent(new JScrollPane(histTable));
        p.add(split, BorderLayout.CENTER);

        // actions
        bDeposit.addActionListener(e -> showDeposit());
        bWithdraw.addActionListener(e -> showWithdraw());
        bTransfer.addActionListener(e -> showTransfer());
        bRefresh.addActionListener(e -> refreshDashboard());
        bExport.addActionListener(e -> exportHistory());
        btnExport.addActionListener(e -> exportHistory());
        btnLogout.addActionListener(e -> { currentAccount = null; cards.show(root, "login"); });
        btnAdmin.addActionListener(e -> {
            String pass = JOptionPane.showInputDialog(frame, "Enter admin passphrase:");
            if ("admin".equals(pass)) {
                refreshAdmin();
                cards.show(root, "admin");
            } else {
                JOptionPane.showMessageDialog(frame, "Admin authentication failed.");
            }
        });
        btnTheme.addActionListener(e -> toggleTheme());

        return p;
    }

    private JPanel buildAdminPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        JLabel title = new JLabel("Admin - Accounts");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        p.add(title, BorderLayout.NORTH);

        DefaultTableModel tm = new DefaultTableModel(new Object[]{"Account ID","Holder","Balance"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tbl = new JTable(tm);
        p.add(new JScrollPane(tbl), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton back = new JButton("Back");
        bottom.add(back);
        p.add(bottom, BorderLayout.SOUTH);

        back.addActionListener(e -> cards.show(root, "dashboard"));

        p.putClientProperty("adminModel", tm);
        return p;
    }

    // Actions and helpers

    private void doLogin() {
        String id = tfAccount.getText().trim();
        String pin = new String(pfPin.getPassword()).trim();
        if (id.isEmpty() || pin.isEmpty()) { JOptionPane.showMessageDialog(frame, "Provide account ID and PIN."); return; }
        try {
            BankAccount acct = service.authenticate(id, pin);
            if (acct == null) { JOptionPane.showMessageDialog(frame, "Invalid credentials."); return; }
            this.currentAccount = acct;
            refreshDashboard();
            cards.show(root, "dashboard");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Login failed: " + ex.getMessage());
        }
    }

    private void showCreateDialog() {
        JPanel p = new JPanel(new GridLayout(0,1,6,6));
        JTextField idf = new JTextField();
        JTextField namef = new JTextField();
        JPasswordField pinf = new JPasswordField();
        JTextField initf = new JTextField("0.0");
        p.add(new JLabel("Account ID:")); p.add(idf);
        p.add(new JLabel("Full name:")); p.add(namef);
        p.add(new JLabel("PIN (4 digits):")); p.add(pinf);
        p.add(new JLabel("Initial deposit:")); p.add(initf);
        int r = JOptionPane.showConfirmDialog(frame, p, "Create Account", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        try {
            String id = idf.getText().trim();
            String name = namef.getText().trim();
            String pin = new String(pinf.getPassword()).trim();
            double init = Double.parseDouble(initf.getText().trim());
            service.createAccount(id, name.toUpperCase(Locale.ROOT), pin, init);
            JOptionPane.showMessageDialog(frame, "Account created.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Account creation failed: " + ex.getMessage());
        }
    }

    private void refreshDashboard() {
        if (currentAccount == null) return;
        lblWelcome.setText("Hello, " + currentAccount.getHolderName() + " - Acc: " + currentAccount.getAccountId());
        lblBalance.setText(String.format("Balance: Rs.%.2f", currentAccount.getBalance()));
        refreshHistoryTable();
    }

    private void refreshHistoryTable() {
        histModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Transaction t : currentAccount.getHistory()) {
            histModel.addRow(new Object[]{ t.getId(), sdf.format(java.sql.Timestamp.valueOf(t.getTimestamp())), t.getType().name(), String.format("%.2f", t.getAmount()), t.getNote()});
        }
    }

    private void showDeposit() {
        if (currentAccount == null) return;
        String s = JOptionPane.showInputDialog(frame, "Enter deposit amount (Rs.):");
        if (s == null) return;
        try {
            double a = Double.parseDouble(s);
            Transaction tx = service.deposit(currentAccount.getAccountId(), a, "Deposit via UI");
            JOptionPane.showMessageDialog(frame, "Deposit ok. Tx ID: " + tx.getId());
            refreshDashboard();
        } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "Deposit failed: " + ex.getMessage()); }
    }

    private void showWithdraw() {
        if (currentAccount == null) return;
        String s = JOptionPane.showInputDialog(frame, "Enter withdrawal amount (Rs.):");
        if (s == null) return;
        try {
            double a = Double.parseDouble(s);
            Transaction tx = service.withdraw(currentAccount.getAccountId(), a, "Withdrawal via UI");
            JOptionPane.showMessageDialog(frame, "Withdraw ok. Tx ID: " + tx.getId());
            refreshDashboard();
        } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "Withdrawal failed: " + ex.getMessage()); }
    }

    private void showTransfer() {
        if (currentAccount == null) return;
        JPanel p = new JPanel(new GridLayout(0,1,6,6));
        JTextField to = new JTextField();
        JTextField amt = new JTextField();
        p.add(new JLabel("Beneficiary Account ID:")); p.add(to);
        p.add(new JLabel("Amount (Rs.):")); p.add(amt);
        int r = JOptionPane.showConfirmDialog(frame, p, "Transfer", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        try {
            String toId = to.getText().trim();
            double a = Double.parseDouble(amt.getText().trim());
            List<Transaction> txs = service.transfer(currentAccount.getAccountId(), toId, a);
            JOptionPane.showMessageDialog(frame, "Transfer ok. Tx IDs: " + txs.get(0).getId() + " , " + txs.get(1).getId());
            refreshDashboard();
        } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "Transfer failed: " + ex.getMessage()); }
    }

    private void exportHistory() {
        if (currentAccount == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("history_" + currentAccount.getAccountId() + ".csv"));
        int r = chooser.showSaveDialog(frame);
        if (r != JFileChooser.APPROVE_OPTION) return;
        File out = chooser.getSelectedFile();
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
            pw.println("TxID,DateTime,Type,Amount,Note");
            for (Transaction t : currentAccount.getHistory()) pw.println(t.toCsvRow());
            JOptionPane.showMessageDialog(frame, "Exported to " + out.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Export failed: " + ex.getMessage());
        }
    }

    private void refreshAdmin() {
        for (Component c : root.getComponents()) {
            if (c instanceof JPanel) {
                JPanel p = (JPanel) c;
                Object obj = p.getClientProperty("adminModel");
                if (obj instanceof DefaultTableModel) {
                    DefaultTableModel tm = (DefaultTableModel) obj;
                    tm.setRowCount(0);
                    for (BankAccount a : service.listAccounts()) tm.addRow(new Object[]{a.getAccountId(), a.getHolderName(), String.format("Rs.%.2f", a.getBalance())});
                }
            }
        }
    }

    private void toggleTheme() {
        darkMode = !darkMode;
        if (darkMode) {
            UIManager.put("control", new Color(60,60,60));
            UIManager.put("text", new Color(220,220,220));
            UIManager.put("nimbusBase", new Color(45,45,45));
        } else {
            UIManager.put("control", null);
            UIManager.put("text", null);
            UIManager.put("nimbusBase", null);
        }
        SwingUtilities.updateComponentTreeUI(frame);
    }

    /* ========================
       ATMService (thread-safe)
       ======================== */
    private static class ATMService {
        private final Path storage;
        private final Map<String, BankAccount> accounts = new HashMap<>();
        private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();

        ATMService(Path storage) {
            this.storage = storage;
            load();
        }

        private void load() {
            rw.writeLock().lock();
            try {
                if (!Files.exists(storage)) return;
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(storage.toFile()))) {
                    Object obj = ois.readObject();
                    if (obj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, BankAccount> map = (Map<String, BankAccount>) obj;
                        accounts.clear();
                        accounts.putAll(map);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: failed to read accounts file: " + e.getMessage());
                }
            } finally { rw.writeLock().unlock(); }
        }

        private void save() {
            rw.readLock().lock();
            try {
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(storage.toFile()))) {
                    oos.writeObject(accounts);
                } catch (IOException e) {
                    System.err.println("Failed to save accounts: " + e.getMessage());
                }
            } finally { rw.readLock().unlock(); }
        }

        public int countAccounts() {
            rw.readLock().lock();
            try { return accounts.size(); } finally { rw.readLock().unlock(); }
        }

        public Collection<BankAccount> listAccounts() {
            rw.readLock().lock();
            try { return new ArrayList<>(accounts.values()); } finally { rw.readLock().unlock(); }
        }

        public BankAccount authenticate(String id, String pin) {
            rw.readLock().lock();
            try {
                BankAccount a = accounts.get(id);
                if (a == null) return null;
                if (a.verifyPin(pin)) return a;
                return null;
            } finally { rw.readLock().unlock(); }
        }

        public void createAccount(String id, String name, String pin, double initial) {
            rw.writeLock().lock();
            try {
                if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Account ID required");
                if (accounts.containsKey(id)) throw new IllegalArgumentException("Account already exists");
                BankAccount a = new BankAccount(id, name, pin, initial);
                accounts.put(id, a);
                save();
            } finally { rw.writeLock().unlock(); }
        }

        public Transaction deposit(String id, double amt, String note) {
            rw.writeLock().lock();
            try {
                BankAccount a = accounts.get(id);
                if (a == null) throw new IllegalArgumentException("Account not found");
                Transaction tx = a.deposit(amt, note);
                save();
                return tx;
            } finally { rw.writeLock().unlock(); }
        }

        public Transaction withdraw(String id, double amt, String note) {
            rw.writeLock().lock();
            try {
                BankAccount a = accounts.get(id);
                if (a == null) throw new IllegalArgumentException("Account not found");
                Transaction tx = a.withdraw(amt, note);
                save();
                return tx;
            } finally { rw.writeLock().unlock(); }
        }

        public List<Transaction> transfer(String fromId, String toId, double amt) {
            rw.writeLock().lock();
            try {
                if (fromId.equals(toId)) throw new IllegalArgumentException("Cannot transfer to same account");
                BankAccount from = accounts.get(fromId);
                BankAccount to = accounts.get(toId);
                if (from == null) throw new IllegalArgumentException("Source account not found");
                if (to == null) throw new IllegalArgumentException("Beneficiary not found");
                Transaction w = from.withdraw(amt, "Transfer to " + toId);
                Transaction d = to.deposit(amt, "Transfer from " + fromId);
                from.logTransfer(amt, "Transfer to " + toId);
                to.logTransfer(amt, "Transfer from " + fromId);
                save();
                return Arrays.asList(w, d);
            } finally { rw.writeLock().unlock(); }
        }
    }
}
