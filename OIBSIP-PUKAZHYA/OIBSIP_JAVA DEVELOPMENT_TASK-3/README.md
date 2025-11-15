======================================================================
TASK-3 README.md

(ATM Interface System)

ATM Interface System — OIBSIP JAVA DEVELOPMENT TASK-3
Overview
This project is a complete ATM Interface System developed in Java (Swing) as part of the OASIS Infobyte Java Development Internship. It simulates real ATM operations including deposit, withdrawal, balance inquiry, fund transfer, transaction history, admin panel, and a modern Swing-based GUI.

The system uses a modular architecture with secure password hashing, persistent storage (accounts.dat), and a responsive UI built using Java Swing components.

Folder Structure
OIBSIP_JAVA DEVELOPMENT_TASK-3
|
+--- src/
|      Main.java                        (GUI and main ATM workflow)
|      BankAccount.java                 (Account model + business logic)
|      Transaction.java                 (Transaction model)
|      HashUtil.java                    (Password hashing utility - SHA-256)
|
+--- accounts.dat                       (Persistent account database)
+--- pom.xml                            (Maven build configuration)
+--- README.md                          (Project documentation)
+--- .gitignore                         (Ignored files configuration)

Features
User Account Features
Create new account
Login with hashed password
Deposit funds
Withdraw with validation
Check balance
Transfer money to other accounts

Transaction History
Complete transaction log
Export option (CSV/text) included
Timestamp and type tracking

Admin Panel
Admin passphrase for access
View all registered accounts
View full transaction history
System dashboard for monitoring

Modern UI / UX (Swing)
Clean window layout
Card-based navigation
Light/Dark theme toggle
Optimized buttons and panels

Security
Passwords hashed using SHA-256
No plain-text storage
accounts.dat auto-updates on every transaction

How to Run (Maven)
1. Open terminal in the task directory
   cd "OIBSIP_JAVA DEVELOPMENT_TASK-3"

2. Build the project
   mvn clean install

3. Run the application
   mvn exec:java -Dexec.mainClass="com.pukazhya.oibsip.task3.Main"

Notes
accounts.dat must remain in the project root to retain user data.
Admin passphrase is set inside Main.java (default: admin).
Theme toggle takes effect instantly across UI.

Technologies Used
Java SE
Swing GUI
OOP principles
Secure hashing (SHA-256)
File-based persistence
Maven build system

Developer
Pukazhya
OASIS Infobyte – Java Development Intern
GitHub: https://github.com/Pukazhya

======================================================================