======================================================================
TASK-2 README.md

(Number Guessing Game)

Number Guessing Game — OIBSIP JAVA DEVELOPMENT TASK-2
Overview
This project is an advanced Number Guessing Game developed in Java as part of the OASIS Infobyte Java Development Internship. It features a dynamically generated target number, multiple rounds, adaptive difficulty, modernized logic, and a persistent leaderboard stored in task2_leaderboard.csv.

The application uses a clean modular design with separate components for gameplay, UI logic, scoring, and the leaderboard system.

Folder Structure
OIBSIP_JAVA DEVELOPMENT_TASK-2
|
+--- src/
|      Main.java                        (Application entry point + UI flow)
|      GameEngine.java                  (Core gameplay engine)
|      NumberGuessGame.java             (Game session controller)
|
+--- task2_leaderboard.csv              (Persistent leaderboard database)
+--- pom.xml                            (Maven build configuration)
+--- README.md                          (Project documentation)
+--- .gitignore                         (Ignored files configuration)

Features
Gameplay
Guess the number between a given range
Attempts counter
Hint system (higher/lower)
Adaptive difficulty increases on correct guesses

Leaderboard System
Stores player name and score
CSV-based persistent storage
Sorted ranking display

Rounds System
Supports multiple rounds
Accurate tracking of wins, attempts, and difficulty progression

User Interface
Clean menu-driven CLI interface
Readable statistics and summaries

Data Persistence
Leaderboard stored in task2_leaderboard.csv
Automatic file creation if missing

How to Run (Maven)
1. Open terminal in the task directory
   cd "OIBSIP_JAVA DEVELOPMENT_TASK-2"

2. Build the project
   mvn clean install

3. Run the application
   mvn exec:java -Dexec.mainClass="com.pukazhya.oibsip.task2.Main"

Notes
Leaderboard file updates automatically after each completed round.
Target number and difficulty are regenerated after each win.
All compiled files are ignored in Git.

Technologies Used
Java SE
OOP architecture
Maven
Random number generation
CSV persistence

Developer
Pukazhya
OASIS Infobyte – Java Development Intern
GitHub: https://github.com/Pukazhya

======================================================================