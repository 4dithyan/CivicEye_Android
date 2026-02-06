# CivicEye Project Documentation

## 1. System Overview
CivicEye is a comprehensive civic issue reporting platform designed to bridge the gap between citizens and municipal authorities.
- **Android App**: Used by citizens to report issues (potholes, garbage, etc.) and by staff to view and resolve assignments.
- **Web Admin Panel**: Used by administrators to manage users, view real-time statistics, and oversee issue resolution.
- **Backend**: Google Firebase (Firestore, Auth, Storage) handles real-time data synchronization.

## 2. Technology Stack

### Mobile Application (Android)
- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **UI Framework**: XML Layouts with Material Design components
- **Dependency Injection**: Hilt
- **Asynchronous Programming**: Coroutines & Flow
- **Image Loading**: Coil
- **Maps**: Google Maps SDK for Android
- **Networking/Data**: Firebase Android SDK

### Web Admin Panel
- **Core**: HTML5, CSS3, JavaScript (ES6+)
- **Styling**: Custom CSS (Responsive Grid/Flexbox)
- **Charts**: Chart.js (Data visualization)
- **Maps**: Google Maps JavaScript API
- **Data/Auth**: Firebase Web SDK (v9 compat)

### Backend & Infrastructure
- **Database**: Cloud Firestore (NoSQL, Real-time)
- **Authentication**: Firebase Auth (Email/Password, Custom Claims)
- **Storage**: Firebase Storage (Image assets)
- **AI Integration**: Google Gemini API (Content validation & analysis)
- **Hosting**: Firebase Hosting (implied for Web Admin)

---

## 3. Data Flow Diagrams (DFD)

### DFD Level 0 (Context Diagram)
This high-level view shows the interaction between external entities (Users) and the CivicEye System.

```mermaid
graph LR
    Citizen[Citizen] -- 1. Reports Issue w/ Photo & Loc --> System((CivicEye System))
    System -- 2. Notification / Status Update --> Citizen
    
    Staff[Field Staff] -- 3. View Assigned Tasks --> System
    Staff -- 4. Upload Proof of Work --> System
    System -- 5. Task Alert --> Staff
    
    Admin[Admin Authority] -- 6. User & Staff Management --> System
    System -- 7. Real-Time Dashboard Stats --> Admin
```

### DFD Level 1 (Process Breakdown)
Breakdown of the main functional modules: Authentication, Issue Management, and Administration.

```mermaid
graph TD
    %% Entities
    Cit[Citizen]
    St[Staff]
    Adm[Admin]
    
    %% Processes
    P1(1.0 Authentication)
    P2(2.0 Issue Reporting)
    P3(3.0 Issue Resolution)
    P4(4.0 Admin Management)
    
    %% Data Stores
    DB_U[(Users DB)]
    DB_I[(Issues DB)]
    DB_D[(Departments DB)]
    
    %% Flows
    Cit -->|Reg/Login| P1
    St -->|Login| P1
    Adm -->|Login| P1
    P1 <--> DB_U
    
    Cit -->|Submit Issue| P2
    P2 -->|Store Issue| DB_I
    P2 -->|Auto-Assign Dept| DB_D
    
    St -->|Fetch Tasks| P3
    DB_I -->|Issue Data| P3
    P3 -->|Update Status| DB_I
    
    Adm -->|Monitor & Ban| P4
    P4 <--> DB_U
    P4 <--> DB_I
```

### DFD Level 2 (Detailed Issue Flow)
Detailed expansion of the **2.0 Issue Reporting** and **3.0 Resolution** processes.

```mermaid
graph TD
    start((Start)) --> P2_1[2.1 Capture Image & Location]
    P2_1 --> P2_2{2.2 AI Validation}
    
    P2_2 -- Valid --> P2_3[2.3 Create Issue Record]
    P2_2 -- Invalid --> P2_0[Reject / Ask Retry]
    
    P2_3 --> DB_I[(Issues Collection)]
    
    DB_I --> P3_1[3.1 Notify Department/Staff]
    P3_1 --> P3_2[3.2 Staff Accepts Task]
    P3_2 --> P3_3[3.3 Work in Progress]
    P3_3 --> P3_4[3.4 Upload Proof Image]
    P3_4 --> P3_5[3.5 Mark Resolved]
    
    P3_5 --> DB_I
    DB_I --> P3_6[3.6 Notify Citizen]
    P3_6 --> endNode((End))
    
    style P2_2 fill:#f9f,stroke:#333
```

---

## 3. Entity Relationship Diagram (ERD)
The database schema designed in Firestore.

```mermaid
erDiagram
    USERS ||--o{ ISSUES : "reports (Civilian)"
    USERS ||--o{ ISSUES : "resolves (Staff)"
    DEPARTMENTS ||--|{ USERS : "has staff"
    DEPARTMENTS ||--o{ ISSUES : "responsible for"
    LOCATIONS ||--o{ ISSUES : "contains"

    USERS {
        string uid PK "Firebase Auth ID"
        string role "civilian | staff | admin"
        string name
        string email
        string phone
        string departmentId FK "For Staff"
        boolean isBlocked
        int reputationScore
    }

    ISSUES {
        string id PK
        string title
        string description
        string category "Road | Garbage | Water..."
        geopoint location "Lat/Lng"
        string address "Geocoded address"
        string status "pending | in_progress | resolved"
        string reporterId FK
        string assignedTo FK
        string departmentId FK
        string[] imageUrls
        string[] proofImages
        timestamp createdAt
        boolean aiValidated
        float aiConfidence
    }

    DEPARTMENTS {
        string id PK
        string name "Sanitation | Infrastructure..."
        string headEmail
    }

    LOCATIONS {
        string id PK
        string name
        geopoint center
        float radius
    }
```
