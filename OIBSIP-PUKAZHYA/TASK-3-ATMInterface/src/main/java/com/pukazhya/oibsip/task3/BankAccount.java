package com.pukazhya.oibsip.task3;

import java.io.Serializable;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * BankAccount.java
 *
 * Thread-safe account model. Keeps a capped history and stores a PIN hash.
 * Author: PUKAZHYA P
 */
public class BankAccount implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String accountId;
    private final String holderName;
    private final String pinHashHex; // sha-256 hex
    private double balance;
    private final List<Transaction> history;

    private static final int HISTORY_CAP = 500;

    public BankAccount(String accountId, String holderName, String plainPin, double initialBalance) {
        if (accountId == null || accountId.trim().isEmpty()) throw new IllegalArgumentException("accountId required");
        this.accountId = accountId;
        this.holderName = holderName == null ? "" : holderName.toUpperCase(Locale.ROOT);
        this.pinHashHex = sha256Hex(plainPin == null ? "" : plainPin);
        this.balance = initialBalance < 0.0 ? 0.0 : initialBalance;
        this.history = new ArrayList<>();
        if (this.balance > 0.0) {
            addToHistory(new Transaction(Transaction.Type.DEPOSIT, this.balance, "Initial credit"));
        }
    }

    public String getAccountId() { return accountId; }
    public String getHolderName() { return holderName; }

    public synchronized double getBalance() { return balance; }

    public synchronized boolean verifyPin(String plainPin) {
        return sha256Hex(plainPin == null ? "" : plainPin).equals(pinHashHex);
    }

    public synchronized Transaction deposit(double amount, String note) {
        if (amount <= 0.0) throw new IllegalArgumentException("Amount must be positive");
        balance += amount;
        Transaction tx = new Transaction(Transaction.Type.DEPOSIT, amount, note);
        addToHistory(tx);
        return tx;
    }

    public synchronized Transaction withdraw(double amount, String note) {
        if (amount <= 0.0) throw new IllegalArgumentException("Amount must be positive");
        if (amount > balance) throw new IllegalArgumentException("Insufficient funds");
        balance -= amount;
        Transaction tx = new Transaction(Transaction.Type.WITHDRAW, amount, note);
        addToHistory(tx);
        return tx;
    }

    public synchronized Transaction logTransfer(double amount, String note) {
        Transaction tx = new Transaction(Transaction.Type.TRANSFER, amount, note);
        addToHistory(tx);
        return tx;
    }

    private synchronized void addToHistory(Transaction tx) {
        history.add(0, tx);
        if (history.size() > HISTORY_CAP) history.remove(history.size() - 1);
    }

    public synchronized List<Transaction> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    @Override
    public String toString() {
        return String.format("%s - %s - Rs.%.2f", accountId, holderName, balance);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
