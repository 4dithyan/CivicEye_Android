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

##### 4. Working of the Project

The CivicEye system operates through three primary interfaces, each serving a distinct user role in the issue resolution lifecycle.

1.  **Issue Reporting (Citizen)**:
    *   A citizen logs into the Android app and clicks the "+" button.
    *   They capture a photo of the civic issue (e.g., a pothole) and the app automatically retrieves the GPS location.
    *   Google Gemini AI analyzes the image to verify it matches a valid category (e.g., ensuring it's a road issue, not a selfie) and assigns a relevance score.
    *   If valid, the issue is stored in Firestore, and the appropriate government department is notified.

2.  **Task Assignment & Resolution (Staff)**:
    *   Field staff receive real-time notifications for issues in their department.
    *   They view the location on a map and navigate to the site.
    *   After fixing the issue, they upload a "Proof of Work" photo.
    *   They mark the task as "Resolved," which updates the status across the entire system.

3.  **Management & Oversight (Admin)**:
    *   Administrators view a live dashboard showing pending, in-progress, and resolved issues.
    *   They can re-assign tasks, manage staff availability, or block users who submit spam.
    *   Changes made by admins reflect instantly on staff and citizen apps.

---

### DFD Level 2 (Detailed Issue Flow)
Detailed expansion of the Issue Reporting and Resolution processes, showing interactions between Civilian, Staff, and Admin.

```mermaid
graph TD
    %% Lanes / Swimlanes
    subgraph Civilian_Lane [Citizen]
        Start((Start))
        C1[1. Capture Image & Loc]
        C2[Receive Notification]
        End((End))
    end

    subgraph System_Lane [CivicEye System]
        S1{AI Validation}
        S2[Create Issue Record]
        S3[Update Status: Resolved]
        DB[(Firestore DB)]
    end

    subgraph Staff_Lane [Field Staff]
        St1[2. View Assigned Task]
        St2[3. Travel to Site]
        St3[4. Upload Proof]
        St4[5. Mark Complete]
    end

    subgraph Admin_Lane [Admin]
        A1[Monitor Dashboard]
        A2[Override/Reassign]
    end

    %% Flow
    Start --> C1
    C1 --> S1
    
    S1 -- Valid --> S2
    S1 -- Invalid --> C2
    S2 --> DB
    
    DB --> St1
    St1 --> St2
    St2 --> St3
    St3 --> St4
    St4 --> S3
    S3 --> DB
    
    DB --> A1
    A1 -.-> A2
    A2 -.-> DB
    
    DB -- Status Change --> C2
    C2 --> End

    style S1 fill:#f9f,stroke:#333
    style DB fill:#ff9,stroke:#333
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
