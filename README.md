# Sciverse Summit

Sciverse Summit is a comprehensive conference management application built with **Spring Boot** and **Thymeleaf**. It is designed to streamline the management of conference sessions, delegates, motions, and speakers, providing a modern and intuitive user interface.

## Tasks
- [X] Finish the glassmorphic design
- [X] Clean up the code

## 🚀 Features

### 🔹 Session Management
- **Create Sessions:** Start new conference sessions with details like Name, Committee, Topic, and Participants.
- **Active Session Tracking:** Automatically detects and allows users to resume the currently active session.
- **Session History:** View a detailed archive of all past sessions with timestamps and metadata.
- **End Session:** Securely close sessions to archive them.

### 🔹 Note-Taking System
- **Integrated Notes:** A professional, modal-based note-taking interface accessible from the sidebar.
- **Auto-Save & Persistence:** Notes are saved to the database and persist across logins.
- **Rich UI:** Features a clean, distraction-free writing environment.

### 🔹 Dashboard & Navigation
- **Central Dashboard:** Quick access to all core functionalities.
- **Smart Sidebar:** Context-aware sidebar that shows session-specific tools (like Notes) only when a session is active.
- **Glassmorphism Design:** Modern, translucent modals and header for a premium look and feel.
- **Committee Options:** Curated list of committees (Mathematics removed to keep scope strictly scientific).

### 🔹 🌗 Theme Support (Light / Dark Mode)
- **Persistent Toggle:** Theme preference saved in `localStorage` and applied across all pages without flash.
- **Pre-load Script:** Each page applies the saved theme before render to prevent the white-flash effect.
- **Fully Themed Pages:**
  - Dashboard
  - History (modernized table with white heading in light mode, black heading in dark mode)
  - Add / Edit Delegate
  - Notes
  - Login
- **Smooth Transitions:** All color changes animate cleanly when the user toggles themes.

### 🔹 Modern History Table
- **Gradient Header:** White background in light mode / black in dark mode for crisp contrast.
- **Status Badges:** Active vs. Ended sessions clearly distinguished with colored pill badges.
- **Action Column:** Per-row delete button alongside view/resume actions.
- **Responsive:** Works on desktop and tablet widths.

### 🔹 Security & User Management
- **Authentication:** Secure Login/Logout functionality using Spring Security.
- **Logout Confirmation:** Prevents accidental logouts with a confirmation dialog.
- **User Context:** Displays current user information and profile options.
- **User-Scoped Operations:** Session deletion is restricted to the owning user (server-side ownership check).
## 🛠️ Technology Stack

### Backend
- **Java 17**: Core programming language.
- **Spring Boot 3.5.7**: Application framework.
- **Spring Data JPA**: For database interactions.
- **Spring Security**: For authentication and authorization.
- **H2 Database**: File-based persistence (stored in `./data/presentationdb`).

### Frontend
- **Thymeleaf**: Server-side Java template engine.
- **Bootstrap 5.3.3**: Responsive CSS framework.
- **Bootstrap Icons**: Comprehensive icon library.
- **HTML5 / CSS3**: Custom styling including Glassmorphism effects.

## 🏗️ Architecture

The project follows a standard **Model-View-Controller (MVC)** architecture:

1.  **Controller Layer** (`com.ishan.sciverse.summit.controller`):
    - Handles incoming HTTP requests.
    - Manages navigation logic (e.g., `DashboardController`).
    - Prepares data models for views.

2.  **Service Layer** (`com.ishan.sciverse.summit.service`):
    - Contains business logic.
    - Manages transactions (e.g., `SessionService`).
    - Bridges Controllers and Repositories.

3.  **Repository Layer** (`com.ishan.sciverse.summit.repository`):
    - Interfaces with the database using Spring Data JPA.
    - Performs CRUD operations.

4.  **Entity Layer** (`com.ishan.sciverse.summit.entity`):
    - Defines the data models (e.g., `Session`, `User`) mapped to database tables.

5.  **View Layer** (`src/main/resources/templates`):
    - **Fragments:** Reusable UI components (Header, Sidebar, Modals).
    - **Pages:** `dashboard.html`, `history.html`, `index.html`.

## ⚙️ Setup & Installation

1.  **Prerequisites:**
    - Java 17 or higher installed.
    - Maven installed.

2.  **Clone the Repository:**
    ```bash
    git clone <repository-url>
    cd summit
    ```

3.  **Build the Project:**
    ```bash
    mvn clean install
    ```

4.  **Run the Application:**
    ```bash
    mvn spring-boot:run
    ```

5.  **Access the App:**
    - Open your browser and go to: `http://localhost:8080`
    - **H2 Console:** `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/presentationdb`)

## 📂 Project Structure

```
summit/
├── src/
│   ├── main/
│   │   ├── java/com/ishan/sciverse/summit/
│   │   │   ├── controller/   # Web Controllers
│   │   │   ├── entity/       # JPA Entities
│   │   │   ├── repository/   # Data Access Interfaces
│   │   │   ├── service/      # Business Logic
│   │   │   └── SummitApplication.java
│   │   └── resources/
│   │       ├── static/       # CSS, JS, Images
│   │       ├── templates/    # Thymeleaf Views
│   │       └── application.properties
│   └── test/                 # Unit Tests
├── pom.xml                   # Maven Dependencies
└── README.md                 # Project Documentation
```

## 🎨 UI Highlights

- **Glass Header:** A custom CSS implementation using `backdrop-filter` for a frosted glass effect.
- **Responsive Sidebar:** Collapsible sidebar for efficient navigation.
- **Interactive Modals:** Used for Notes and Logout confirmation to maintain context without page reloads.

---
*Developed By MystiTheDev*
