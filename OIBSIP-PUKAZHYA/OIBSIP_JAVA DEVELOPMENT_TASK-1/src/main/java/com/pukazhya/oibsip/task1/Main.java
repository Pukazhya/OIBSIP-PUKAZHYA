package com.pukazhya.oibsip.task1;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.*;
import java.util.stream.Collectors;

/**
 * Main.java
 *
 * Online Reservation System — by PUKAZHYA
 *
 * Single-file Swing application suitable for Maven layout:
 * src/main/java/com/pukazhya/oibsip/task1/Main.java
 *
 * Features:
 * - Book tickets with generated unique PNR
 * - Save/load reservations to reservations.csv (robust quoting)
 * - View/search/cancel reservations
 * - Fare preview using a deterministic heuristic
 * - Export ticket text file
 *
 * Run (after 'mvn compile'):
 *   mvn exec:java -Dexec.mainClass="com.pukazhya.oibsip.task1.Main"
 *
 * Author: PUKAZHYA (rebranded & improved)
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                ReservationService service = new ReservationService(Paths.get("reservations.csv"));
                ReservationGUI gui = new ReservationGUI(service);
                gui.start();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, "Startup error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}

/* ----------------------------
   Domain models
   ---------------------------- */
class Passenger {
    private final String name;
    private final int age;

    Passenger(String name, int age) {
        this.name = name;
        this.age = age;
    }

    String getName() { return name; }
    int getAge() { return age; }
}

final class Reservation {
    private final String pnr;
    private final Passenger passenger;
    private final String trainNo;
    private final String trainName;
    private final String classType;
    private final String from;
    private final String to;
    private final LocalDateTime bookingTime;
    private final LocalDate travelDate;
    private final double fare;
    private final String status;
    private final String cancelReason;

    Reservation(String pnr, Passenger passenger, String trainNo, String trainName, String classType,
                String from, String to, LocalDateTime bookingTime, LocalDate travelDate, double fare,
                String status, String cancelReason) {
        this.pnr = pnr;
        this.passenger = passenger;
        this.trainNo = trainNo;
        this.trainName = trainName;
        this.classType = classType;
        this.from = from;
        this.to = to;
        this.bookingTime = bookingTime;
        this.travelDate = travelDate;
        this.fare = fare;
        this.status = status;
        this.cancelReason = cancelReason == null ? "" : cancelReason;
    }

    String getPnr() { return pnr; }
    Passenger getPassenger() { return passenger; }
    String getTrainNo() { return trainNo; }
    String getTrainName() { return trainName; }
    String getClassType() { return classType; }
    String getFrom() { return from; }
    String getTo() { return to; }
    LocalDateTime getBookingTime() { return bookingTime; }
    LocalDate getTravelDate() { return travelDate; }
    double getFare() { return fare; }
    String getStatus() { return status; }
    String getCancelReason() { return cancelReason; }

    // CSV row with quoting
    String toCSVRow(DateTimeFormatter dtfFull, DateTimeFormatter dtfDate) {
        List<String> cols = Arrays.asList(
                pnr,
                passenger.getName(),
                String.valueOf(passenger.getAge()),
                trainNo,
                trainName,
                classType,
                from,
                to,
                bookingTime.format(dtfFull),
                travelDate.format(dtfDate),
                String.format(Locale.US, "%.2f", fare),
                status,
                cancelReason
        );
        return cols.stream().map(Reservation::csvQuote).collect(Collectors.joining(","));
    }

    static String csvQuote(String s) {
        if (s == null) s = "";
        boolean need = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String escaped = s.replace("\"", "\"\"");
        return need ? "\"" + escaped + "\"" : escaped;
    }
}

/* ----------------------------
   Service: persistence, fare, PNR
   ---------------------------- */
class ReservationService {
    private final Path dataFile;
    private final List<Reservation> reservations = new ArrayList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final DateTimeFormatter dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter dtfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // sample trains
    private final LinkedHashMap<String,String> trains = new LinkedHashMap<>();

    ReservationService(Path dataFile) throws IOException {
        this.dataFile = dataFile;
        seedTrains();
        load();
    }

    private void seedTrains() {
        trains.put("22401", "Rajdhani Express");
        trains.put("12049", "Shatabdi Deluxe");
        trains.put("12345", "InterCity Express");
        trains.put("22411", "Duronto");
        trains.put("SPECIAL", "Local Special");
    }

    Map<String,String> getTrains() {
        return Collections.unmodifiableMap(trains);
    }

    // Thread-safe load
    private void load() throws IOException {
        lock.writeLock().lock();
        try {
            reservations.clear();
            if (!Files.exists(dataFile)) return;
            List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
            for (String ln : lines) {
                Reservation r = parseLine(ln);
                if (r != null) reservations.add(r);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Thread-safe save (uses write lock)
    private void save() {
        lock.writeLock().lock();
        try {
            List<String> lines = reservations.stream().map(r -> r.toCSVRow(dtfFull, dtfDate)).collect(Collectors.toList());
            try {
                Files.write(dataFile, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Failed to save reservations: " + e.getMessage()));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // CSV parsing supporting quoted fields
    private Reservation parseLine(String line) {
        try {
            List<String> cols = parseCSV(line);
            if (cols.size() < 13) return null;
            String pnr = cols.get(0);
            String name = cols.get(1);
            int age = Integer.parseInt(cols.get(2));
            String trainNo = cols.get(3);
            String trainName = cols.get(4);
            String classType = cols.get(5);
            String from = cols.get(6);
            String to = cols.get(7);
            LocalDateTime booking = LocalDateTime.parse(cols.get(8), dtfFull);
            LocalDate travel = LocalDate.parse(cols.get(9), dtfDate);
            double fare = Double.parseDouble(cols.get(10));
            String status = cols.get(11);
            String cancelReason = cols.get(12);
            return new Reservation(pnr, new Passenger(name, age), trainNo, trainName, classType, from, to, booking, travel, fare, status, cancelReason);
        } catch (Exception ex) {
            // skip malformed lines
            return null;
        }
    }

    // Basic CSV parser that supports quoted fields with "" escaping.
    private static List<String> parseCSV(String line) {
        List<String> out = new ArrayList<>();
        if (line == null || line.isEmpty()) return out;
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(ch);
                }
            }
        }
        out.add(cur.toString());
        return out;
    }

    // Create a booking
    Reservation book(String name, int age, String trainNo, String trainName, String classType,
                     String from, String to, LocalDate travelDate) {
        // fare
        double fare = estimateFare(trainNo, from, to, classType);
        String pnr = generatePNR();
        Reservation r = new Reservation(pnr, new Passenger(name, age), trainNo, trainName, classType, from, to,
                LocalDateTime.now(), travelDate, fare, "Booked", "");
        lock.writeLock().lock();
        try {
            reservations.add(r);
            save();
        } finally {
            lock.writeLock().unlock();
        }
        return r;
    }

    // Cancel (returns true if success)
    boolean cancel(String pnr, String reason) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < reservations.size(); i++) {
                Reservation r = reservations.get(i);
                if (r.getPnr().equalsIgnoreCase(pnr)) {
                    if ("Cancelled".equalsIgnoreCase(r.getStatus())) return false;
                    Reservation cancelled = new Reservation(r.getPnr(), r.getPassenger(), r.getTrainNo(), r.getTrainName(), r.getClassType(),
                            r.getFrom(), r.getTo(), r.getBookingTime(), r.getTravelDate(), r.getFare(), "Cancelled", reason);
                    reservations.set(i, cancelled);
                    save();
                    return true;
                }
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Search by PNR
    Reservation findByPNR(String pnr) {
        lock.readLock().lock();
        try {
            return reservations.stream().filter(r -> r.getPnr().equalsIgnoreCase(pnr)).findFirst().orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    // Search by name (partial)
    List<Reservation> findByName(String namePart) {
        lock.readLock().lock();
        try {
            String q = namePart.toLowerCase();
            return reservations.stream().filter(r -> r.getPassenger().getName().toLowerCase().contains(q)).collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    // Return snapshot list
    List<Reservation> allReservations() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(reservations);
        } finally {
            lock.readLock().unlock();
        }
    }

    // Fare estimator - deterministic heuristic (no external data)
    double estimateFare(String trainNo, String from, String to, String classType) {
        // base estimate from pseudo-distance
        double dist = estimateDistance(trainNo, from, to); // km
        double base = 0.5 * dist + 100;
        double cls = classMultiplier(classType);
        double fare = base * cls;
        // round to nearest 5
        fare = Math.max(40, Math.round(fare / 5.0) * 5.0);
        return fare;
    }

    private double estimateDistance(String trainNo, String from, String to) {
        if (from != null && !from.isEmpty() && to != null && !to.isEmpty()) {
            int len = Math.abs(from.trim().length() - to.trim().length());
            int sum = (from.trim().length() + to.trim().length());
            double base = 60 + len * 10 + (sum % 200);
            base += Math.abs(trainNo.hashCode()) % 200;
            return Math.min(2000, Math.max(30, base));
        } else {
            return 100 + Math.abs(trainNo.hashCode()) % 800;
        }
    }

    private double classMultiplier(String classType) {
        if (classType == null) return 1.0;
        switch (classType) {
            case "AC 1st": return 3.0;
            case "AC 2-tier": return 2.2;
            case "AC 3-tier": return 1.6;
            case "Sleeper": return 1.0;
            case "General": return 0.6;
            default: return 1.0;
        }
    }

    // PNR generator: PZ + timestamp + 4 random chars
private String generatePNR() {
    String base = "PZ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
    String rnd = randomAlphaNum(4);

    while (true) {
        String candidate = (base + rnd).toUpperCase();
        lock.readLock().lock();
        try {
            boolean exists = reservations.stream().anyMatch(r -> r.getPnr().equals(candidate));
            if (!exists) {
                return candidate;
            }
        } finally {
            lock.readLock().unlock();
        }
        rnd = randomAlphaNum(4);
    }
}

    private static String randomAlphaNum(int n) {
        final String CH = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(CH.charAt(rnd.nextInt(CH.length())));
        return sb.toString();
    }

    // Export a ticket text file; returns path or null
    Path exportTicketText(Reservation r) {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("====================================================");
            lines.add("               PUKAZHYA RAILWAYS (Demo)");
            lines.add("----------------------------------------------------");
            lines.add("PNR: " + r.getPnr());
            lines.add("Passenger: " + r.getPassenger().getName() + "   Age: " + r.getPassenger().getAge());
            lines.add("Train: " + r.getTrainName() + " (" + r.getTrainNo() + ")");
            lines.add("Class: " + r.getClassType());
            lines.add("From: " + r.getFrom() + "   To: " + r.getTo());
            lines.add("Journey Date: " + r.getTravelDate().format(dtfDate));
            lines.add("Booked At: " + r.getBookingTime().format(dtfFull));
            lines.add("Fare: ₹" + new DecimalFormat("#,##0").format(Math.round(r.getFare())));
            lines.add("Status: " + r.getStatus());
            if (r.getCancelReason() != null && !r.getCancelReason().isEmpty()) lines.add("Cancel Reason: " + r.getCancelReason());
            lines.add("----------------------------------------------------");
            lines.add("Generated by: PUKAZHYA - Reservation System");
            lines.add("====================================================");

            Path p = Paths.get(r.getPnr() + "_ticket.txt");
            Files.write(p, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return p;
        } catch (IOException ex) {
            return null;
        }
    }
}

/* ----------------------------
   Swing UI
   ---------------------------- */
class ReservationGUI {
    private final ReservationService service;

    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel root;

    // booking fields
    private JTextField tfName, tfAge, tfFrom, tfTo, tfTrainNo;
    private JComboBox<String> cbClass, cbTrainSelect;
    private JSpinner spDate;
    private JLabel lblFare, lblPNR;

    // view fields
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField tfSearchPNR, tfSearchName;

    private boolean darkTheme = true;

    ReservationGUI(ReservationService service) {
        this.service = service;
    }

    void start() {
        buildUI();
        loadDataToTable();
        applyTheme();
        frame.setVisible(true);
    }

    private void buildUI() {
        frame = new JFrame("Online Reservation System - by PUKAZHYA");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1024, 680);
        frame.setLocationRelativeTo(null);

        root = new JPanel();
        root.setBorder(new EmptyBorder(12,12,12,12));
        cardLayout = new CardLayout();
        root.setLayout(cardLayout);

        JPanel home = createHomePanel();
        JPanel book = createBookPanel();
        JPanel view = createViewPanel();

        root.add(home, "home");
        root.add(book, "book");
        root.add(view, "view");

        frame.setContentPane(root);
        frame.setJMenuBar(createMenuBar());
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();
        mb.setBorder(new EmptyBorder(6,8,6,8));

        JLabel title = new JLabel("PUKAZHYA - Reservation Manager");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setBorder(new EmptyBorder(0,6,0,20));
        mb.add(title);

        mb.add(Box.createHorizontalGlue());

        JButton btnHome = toolbarButton("Dashboard");
        btnHome.addActionListener(e -> cardLayout.show(root, "home"));
        mb.add(btnHome);

        JButton btnBook = toolbarButton("Book Ticket");
        btnBook.addActionListener(e -> { resetBookingForm(); cardLayout.show(root, "book"); });
        mb.add(btnBook);

        JButton btnView = toolbarButton("View / Cancel");
        btnView.addActionListener(e -> { loadDataToTable(); cardLayout.show(root, "view"); });
        mb.add(btnView);

        JToggleButton tgTheme = new JToggleButton("Dark");
        tgTheme.setSelected(darkTheme);
        tgTheme.addActionListener(e -> {
            darkTheme = tgTheme.isSelected();
            tgTheme.setText(darkTheme ? "Dark" : "Light");
            applyTheme();
        });
        mb.add(Box.createHorizontalStrut(12));
        mb.add(tgTheme);

        return mb;
    }

    private JButton toolbarButton(String text) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(6,10,6,10));
        return b;
    }

    private JPanel createHomePanel() {
        JPanel p = new JPanel(new BorderLayout(12,12));
        p.setOpaque(false);

        JLabel header = new JLabel("Dashboard");
        header.setFont(new Font("Segoe UI", Font.BOLD, 22));

        JPanel stats = new JPanel(new GridLayout(1,3,12,12));
        stats.setOpaque(false);

        JPanel c1 = statCard("Total Records", () -> String.valueOf(service.allReservations().size()));
        JPanel c2 = statCard("Active (Booked)", () -> String.valueOf(service.allReservations().stream().filter(r -> "Booked".equalsIgnoreCase(r.getStatus())).count()));
        JPanel c3 = statCard("Cancelled", () -> String.valueOf(service.allReservations().stream().filter(r -> "Cancelled".equalsIgnoreCase(r.getStatus())).count()));

        stats.add(c1); stats.add(c2); stats.add(c3);

        JTextArea info = new JTextArea("Welcome, PUKAZHYA!\nThis application stores data in 'reservations.csv'.\nUse Book Ticket to create new bookings and View/Cancel to manage them.");
        info.setOpaque(false);
        info.setEditable(false);
        info.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        info.setBorder(new EmptyBorder(12,12,12,12));

        p.add(header, BorderLayout.NORTH);
        p.add(stats, BorderLayout.CENTER);
        p.add(info, BorderLayout.SOUTH);
        return p;
    }

    private JPanel statCard(String title, SupplierString supplier) {
        JPanel c = new JPanel(new BorderLayout());
        c.setBorder(new EmptyBorder(12,12,12,12));
        c.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JLabel v = new JLabel(supplier.get());
        v.setFont(new Font("Segoe UI", Font.BOLD, 24));
        javax.swing.Timer timer = new javax.swing.Timer(900, e -> v.setText(supplier.get()));
        timer.start();
        c.add(t, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        return c;
    }

    private JPanel createBookPanel() {
        JPanel p = new JPanel(new BorderLayout(12,12));
        p.setOpaque(false);
        JLabel header = new JLabel("Book a Ticket");
        header.setFont(new Font("Segoe UI", Font.BOLD, 20));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8,8,8,8);
        c.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        c.gridx=0; c.gridy=row; form.add(new JLabel("Passenger Name:"), c);
        tfName = new JTextField(); c.gridx=1; c.gridy=row++; c.weightx=1; form.add(tfName, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Age:"), c);
        tfAge = new JTextField(); c.gridx=1; c.gridy=row++; form.add(tfAge, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Train (select):"), c);
        cbTrainSelect = new JComboBox<>(service.getTrains().keySet().toArray(new String[0]));
        cbTrainSelect.setEditable(false);
        cbTrainSelect.addActionListener(e -> {
            String tn = (String) cbTrainSelect.getSelectedItem();
            tfTrainNo.setText(tn);
            calculateFarePreview();
        });
        c.gridx=1; c.gridy=row++; form.add(cbTrainSelect, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Train No/code:"), c);
        tfTrainNo = new JTextField(); c.gridx=1; c.gridy=row++; form.add(tfTrainNo, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Class:"), c);
        cbClass = new JComboBox<>(new String[] {"AC 1st","AC 2-tier","AC 3-tier","Sleeper","General"});
        c.gridx=1; c.gridy=row++; form.add(cbClass, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("From:"), c);
        tfFrom = new JTextField(); c.gridx=1; c.gridy=row++; form.add(tfFrom, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("To:"), c);
        tfTo = new JTextField(); c.gridx=1; c.gridy=row++; form.add(tfTo, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Journey Date:"), c);
        Date init = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        spDate = new JSpinner(new SpinnerDateModel(init, null, null, Calendar.DAY_OF_MONTH));
        JSpinner.DateEditor de = new JSpinner.DateEditor(spDate, "yyyy-MM-dd");
        spDate.setEditor(de);
        c.gridx=1; c.gridy=row++; form.add(spDate, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Fare (preview):"), c);
        lblFare = new JLabel("—"); c.gridx=1; c.gridy=row++; form.add(lblFare, c);

        c.gridx=0; c.gridy=row; form.add(new JLabel("Generated PNR:"), c);
        lblPNR = new JLabel("—"); c.gridx=1; c.gridy=row++; form.add(lblPNR, c);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnCalc = new JButton("Calculate Fare");
        btnCalc.addActionListener(e -> calculateFarePreview());
        JButton btnBook = new JButton("Book & Generate PNR");
        btnBook.addActionListener(e -> submitBooking());
        JButton btnSample = new JButton("Export Sample CSV");
        btnSample.addActionListener(e -> exportSampleCSV());
        actions.add(btnCalc); actions.add(btnBook); actions.add(btnSample);

        p.add(header, BorderLayout.NORTH);
        p.add(form, BorderLayout.CENTER);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createViewPanel() {
        JPanel p = new JPanel(new BorderLayout(12,12));
        p.setOpaque(false);
        JLabel header = new JLabel("View / Cancel Reservations");
        header.setFont(new Font("Segoe UI", Font.BOLD, 20));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        top.add(new JLabel("Search PNR:"));
        tfSearchPNR = new JTextField(10);
        top.add(tfSearchPNR);
        JButton btnSearchPNR = new JButton("Search");
        btnSearchPNR.addActionListener(e -> searchByPNR());
        top.add(btnSearchPNR);

        top.add(Box.createHorizontalStrut(12));
        top.add(new JLabel("Search Name:"));
        tfSearchName = new JTextField(12);
        top.add(tfSearchName);
        JButton btnSearchName = new JButton("Find");
        btnSearchName.addActionListener(e -> searchByName());
        top.add(btnSearchName);

        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(e -> loadDataToTable());
        top.add(btnRefresh);

        tableModel = new DefaultTableModel(new Object[] {"PNR","Name","Train(no)","Class","Date","Fare","Status"}, 0) {
            public boolean isCellEditable(int r,int c){ return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(26);
        table.setAutoCreateRowSorter(true);
        JScrollPane jsp = new JScrollPane(table);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnView = new JButton("View Details");
        btnView.addActionListener(e -> viewSelected());
        JButton btnCancel = new JButton("Cancel Selected");
        btnCancel.addActionListener(e -> cancelSelected());
        bottom.add(btnView); bottom.add(btnCancel);

        p.add(header, BorderLayout.NORTH);
        p.add(top, BorderLayout.NORTH); // corrected from AFTER_LINE_ENDS
        p.add(jsp, BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private void applyTheme() {
        Color bg = darkTheme ? new Color(34,34,38) : Color.WHITE;
        Color fg = darkTheme ? Color.WHITE : Color.DARK_GRAY;
        Color panel = darkTheme ? new Color(44,44,48) : new Color(245,245,245);
        Color accent = darkTheme ? new Color(75,135,255) : new Color(20,110,230);

        frame.getContentPane().setBackground(bg);
        for (Component comp : getAll(frame.getContentPane())) {
            style(comp, bg, fg, panel, accent);
        }
        frame.repaint();
    }

    private void style(Component comp, Color bg, Color fg, Color panel, Color accent) {
        comp.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        if (comp instanceof JPanel) {
            comp.setBackground(panel);
        } else if (comp instanceof JLabel) {
            comp.setForeground(fg);
        } else if (comp instanceof JButton) {
            JButton b = (JButton) comp;
            b.setBackground(accent);
            b.setForeground(Color.WHITE);
            b.setFocusPainted(false);
        } else if (comp instanceof JTextField || comp instanceof JSpinner || comp instanceof JComboBox || comp instanceof JTable || comp instanceof JTextArea) {
            comp.setBackground(darkTheme ? new Color(60,60,64) : Color.WHITE);
            comp.setForeground(fg);
            if (comp instanceof JTable) {
                JTable t = (JTable) comp;
                t.getTableHeader().setBackground(panel);
                t.getTableHeader().setForeground(fg);
            }
        }
        if (comp instanceof Container) {
            for (Component c : ((Container) comp).getComponents()) style(c, bg, fg, panel, accent);
        }
    }

    private java.util.List<Component> getAll(Container c) {
        List<Component> out = new ArrayList<>();
        for (Component comp : c.getComponents()) {
            out.add(comp);
            if (comp instanceof Container) out.addAll(getAll((Container) comp));
        }
        return out;
    }

    private void resetBookingForm() {
        tfName.setText("");
        tfAge.setText("");
        tfFrom.setText("");
        tfTo.setText("");
        tfTrainNo.setText((String) cbTrainSelect.getSelectedItem());
        cbClass.setSelectedIndex(0);
        lblFare.setText("-");
        lblPNR.setText("-");
        spDate.setValue(Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }

  private void calculateFarePreview() {
    try {
        String trainNo = tfTrainNo.getText().trim();
        String from = tfFrom.getText().trim();
        String to = tfTo.getText().trim();
        String cls = (String) cbClass.getSelectedItem();
        double fare = service.estimateFare(trainNo, from, to, cls);
        lblFare.setText("Rs. " + new DecimalFormat("#,##0").format(fare));
    } catch (Exception ex) {
        lblFare.setText("-");
    }
}

    private void submitBooking() {
        String name = tfName.getText().trim();
        String ageStr = tfAge.getText().trim();
        String from = tfFrom.getText().trim();
        String to = tfTo.getText().trim();
        String trainNo = tfTrainNo.getText().trim();
        String cls = (String) cbClass.getSelectedItem();
        Date dt = (Date) spDate.getValue();
        LocalDate travel = Instant.ofEpochMilli(dt.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();

        // validations
        if (name.length() < 2) { JOptionPane.showMessageDialog(frame, "Enter a valid passenger name."); return; }
        int age;
        try { age = Integer.parseInt(ageStr); if (age <= 0 || age > 120) throw new Exception(); } catch (Exception e) { JOptionPane.showMessageDialog(frame, "Enter a valid age (1-120)."); return; }
        if (from.isEmpty() || to.isEmpty()) { JOptionPane.showMessageDialog(frame, "Enter From and To places."); return; }
        if (trainNo.isEmpty()) { JOptionPane.showMessageDialog(frame, "Select or enter a train number/code."); return; }
        if (travel.isBefore(LocalDate.now())) { JOptionPane.showMessageDialog(frame, "Travel date cannot be in the past."); return; }

        String trainName = service.getTrains().getOrDefault(trainNo, service.getTrains().getOrDefault((String)cbTrainSelect.getSelectedItem(), "Express"));
        Reservation r = service.book(name, age, trainNo, trainName, cls, from, to, travel);
        lblPNR.setText(r.getPnr());
        JOptionPane.showMessageDialog(frame, "Booked successfully! PNR: " + r.getPnr());
        loadDataToTable();
    }

    private void loadDataToTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (Reservation r : service.allReservations()) {
                tableModel.addRow(new Object[] {
                        r.getPnr(),
                        r.getPassenger().getName(),
                        r.getTrainName() + " (" + r.getTrainNo() + ")",
                        r.getClassType(),
                        r.getTravelDate().toString(),
                        "Rs." + (int)Math.round(r.getFare()),
                        r.getStatus()
                });
            }
        });
    }

    private void searchByPNR() {
        String q = tfSearchPNR.getText().trim();
        if (q.isEmpty()) { JOptionPane.showMessageDialog(frame, "Enter PNR to search."); return; }
        Reservation r = service.findByPNR(q);
        if (r == null) { JOptionPane.showMessageDialog(frame, "PNR not found: " + q); return; }
        showReservationDialog(r);
    }

    private void searchByName() {
        String q = tfSearchName.getText().trim();
        if (q.isEmpty()) { JOptionPane.showMessageDialog(frame, "Enter name to search."); return; }
        List<Reservation> matches = service.findByName(q);
        if (matches.isEmpty()) { JOptionPane.showMessageDialog(frame, "No matches for: " + q); return; }
        if (matches.size() == 1) {
            showReservationDialog(matches.get(0));
        } else {
            String[] choices = matches.stream().map(m -> m.getPnr() + " — " + m.getPassenger().getName() + " (" + m.getTravelDate() + ")").toArray(String[]::new);
            String pick = (String) JOptionPane.showInputDialog(frame, "Multiple matches - choose one", "Choose", JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
            if (pick != null) {
                String pnr = pick.split(" — ")[0].trim();
                Reservation r = service.findByPNR(pnr);
                if (r != null) showReservationDialog(r);
            }
        }
    }

    private void viewSelected() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(frame, "Select a row first."); return; }
        int modelRow = table.convertRowIndexToModel(row);
        String pnr = (String) tableModel.getValueAt(modelRow, 0);
        Reservation r = service.findByPNR(pnr);
        if (r != null) showReservationDialog(r);
    }

    private void cancelSelected() {
        int row = table.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(frame, "Select a row to cancel."); return; }
        int modelRow = table.convertRowIndexToModel(row);
        String pnr = (String) tableModel.getValueAt(modelRow, 0);
        Reservation r = service.findByPNR(pnr);
        if (r == null) { JOptionPane.showMessageDialog(frame, "Reservation not found."); return; }
        if ("Cancelled".equalsIgnoreCase(r.getStatus())) { JOptionPane.showMessageDialog(frame, "Already cancelled."); return; }
        String reason = JOptionPane.showInputDialog(frame, "Enter cancellation reason (required):");
        if (reason == null || reason.trim().length() < 3) { JOptionPane.showMessageDialog(frame, "Cancellation aborted. Reason required."); return; }
        boolean ok = service.cancel(pnr, reason.trim());
        if (ok) { JOptionPane.showMessageDialog(frame, "Reservation cancelled."); loadDataToTable(); }
        else JOptionPane.showMessageDialog(frame, "Cancellation failed.");
    }

    private void showReservationDialog(Reservation r) {
        StringBuilder sb = new StringBuilder();
        sb.append("PNR: ").append(r.getPnr()).append("\n");
        sb.append("Name: ").append(r.getPassenger().getName()).append(" (Age ").append(r.getPassenger().getAge()).append(")\n");
        sb.append("Train: ").append(r.getTrainName()).append(" (").append(r.getTrainNo()).append(")\n");
        sb.append("Class: ").append(r.getClassType()).append("\n");
        sb.append("From → To: ").append(r.getFrom()).append(" → ").append(r.getTo()).append("\n");
        sb.append("Journey Date: ").append(r.getTravelDate()).append("\n");
        sb.append("Booked At: ").append(r.getBookingTime()).append("\n");
        sb.append("Fare: ₹").append((int)Math.round(r.getFare())).append("\n");
        sb.append("Status: ").append(r.getStatus()).append("\n");
        if ("Cancelled".equalsIgnoreCase(r.getStatus())) sb.append("Cancel Reason: ").append(r.getCancelReason()).append("\n");

        JTextArea ta = new JTextArea(sb.toString());
        ta.setEditable(false);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        ta.setBorder(new EmptyBorder(8,8,8,8));

        int opt = JOptionPane.showOptionDialog(frame, new JScrollPane(ta), "Reservation Details",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                new String[] {"Export Ticket", "Close"}, "Close");

        if (opt == 0) {
            Path p = service.exportTicketText(r);
            if (p != null) {
                try { Desktop.getDesktop().open(p.toFile()); } catch (Exception ex) { /* ignore */ }
            } else {
                JOptionPane.showMessageDialog(frame, "Export failed.");
            }
        }
    }

    private void exportSampleCSV() {
        try {
            Path p = Paths.get("reservations_sample_template.csv");
            List<String> lines = Arrays.asList(
                    "PNR,Name,Age,TrainNo,TrainName,Class,From,To,BookingTime,TravelDate,Fare,Status,CancelReason",
                    "\"PZEX01\",\"John Doe\",30,22401,\"Rajdhani Express\",\"AC 3-tier\",\"CityA\",\"CityB\",\"2025-10-30 19:46:00\",\"2025-11-15\",1200.00,Booked,",
                    "\"PZEX02\",\"Anita\",28,12049,\"Shatabdi Deluxe\",\"Sleeper\",\"CityX\",\"CityY\",\"2025-10-30 19:46:00\",\"2025-12-01\",550.00,Cancelled,\"User requested\""
            );
            Files.write(p, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try { Desktop.getDesktop().open(p.toFile()); } catch (Exception ex) { /* ignore */ }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Could not export sample CSV: " + ex.getMessage());
        }
    }
}

// small functional-style interface (simple replacement for Supplier<String> to avoid imports)
interface SupplierString {
    String get();
}
