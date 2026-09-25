# Banana Leaf Disease Detector — User Guide & Technical Instructions 📖

This guide provides comprehensive instructions for running, operating, developing, and maintaining the **Banana Leaf Disease Detector** Android application and its machine learning pipeline.

---

## 📑 Table of Contents

1. [Developer & Setup Instructions](#1-developer--setup-instructions)
   - [System Prerequisites](#system-prerequisites)
   - [Setting Up & Developing in Google Antigravity (AGY)](#setting-up--developing-in-google-antigravity-agy)
   - [Comprehensive USB Debugging Guide](#comprehensive-usb-debugging-guide)
     - [Step 1: Enabling Developer Options (Brand-Specific Steps)](#step-1-enabling-developer-options-brand-specific-steps)
     - [Step 2: Configuring USB Debugging & Brand Quirks](#step-2-configuring-usb-debugging--brand-quirks)
     - [Step 3: Cable Selection & Connecting to PC](#step-3-cable-selection--connecting-to-pc)
     - [Step 4: Authorizing the PC Connection](#step-4-authorizing-the-pc-connection)
     - [Step 5: Verifying Device via ADB in Antigravity](#step-5-verifying-device-via-adb-in-antigravity)
     - [Step 6: One-Command Build, Install & Launch via ADB](#step-6-one-command-build-install--launch-via-adb)
   - [Wireless ADB Debugging (Cable-Free Testing)](#wireless-adb-debugging-cable-free-testing)
   - [Opening & Building in Android Studio](#opening--building-in-android-studio)
   - [Running on an Android Emulator](#running-on-an-android-emulator)
   - [Rapid UI Development (Hot Reloading)](#rapid-ui-development-hot-reloading)
2. [End-User & Field Scanning Manual](#2-end-user--field-scanning-manual)
   - [App Navigation & Layout](#app-navigation--layout)
   - [How to Perform an Accurate Leaf Scan](#how-to-perform-an-accurate-leaf-scan)
   - [Understanding the 2-Stage Diagnostic Results](#understanding-the-2-stage-diagnostic-results)
   - [Text-to-Speech (TTS) Voice Briefing](#text-to-speech-tts-voice-briefing)
   - [Using LGU Connect for Agricultural Support](#using-lgu-connect-for-agricultural-support)
   - [Reviewing Scan History in the Garden Tab](#reviewing-scan-history-in-the-garden-tab)
3. [Machine Learning & Pipeline Maintenance](#3-machine-learning--pipeline-maintenance)
   - [2-Stage Detection Architecture](#2-stage-detection-architecture)
   - [Retraining the Models](#retraining-the-models)
   - [Exporting Scikit-Learn Models to Binary Format (.bin)](#exporting-scikit-learn-models-to-binary-format-bin)
   - [Deploying Updated Models to the App](#deploying-updated-models-to-the-app)
4. [Troubleshooting & Frequently Asked Questions](#4-troubleshooting--frequently-asked-questions)

---

## 1. Developer & Setup Instructions

### System Prerequisites

Before building the application, ensure your workstation has the following installed:

| Tool | Minimum Version | Recommended Version |
|---|---|---|
| **Google Antigravity IDE / Android Studio** | Latest Stable | Antigravity IDE + Android Studio Ladybug |
| **Java Development Kit (JDK)** | JDK 17 | Eclipse Temurin 17 / Bundled Android Studio JDK |
| **Android SDK Build-Tools** | 34.0.0 | 34.0.0 |
| **Android Debug Bridge (ADB)** | Included in Android SDK Platform-Tools | Added to system `PATH` |
| **Physical Phone / Emulator** | Android 7.0 (API 24) | Android 12–14 (API 31–34) |

---

### Setting Up & Developing in Google Antigravity (AGY)

Google Antigravity is an AI-first IDE that allows you to code, refactor, build, and test Android apps collaboratively with AI agents.

#### 1. Open the Project in Antigravity
1. Launch **Google Antigravity**.
2. Click **File > Open Folder...** (or press `Ctrl + O` / `Ctrl + K, Ctrl + O`).
3. Select the project directory (`bananaleafdetector-main`).
4. Antigravity will index the Kotlin source code, OpenCV wrappers, and XML resources.

#### 2. Pair-Programming & Agent Workflows
- **Chat & Edit**: Ask Antigravity to adjust OpenCV thresholds, refactor Kotlin use cases, design Material 3 layouts, or fix lint warnings.
- **Slash Commands**:
  - `/plan`: Request a step-by-step technical plan before implementing complex features.
  - `/goal`: Run long-running, autonomous refactors or multi-file changes with thorough validation.
- **Integrated Terminal**: Open the integrated terminal (`Ctrl + \`` or ``Ctrl + Shift + ` ``) to execute PowerShell, Git, Gradle, and ADB commands without switching windows.

#### 3. Building the Project from Antigravity Terminal
In the Antigravity integrated PowerShell terminal:
```powershell
# Compile debug APK
.\gradlew.bat assembleDebug
```
The output APK is generated at:
`app\build\outputs\apk\debug\app-debug.apk`

---

### Comprehensive USB Debugging Guide

Connecting a physical Android phone via USB debugging gives you direct, real-time testing of the camera sensor, OpenCV image processing, and on-device ML inference.

```
┌─────────────────┐       USB Data Cable      ┌──────────────────┐
│  Workstation /  │ ═════════════════════════ │  Android Device  │
│   Antigravity   │   ADB Daemon (Port 5037)  │ (Developer Mode) │
└─────────────────┘                           └──────────────────┘
```

#### Step 1: Enabling Developer Options (Brand-Specific Steps)

By default, Developer Options are hidden on Android. Follow the instructions for your device's brand:

- **Samsung Galaxy (One UI)**:
  1. Open **Settings > About Phone > Software Information**.
  2. Tap **Build number** rapidly **7 times**.
  3. Enter your phone's PIN/Pattern when prompted. You will see: *"Developer mode has been enabled"*.
- **Xiaomi / Redmi / POCO (HyperOS / MIUI)**:
  1. Open **Settings > About Phone**.
  2. Tap **OS version** (or **MIUI version**) rapidly **7 times**.
  3. You will see: *"You are now a developer!"*.
- **Oppo / Realme / OnePlus (ColorOS / OxygenOS)**:
  1. Open **Settings > About Device > Version**.
  2. Tap **Build number** (or **Version number**) rapidly **7 times**.
- **Google Pixel / Motorola / Stock Android**:
  1. Open **Settings > About Phone**.
  2. Scroll down and tap **Build number** rapidly **7 times**.
- **Vivo / iQOO (FuntouchOS / OriginOS)**:
  1. Open **Settings > About Phone > Software Information**.
  2. Tap **Build number** 7 times.

---

#### Step 2: Configuring USB Debugging & Brand Quirks

Now open the newly unlocked **Developer Options** menu:
- Navigate to **Settings > System > Developer Options** (on Samsung/Xiaomi/Oppo, it may appear under **Settings > Additional Settings > Developer Options**).
- Toggle **Developer Options** to **ON**.
- Scroll to the **Debugging** section and toggle **USB Debugging** to **ON**.

> [!WARNING]
> **Crucial Brand-Specific Settings (Avoid Build Failures):**
> 
> - **Xiaomi / Redmi / POCO (MIUI / HyperOS)**:
>   - Enable **"Install via USB"** (allows ADB to install debug APKs).
>   - Enable **"USB debugging (Security settings)"** (requires Mi account login and SIM card inserted). *Without this, ADB install will fail with `INSTALL_FAILED_USER_RESTRICTED`*.
> - **Oppo / Realme (ColorOS)**:
>   - Enable **"Disable Permission Monitoring"** if ADB prompts with `INSTALL_FAILED_VERIFICATION_FAILURE`.
> - **Samsung**:
>   - If installation hangs, disable **"Auto Blocker"** in *Settings > Security and Privacy > Auto Blocker*.

---

#### Step 3: Cable Selection & Connecting to PC

1. **Use a High-Quality Data Cable**:
   - Ensure your USB cable supports **data transfer**, not just charging. (If your PC makes no sound and ADB does not detect the phone, the cable is likely charge-only).
2. **USB Connection Mode**:
   - Plug the phone into a USB 3.0/2.0 port on your computer.
   - Swipe down the notification shade on your phone, tap **USB charging this device**, and switch to **File Transfer (MTP)** or **Transfer files**.

---

#### Step 4: Authorizing the PC Connection

1. Unlock your phone screen.
2. A security dialog will pop up:
   > **"Allow USB debugging?"**
   > *The computer's RSA key fingerprint is: XX:XX:XX...*
3. Check the box: ☑ **"Always allow from this computer"**.
4. Tap **Allow** or **OK**.

---

#### Step 5: Verifying Device via ADB in Antigravity

In the Google Antigravity integrated terminal, run:
```powershell
adb devices
```

**Output Scenarios:**
- ✅ **Connected & Ready**:
  ```
  List of devices attached
  RFCW10ABCDE    device
  ```
- ⚠️ **Unauthorized**:
  ```
  List of devices attached
  RFCW10ABCDE    unauthorized
  ```
  *Solution*: Unlock your phone, check your screen, and tap **Allow** on the RSA key prompt.
- ❌ **No Devices Listed**:
  ```
  List of devices attached
  ```
  *Solution*: Try a different USB port, reconnect the cable, or restart the ADB server:
  ```powershell
  adb kill-server
  adb start-server
  ```

---

#### Step 6: One-Command Build, Install & Launch via ADB

Once your device shows as `device`, run this single command in Antigravity to build, push to your phone, and launch the app immediately:

```powershell
.\gradlew.bat installDebug; adb shell am start -n com.thesis.bananaleaf/.SplashActivity
```

The app will compile, install directly over USB, and immediately open to the camera scanner on your phone!

---

### Wireless ADB Debugging (Cable-Free Testing)

Once you've connected via USB once, you can untether your phone and debug over your local Wi-Fi network:

1. Ensure your computer and phone are connected to the **same Wi-Fi network**.
2. With the phone still plugged into USB, set ADB to TCP/IP mode:
   ```powershell
   adb tcpip 5555
   ```
3. Find your phone's local IP address:
   - On your phone: **Settings > Wi-Fi > [Your Wi-Fi Name] > IP Address** (e.g., `192.168.1.45`).
4. Connect wirelessly:
   ```powershell
   adb connect 192.168.1.45:5555
   ```
5. **Unplug the USB cable!**
6. Verify with `adb devices` — you will see `192.168.1.45:5555 device`.
7. You can now run `.\gradlew installDebug` or `.\scripts\hot_reload.ps1` completely over the air.

---

### Opening & Building in Android Studio

If you prefer using the full Android Studio IDE alongside Antigravity:

1. Open Android Studio.
2. Select **File > Open...** and select `bananaleafdetector-main`.
3. Wait for Gradle sync to finish.
4. Select your USB or Wireless connected device from the top device dropdown.
5. Click **Run 'app'** (`Shift + F10`).

---

### Running on an Android Emulator

If testing without a physical phone:
1. Open **Tools > Device Manager** in Android Studio.
2. Create a Virtual Device (e.g., **Pixel 7**, API Level 34).
3. In Virtual Device Settings, set **Camera Back** to:
   - **Webcam0**: to stream your computer webcam directly as the leaf camera, OR
   - **VirtualScene**: to simulate a 3D environment where you can move toward photos of leaves.
4. Launch the emulator and run the app (`Shift + F10`).

---

### Rapid UI Development (Hot Reloading)

For rapid layout and UI tweaking without manual reinstallation:
1. Ensure your device is connected with `adb devices`.
2. Open PowerShell in Antigravity and run:
   ```powershell
   .\scripts\hot_reload.ps1
   ```
3. Any changes saved to XML layout or drawable files in `app/src/main/res/` will automatically recompile and restart the app on your connected device in ~2 seconds.

---

## 2. End-User & Field Scanning Manual

### App Navigation & Layout

The app is organized into four main tabs accessible via the bottom navigation bar:

```
┌────────────────────────────────────────────────────────┐
│  [🌿 Banana Leaf Disease Detector]          [⚡ Flash] │  <-- Top Bar
├────────────────────────────────────────────────────────┤
│                                                        │
│                    CAMERA PREVIEW                      │
│                                                        │
│             ┌────────────────────────┐                 │
│             │  [+] Reticle Guide [+] │                 │  <-- Viewfinder
│             └────────────────────────┘                 │
│                                                        │
│               (●) Banana Leaf Detected                 │  <-- Status Pill
│                                                        │
│                         [ 🔘 ]                         │  <-- Shutter
├────────────────────────────────────────────────────────┤
│   [🏠 Home]    [🔍 Discover]    [🌱 Garden]    [📞 LGU] │  <-- Bottom Bar
└────────────────────────────────────────────────────────┘
```

1. **🏠 Home (Scanner)**: Live camera viewfinder for detecting and diagnosing banana leaves.
2. **🔍 Discover (Encyclopedia)**: Detailed guides on Sigatoka, Cordana, and Pestalotiopsis symptoms, causes, management practices, and prevention.
3. **🌱 Garden (History)**: Chronological catalog of all past scans, diagnoses, severity ratings, and dates.
4. **📞 LGU Connect**: Directory with one-tap Call, SMS, and Email shortcuts to Municipal Agriculture Offices (MAO).

---

### How to Perform an Accurate Leaf Scan

To achieve maximum diagnostic accuracy in the field:

1. **Focus on the Leaf Blade**:
   - Point your camera directly at the banana leaf surface.
   - Keep the leaf centered inside the on-screen rectangular reticle.
2. **Distance**:
   - Hold the camera **30 cm to 60 cm (1 to 2 feet)** away from the leaf surface.
   - The leaf blade should fill at least **40% to 70%** of the frame.
3. **Lighting & Glare**:
   - Avoid direct harsh sunlight reflecting off waxy leaf surfaces.
   - In dim conditions or beneath dense banana canopies, tap the **Flash / Torch button** in the top-right corner.
   - Avoid pointing at uniform non-foliage surfaces (e.g., walls, concrete, clothing) with flash, as built-in botanical guards will reject them.
4. **Observe the Dynamic Status Pill**:
   - **Searching for Banana Leaf...** (Yellow/Gray): The camera is scanning; move closer or adjust angle.
   - **Banana Leaf Detected** (Bright Green): Leaf botanical validation passed; the shutter button is now active.
   - **Adjust Framing** (Amber): The leaf is partially visible or at an extreme angle.
5. **Capture**:
   - Tap the central **Shutter Button**. The app will capture the frame, execute the feature extraction pipeline, and open the diagnosis sheet.

---

### Understanding the 2-Stage Diagnostic Results

The diagnostic bottom sheet provides actionable agronomic information:

| Result Header | What It Means | Recommended Action |
|---|---|---|
| **Healthy Banana Leaf** | No significant fungal lesions detected. Natural chlorophyll pigmentation and vein patterns observed. | Maintain good field sanitation and balanced potassium fertilization. |
| **Black / Yellow Sigatoka Detected** | Elongated brown/black necrotic streaks or yellow spots detected (*Pseudocercospora fijiensis* / *musae*). | Deleaf severely affected foliage, prune suckers to improve airflow, apply systemic or contact fungicides during rainy periods. |
| **Cordana Leaf Spot Detected** | Oval to diamond-shaped necrotic lesions with pale gray centers and bright yellow halos (*Cordana musae*). | Improve field drainage, remove senescent leaves, avoid overhead sprinkler splash. |
| **Pestalotiopsis Spot Detected** | Irregular dark brown spots merging into dry, brittle patches (*Pestalotiopsis palmarum*). | Manage insect vectors, optimize crop nutrition, apply protective copper-based sprays if widespread. |
| **No Banana Leaf Detected** | The scanned object does not meet the botanical color, texture, or foliage criteria. | Re-aim at an actual banana leaf blade with good lighting. |

Each diagnosis includes:
- **Confidence Rating**: Probability percentage from the ensemble random forest models.
- **Disease Description**: Agronomic summary of the fungal pathogen.
- **Immediate Treatment**: Steps for immediate containment.
- **Long-Term Prevention**: Cultural practices to reduce recurrence.

---

### Text-to-Speech (TTS) Voice Briefing

- **Automatic Spoken Summary**: When a diagnosis completes, the app speaks a concise summary of the disease finding and primary recommendation.
- **Language Toggle**: In the result card, tap the speaker icon to replay the audio briefing in English or Tagalog/Filipino.
- **Audio Control**: You can mute or stop voice playback anytime by tapping the speaker button.

---

### Using LGU Connect for Agricultural Support

When a disease is detected, local government assistance is one tap away:
1. Tap the **Contact Agricultural Officer** button directly on the diagnosis sheet (or navigate to the **LGU Connect** tab).
2. Choose your preferred contact method:
   - 📞 **Call**: Dials the Municipal Agriculture Office helpline directly.
   - 💬 **SMS / Text**: Opens your SMS app with an auto-drafted message containing the detected disease name and timestamp.
   - 📧 **Email**: Opens your email client with diagnostic findings pre-filled, ready to attach the scan image for expert verification.

---

### Reviewing Scan History in the Garden Tab

- Every scan is automatically saved to the local offline SQLite/Room database.
- Navigate to **🌱 Garden** to review past scans.
- Tap any historical record to reopen the detailed diagnostic report, treatment recommendations, and confidence metrics.
- Swipe or tap the delete icon to remove outdated records.

---

## 3. Machine Learning & Pipeline Maintenance

### 2-Stage Detection Architecture

The application uses an edge-computed, two-stage cascade classifier:

```
[ Input Frame: 200x200 BGR ]
               │
               ▼
[ Preprocessing & Segmentation ]
 ├── K-Means Foliage Segmentation (HSV Saturation + Otsu threshold fallback)
 └── Morphological Open/Close filtering
               │
               ▼
[ Stage 1: Leaf Presence Gate ]
 ├── Extract 10 Botanical Features:
 │   (veg_ratio, circularity, largest_blob_ratio, aspect, extent,
 │    hue_mean, hue_std, sat_mean, val_mean, edge_density)
 ├── Normalize with Stage 1 Scaler parameters
 └── Evaluate `leaf_detector.bin` (200 Trees)
               │
      [ Leaf Gate > 0.62? ]
         ├── NO  ──> Return "No Banana Leaf Detected"
         └── YES ──> Proceed to Stage 2
               │
               ▼
[ Stage 2: Multi-Head Disease Classifiers ]
 ├── Extract 15 Lesion Features:
 │   (H/S/V mean, std, skewness [9 features] + 6 GLCM texture features)
 ├── Scale features with Stage 2 Scaler
 └── Evaluate in parallel:
     ├── `sigatoka.bin`        (200 Trees) ──> Threshold: 0.38
     ├── `cordana.bin`         (400 Trees) ──> Threshold: 0.38
     └── `pestalotiopsis.bin`  (200 Trees) ──> Threshold: 0.38
```

---

### Retraining the Models

If you gather new field imagery or augment the training dataset:

1. **Dataset Directory Structure**:
   Organize your training images into the following folder structure:
   ```
   dataset/
   ├── Cordana/
   ├── Healthy/
   ├── Pestalotiopsis/
   └── Sigatoka/
   ```

2. **Using the Training Script**:
   Run the automated training pipeline in Python:
   ```bash
   python scripts/train_pipeline.py --data_dir path/to/dataset --output_dir output_models/
   ```

3. **Or using Jupyter Notebook**:
   - Open `training-notebooks/BananaLeafModel_Final.ipynb` in Jupyter Lab or Google Colab.
   - Run all cells sequentially.
   - The notebook uses `StratifiedGroupKFold` cross-validation to prevent augmented image leakage.

---

### Exporting Scikit-Learn Models to Binary Format (.bin)

The Android app does not use heavyweight runtimes like TensorFlow Lite or ONNX. Instead, it uses a custom, lightweight binary format (`.bin`) that serializes Scikit-Learn Decision Trees into raw bytes.

To convert trained Scikit-Learn `.pkl` models to `.bin`:
```bash
python training-notebooks/convert_pkl_to_bin.py
```

The script outputs four binary model files:
- `leaf_detector.bin` (Stage 1 presence gate)
- `cordana.bin` (Stage 2 Cordana detector)
- `sigatoka.bin` (Stage 2 Sigatoka detector)
- `pestalotiopsis.bin` (Stage 2 Pestalotiopsis detector)

---

### Deploying Updated Models to the App

1. Copy the exported `.bin` files into the Android assets directory:
   ```
   app/src/main/assets/leaf_detector.bin
   app/src/main/assets/cordana.bin
   app/src/main/assets/sigatoka.bin
   app/src/main/assets/pestalotiopsis.bin
   ```
2. If feature normalization parameters changed, update the mean and scale values in:
   [FeatureScaler.kt](file:///c:/Users/User/Downloads/bananaleafdetector-main/app/src/main/java/com/thesis/bananaleaf/FeatureScaler.kt)
3. Rebuild the app:
   ```powershell
   .\gradlew assembleDebug
   ```

---

## 4. Troubleshooting & Frequently Asked Questions

### Q1: The camera preview is black or shows a permission error.
- **Solution**: Open **Settings > Apps > Banana Leaf Detector > Permissions > Camera** and set to **Allow while using the app**. If on an emulator, verify that **Camera Back** is configured to **Webcam0** or **VirtualScene**.

### Q2: Why does the app say "No Banana Leaf Detected" on a real leaf?
- **Causes**:
  1. *Leaf too far away*: Move closer so the leaf fills the reticle box.
  2. *Extreme shadow or backlighting*: Ensure the leaf is evenly illuminated.
  3. *Dirty lens or motion blur*: Hold the phone steady for 1 second until the status pill turns green.

### Q3: Gradle build fails with "Java heap space" or "Out of memory".
- **Solution**: Open `gradle.properties` in the project root and add or increase:
  ```properties
  org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m
  ```

### Q4: Can the app work completely without internet?
- **Yes!** All image processing (OpenCV), model inferences (Random Forest binaries), and database storage (SQLite) operate **100% on-device**. Internet connectivity is only needed if you choose to email or browse external resources.

---

*For further inquiries, bug reports, or contributions, please open an issue in the [GitHub Repository](https://github.com/GregorMondragon/bananaleafdetector).*
