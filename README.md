# Sciverse Summit

Sciverse Summit is a conference management app built with **Spring Boot** and **Thymeleaf** — sessions, delegates, motions, speakers, timers, notes, all in one modern, glassy interface, built for the ambitious project knows as **"SciVerse Summit"**. And as of **v3.0.3**, it comes as proper **Windows installer apps** (Chair + Delegate) with **built-in auto-updates**. No Java to install, no zips to extract, no folders to babysit. 🥳

> ⬇️ **Get the apps:** go to [**Releases**](https://github.com/MystiTheDev/sciverse-summit/releases/latest) and grab your installer:
> - 🪑 **Chairs download:** `SciVerse-Summit-Chair-Setup-3.0.0.exe` — hosts the session server on your PC
> - 🎤 **Delegates download:** `SciVerse-Summit-Delegate-Setup-3.0.0.exe` — connects to the chair's session
>
> (The other files in the release — `.yml`, `.blockmap` — are for auto-update. You can ignore them.)

## 🖥️ Desktop Apps

[](#-desktop-apps)

Two apps, two jobs:

### 🪑 Chair — hosts the session

-   **Start Session Server:** one click spins up the bundled server (Java runtime included — you don't need Java installed, I checked twice).
-   **Delegate Join URL:** one click copies the address your delegates type in. Shout it across the room, done.
-   **Your data survives reinstalls:** the database lives in your user folder, so updating never wipes anything.
-   **Fancy bits:** animated loading splash on launch, live server log in the console, floating "Back to Console" button while you're in the web UI.

### 🎤 Delegate — joins the session

-   **Type the chair's address, hit Connect.** That's the whole setup. It waits patiently (and retries forever) until the chair starts the server, then walks you straight in.
-   **Custom sign-in screen** with the same login under the hood — sessions and redirects behave exactly like the website.
-   **Lightweight:** no server, no Java inside. Just the connector.

## 🔄 Auto-Update (yes, really)

[](#-auto-update)

Both apps check for updates on every launch. When one is found, you get a nice in-app card — **not** a cryptic system popup:

-   **Update available?** It *asks* first: Download Update / Later. Nothing downloads behind your back.
-   **Downloading?** Progress bar + live speed, floating icons, and rotating tips to keep you entertained.
-   **Ready?** Hit **Restart to Update** and you're on the new version in seconds. Or hit Later and it installs next time you quit.

## 🚀 Features

[](#-features)

### 🔹 Session Management

-   **Create Sessions:** with Name, Committee, Topic, and Participants.
-   **Active Session Tracking:** detects and resumes the currently active session.
-   **Session History:** archive of past sessions with timestamps and metadata.
-   **End Session:** securely close sessions to archive them.

### 🔹 Note-Taking System

-   **Integrated Notes:** modal-based, accessible from the sidebar.
-   **Auto-Save & Persistence:** notes survive logins.
-   **Rich UI:** clean, distraction-free writing environment.

### 🔹 Dashboard & Navigation

-   **Central Dashboard:** quick access to everything.
-   **Smart Sidebar:** session tools (like Notes) appear only when a session is active.
-   **Glassmorphism Design:** translucent modals and headers, premium look and feel.

### 🔹 🌗 Theme Support (Light / Dark Mode)

-   **Persistent Toggle:** saved in `localStorage`, applied before render — no white flash, ever.
-   **Fully Themed Pages:** Dashboard, History, Add/Edit Delegate, Notes, Login.
-   **Smooth Transitions:** everything animates cleanly on toggle.

### 🔹 Security & User Management

-   **Authentication:** Spring Security login/logout with role-based landing (Admin → setup, Chair → dashboard, Delegate → portal).
-   **Logout Confirmation:** no more accidental logouts.
-   **User-Scoped Operations:** you can only delete your own sessions (enforced server-side, not just hidden buttons).

## 🛠️ Technology Stack

[](#️-technology-stack)

### Backend

-   **Java 17** + **Spring Boot 3.5.x**: app framework, JPA, Security.
-   **H2 Database**: file-based persistence (`./data/presentationdb` standalone, per-user folder in the Chair app).

### Frontend (web)

-   **Thymeleaf** + **Bootstrap 5.3.3** + **Bootstrap Icons**, custom Glassmorphism CSS.

### Desktop shell (new in v3.0.0!)

-   **Electron 38**: Chair launcher + Delegate connector, NSIS per-user installers.
-   **Bundled mini Java runtime** (jlink) inside the Chair installer — no system Java needed.
-   **electron-updater** via GitHub Releases (separate `chair` / `delegate` channels, ask-first flow).

## 🏗️ Architecture

[](#️-architecture)

Standard **MVC**, plus a desktop shell that never touches web code:

1.  **Controller Layer** — HTTP requests, navigation, view models.
2.  **Service Layer** — business logic and transactions.
3.  **Repository Layer** — Spring Data JPA, CRUD.
4.  **Entity Layer** — `Session`, `User`, etc.
5.  **View Layer** — Thymeleaf templates + fragments.
6.  **Desktop shell** (`summit-desktop/`) — Electron launchers that *spawn* the jar (Chair) or *connect* to it (Delegate). Zero shared code with the web app.

## ⚙️ Setup & Installation

[](#️-setup--installation)

### Option A — installers (Recommended)

1.  Go to [**Releases**](https://github.com/MystiTheDev/sciverse-summit/releases/latest), download your `.exe` (Chair or Delegate).
2.  Run it. If Windows SmartScreen complains (unsigned build), More info → Run anyway.
3.  Chair: **Start Session Server**, share the join URL. Delegates: type it in, Connect. Done.
4.  Both PCs just need to be on the same network. Internet not required after install.

### Option B — from source (Developers)

1.  **Prerequisites:** Java 17+, Maven, Node 18+.
2.  **Web app:** `mvn package -DskipTests` → `target/summit-0.0.1-SNAPSHOT.jar`, run with `java -jar`, open `http://localhost:8080`.
3.  **Desktop:** copy the jar to `summit-desktop/chair/server/summit.jar`, build the mini-runtime with `jlink` (see `summit-desktop/README.md`), then `npm run dist` in `chair/` / `delegate/`.

## 📂 Project Structure

[](#-project-structure)

```
sciverse-summit/
├── src/main/java/.../summit/
│   ├── controller/   # Web Controllers
│   ├── entity/       # JPA Entities
│   ├── repository/   # Data Access Interfaces
│   ├── service/      # Business Logic
│   └── config/       # Security & friends
├── src/main/resources/
│   ├── templates/    # Thymeleaf Views
│   └── application.properties
├── summit-desktop/   # Electron shells (separate, no shared code)
│   ├── chair/        # Launcher + bundled server + mini JRE
│   └── delegate/     # Connector + custom login overlay
├── pom.xml
└── README.md         # You are here. Hi. 👋
```

## 🎨 UI Highlights

[](#-ui-highlights)

-   **Liquid Glass UI** inspired by Apple's design language.
-   **Animated launch splashes** in both desktop apps (the Chair one has a *very* cool loader, not gonna lie 😎).
-   **In-app update screen** with floating icons and rotating tips. Updates finally feel like a feature, not a chore.
-   **Responsive Sidebar**, collapsible, context-aware.
-   **Interactive Modals** for Notes and Logout — no page reloads, no lost context.

---

*Developed By MystiTheDev* — built with too much coffee and an unreasonable love for changelogs ☕
