================================================================
  Collaborative Plain Text Editor
  README - How to Run the Application
================================================================

OVERVIEW
--------
This is a real-time collaborative plain text editor built with:
  - Java 17
  - Spring Boot 3.2 (WebSocket server)
  - JavaFX 17 (desktop UI)
  - SQLite (local database, auto-created on first run)
  - CRDT (Conflict-free Replicated Data Type) for conflict resolution
  - Gson for JSON serialization

The application has two separate runnable components:
  1. SERVER  - DocumentServer (Spring Boot WebSocket backend)
  2. CLIENT  - EditorUI       (JavaFX desktop editor)


================================================================
PREREQUISITES
================================================================

Make sure the following are installed before proceeding:

  1. Java Development Kit (JDK) 17 or higher
     Verify: java -version

  2. Apache Maven 3.6+
     Verify: mvn -version

  3. JavaFX SDK 17 (if not already bundled by your JDK/IDE)
     Download from: https://openjfx.io/
     - Required for the client UI only
     - IntelliJ IDEA usually handles this automatically

  4. Git (to clone the repository)
     Verify: git --version


================================================================
STEP 1 - CLONE THE REPOSITORY
================================================================

  git clone https://github.com/AwabElshahawi/Collaborative_Plain_Text_Editor_APT.git
  cd Collaborative_Plain_Text_Editor_APT


================================================================
STEP 2 - BUILD THE PROJECT
================================================================

Run the following Maven command from the project root directory
(the folder that contains pom.xml):

  mvn clean install -DskipTests

This will:
  - Download all dependencies (Spring Boot, JavaFX, SQLite, Gson)
  - Compile all source files
  - Package the application


================================================================
STEP 3 - RUN THE SERVER
================================================================

The server must be started BEFORE any clients connect.

Main class: Server_DB_Init
Located at: src/main/java/Server_DB_Init.java

Option A - Using Maven:

  mvn spring-boot:run -Dspring-boot.run.mainClass=Server_DB_Init

Option B - Using an IDE (IntelliJ IDEA / Eclipse):

  1. Open the project in your IDE
  2. Navigate to src/main/java/Server_DB_Init.java
  3. Right-click the file -> Run 'Server_DB_Init.main()'

The server will start on:
  http://localhost:8080
  WebSocket endpoint: ws://localhost:8080/editor

A SQLite database file named "collaborative_editor.db" will be
automatically created in the project root directory on first run.

You should see in the console:
  "Database initialized successfully."
  "Started Server_DB_Init in X.XXX seconds"


================================================================
STEP 4 - RUN THE CLIENT (Editor UI)
================================================================

Each collaborating user runs their own instance of the client.
You can run multiple clients on the same machine or across
different machines on the same network.

Main class: ui.EditorUI
Located at: src/main/java/ui/EditorUI.java

Option A - Using an IDE:

  1. Open the project in your IDE
  2. Navigate to src/main/java/ui/EditorUI.java
  3. Right-click -> Run 'EditorUI.main()'

Option B - Command line (with JavaFX module path):

  mvn javafx:run

  Or if running the compiled JAR directly, add JavaFX VM args:

  java --module-path /path/to/javafx-sdk/lib \
       --add-modules javafx.controls,javafx.fxml \
       -cp target/collaborative-editor-1.0-SNAPSHOT.jar \
       ui.EditorUI

  (Replace /path/to/javafx-sdk with your actual JavaFX SDK path)

NOTE: JavaFX apps are best launched from an IDE due to module
      system requirements. IntelliJ IDEA handles this automatically.


================================================================
STEP 5 - USING THE EDITOR
================================================================

1. Start the server (Step 3)
2. Launch the client (Step 4)
3. The JavaFX editor window will open
4. Enter a username when prompted
5. Create a new document or join an existing one using a share code

SHARING / COLLABORATION:
  - The document owner can generate two types of share codes:
      * Editor code  - allows collaborators to edit the document
      * Viewer code  - allows collaborators to view (read-only)
  - Share the generated code with other users
  - Other users enter the code in their client to join the session

FEATURES:
  - Real-time collaborative editing using CRDT
  - Bold and italic text formatting
  - Undo / Redo support
  - Live cursor tracking for remote users
  - File open/save support
  - Viewer (read-only) and Editor (read-write) access modes


================================================================
PROJECT STRUCTURE
================================================================

  src/main/java/
  ├── Server_DB_Init.java          - Spring Boot entry point (SERVER)
  ├── ui/
  │   └── EditorUI.java            - JavaFX client UI (CLIENT)
  ├── Network/
  │   ├── DocumentServer.java      - WebSocket handler
  │   ├── ClientConnection.java    - Client WebSocket connection
  │   ├── MessageWrapper.java      - Message serialization
  │   └── WebSocketConfig.java     - WebSocket configuration
  ├── crdt/
  │   ├── BlockCRDT.java           - Block-level CRDT structure
  │   ├── CharacterCRDT.java       - Character-level CRDT
  │   ├── CollaborativeDocumentController.java
  │   ├── UndoRedoManager.java
  │   ├── Operation.java
  │   └── LocalClock.java
  └── Database/
      └── DatabaseManager.java     - SQLite database operations

  src/main/resources/
  └── application.properties       - Server config (port: 8080)

  pom.xml                          - Maven build file
  lib/                             - Local JUnit JAR (for testing)


================================================================
CONFIGURATION
================================================================

Server port can be changed in:
  src/main/resources/application.properties

  server.port=8080   <-- change this if port 8080 is in use


================================================================
TROUBLESHOOTING
================================================================

Problem: "Port 8080 already in use"
Solution: Change the port in application.properties, or stop
          the process using port 8080.
          On Linux/macOS: lsof -i :8080
          On Windows:     netstat -ano | findstr :8080

Problem: JavaFX windows don't open / "Error: JavaFX runtime
         components are missing"
Solution: Use IntelliJ IDEA which auto-configures JavaFX, or
          manually set --module-path and --add-modules VM args.

Problem: "Database connection failed"
Solution: Ensure the working directory is writable. The SQLite
          file collaborative_editor.db is created in the
          directory from which you run the server.

Problem: Client cannot connect to server
Solution: Ensure the server is running first and accessible at
          ws://localhost:8080/editor. If running on separate
          machines, replace "localhost" with the server's IP.

================================================================
  End of README
================================================================