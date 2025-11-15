package com.pukazhya.oibsip.task3;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Transaction.java
 *
 * Transaction model used by the ATM system.
 * Author: PUKAZHYA P
 */
public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type { DEPOSIT, WITHDRAW, TRANSFER }

    private final String id;
    private final LocalDateTime timestamp;
    private final Type type;
    private final double amount;
    private final String note;

    public Transaction(Type type, double amount, String note) {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.type = type;
        this.amount = amount;
        this.note = note == null ? "" : note;
    }

    public String getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Type getType() { return type; }
    public double getAmount() { return amount; }
    public String getNote() { return note; }

    public String toCsvRow() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String ts = timestamp.format(f);
        return quote(id) + "," + quote(ts) + "," + type.name() + "," + String.format("%.2f", amount) + "," + quote(note);
    }

    private static String quote(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String toString() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return String.format("%s | %s | %s | Rs.%.2f | %s", id, timestamp.format(f), type.name(), amount, note);
    }
}
