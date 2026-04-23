# CivicEye 🏙️
> **Report. Support. Resolve.**

CivicEye is a comprehensive platform empowering citizens to report civic issues directly to local authorities. It consists of an **Android Application** for citizens to seamlessly log issues and a **Web-based Admin Dashboard** for authorities to manage, track, and resolve reports in real-time.

---

## 🛠️ Technical Stack

### **Android Application (Citizen App)**
* **Language:** Kotlin
* **Architecture:** MVVM with Navigation Component
* **Dependency Injection:** Hilt (Dagger)
* **Asynchronous Programming:** Kotlin Coroutines
* **Image Loading:** Coil
* **Backend & Authentication:** Firebase (Auth, Firestore, Storage)
* **UI/UX:** Material Design 3, Lottie Animations, Shimmer Loading
* **Image Handling:** Cloudinary via OkHttp, ImagePicker

### **Web Application (Admin/Staff Panel)**
* **Frontend:** HTML5, CSS3, Vanilla JavaScript
* **Charting & Analytics:** Chart.js
* **Backend & Authentication:** Firebase (Auth, Firestore Realtime Database)
* **Styling:** Custom CSS variables with a modern, responsive design system

---

## 🚀 How to Install and Set Up

### **1. Web Admin Panel Setup**
1. Navigate to the `web` directory in the project folder.
2. Open `js/firebase-config.js` and add your Firebase project configuration object.
3. Host the `web` directory using any local server (e.g., VS Code Live Server, Python HTTP server, or Firebase Hosting).
4. Open `index.html` in your browser.
5. *Note: Only users with the `admin` or `staff` role assigned in Firestore can log into the web dashboard.*

### **2. Android Application Setup**
1. Ensure you have **Android Studio** installed (Flamingo or later recommended).
2. Open the `android` folder in Android Studio.
3. Connect the app to your Firebase project by placing your `google-services.json` file inside the `android/app/` directory.
4. Sync the project with Gradle files.
5. Build and Run the app on an Android Emulator or a physical device running Android 7.0 (API 24) or higher.

---

## 📖 How to Use the System (Step-by-Step)

### **For Citizens (Android App)**
1. **Registration/Login:** Open the app and sign up using your email and password.
2. **Report an Issue:** Tap the floating action button to create a new report. Capture a photo of the issue using the camera or gallery (handled by ImagePicker).
3. **Issue Details:** Provide a title, description, and categorize the issue (e.g., Road, Water, Electricity). The app can also automatically tag your location.
4. **Track Status:** View your submitted reports on the home screen. You can track their status in real-time as they change from "Pending" to "In Progress" and finally "Resolved".

### **For Admins & Staff (Web Dashboard)**
1. **Login:** Access the web dashboard and log in using your admin/staff credentials.
2. **Dashboard Overview:** View real-time statistics including Total Issues, Resolved Issues, Active Pending issues, and Registered Citizens. Check the visual charts for status distribution and top department categories.
3. **Manage Issues:** Navigate to the issues section to view reports submitted by citizens.
4. **Update Status:** Review the details and images of an issue, and change its status (e.g., to "In Progress", "Resolved", or "Rejected"). The citizen will see the updated status instantly on their mobile app.

---

## 📞 Contact & Credits

<div align="center">
  <h3>❤️ Made with love by Adithyan</h3>
  
  <p>If you have any questions, feedback, or would like to collaborate, feel free to reach out:</p>
  
  <p>
    📧 <b>Email:</b> <a href="mailto:mailforadithyan@gmail.com">mailforadithyan@gmail.com</a><br>
    🌐 <b>Portfolio:</b> <a href="https://adithyan-portfolio.pages.dev/">https://adithyan-portfolio.pages.dev/</a>
  </p>
</div>
