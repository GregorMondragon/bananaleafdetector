# Banana Leaf Disease Detector 🍌🍃

An Android mobile application for real-time detection and multi-label classification of banana leaf diseases using edge computer vision and on-device machine learning models.

> 📘 **Documentation & Guides:**
> - [INSTRUCTIONS.md](INSTRUCTIONS.md) — Step-by-step developer setup, Google Antigravity guide, USB debugging & field scanning manual.
> - [RETRAINING_GUIDE.md](RETRAINING_GUIDE.md) — Retraining in Google Colab & locally, plus automated Antigravity AI prompts and manual checklist.

---

## 📌 Overview

Banana cultivation is vital to agricultural economies, but diseases such as **Sigatoka**, **Cordana**, and **Pestalotiopsis** can cause severe yield losses if not identified and treated early. 

This application provides farmers, agricultural technicians, and researchers with an offline-capable, on-device diagnostic tool that:
1. **Detects Banana Leaves (Stage 1 Gate)**: Verifies presence of a valid banana leaf using visual and color-space metrics before running disease classification.
2. **Multi-Head Disease Inference (Stage 2 Classifiers)**: Uses independent Random Forest binary classifiers for each specific disease condition.
3. **Audio Guidance (TTS)**: Delivers clear spoken diagnostic summaries and treatment suggestions in English and Filipino/Tagalog.
4. **LGU & Agriculture Office Directory**: Direct contact links to local municipal agricultural extension workers for timely advisories.

---

## 🏗️ Architecture & Model Pipeline

The detection pipeline runs completely on-device without requiring continuous internet connectivity:

```
Camera Preview / Photo
          │
          ▼
   [ OpenCV Preprocessing ]
   ├── Color Space Conversion (HSV, LAB)
   ├── Green Foliage & Lesion Masking
   └── Texture & Morphological Extraction
          │
          ▼
[ Stage 1: Leaf Presence Gate ]
   └── 10-Feature Random Forest (`leaf_detector.bin`)
          │
   ┌──────┴──────┐
   │ Leaf OK?    │
   ├─────────────┼──────────────┐
  YES            NO             NO
   │             │              │
   ▼             ▼              ▼
[ Stage 2 ]   "No Leaf"    "Partial / Poor Lighting"
 ├── Sigatoka Head (`sigatoka.bin` - 15 features)
 ├── Cordana Head (`cordana.bin` - 15 features)
 └── Pestalotiopsis Head (`pestalotiopsis.bin` - 15 features)
          │
          ▼
[ Aggregate Diagnosis & Guidance ]
 ├── Result Bottom Sheet (Confidence & Severity)
 ├── Recommended Cultural & Chemical Treatments
 └── Text-to-Speech (TTS) Voice Briefing
```

---

## 📱 Features

- **Live Camera Feed & Instant Shutter**: Real-time viewfinder with on-screen bounding guide and dynamic status pills.
- **Glassmorphic UI & Visual Polish**: Fluid bottom sheets, dark/light theme adaptability, and smooth animations.
- **Offline ML Execution**: Compact custom binary decision tree reader (`.bin`) for high-speed inference without TensorFlow/PyTorch runtime overhead.
- **Disease Encyclopedia (Discover)**: Browse symptoms, causes, management practices, and photographic references.
- **Scan History / Garden Log**: Track previous scan results, disease progression, and timestamps.
- **LGU Connect**: Quick call, SMS, and email shortcuts to municipal agriculture offices.

---

## 📂 Repository Structure

```
├── app/
│   ├── src/main/
│   │   ├── assets/               # Model binaries (.bin) & training summary
│   │   ├── java/com/thesis/bananaleaf/
│   │   │   ├── data/             # Repositories & Local Room Database
│   │   │   ├── domain/           # Use cases, models & diagnosis engine
│   │   │   ├── ml/               # Binary RF model reader & feature scaler
│   │   │   ├── presentation/     # UI Activities, Fragments, & ViewModels
│   │   │   └── utils/            # Image processing, OpenCV wrappers & TTS
│   │   └── res/                  # Layouts, drawables, navigation & strings
├── docs/                         # Research paper & documentation
├── scripts/                      # Training & model conversion scripts
├── training-notebooks/           # Jupyter notebooks for model training & evaluation
├── build.gradle                  # Root build configuration
└── settings.gradle               # Gradle settings & module definitions
```

---

## 🛠️ Prerequisites & Setup

### Environment
- **Android Studio**: Ladybug / Hedgehog or newer
- **JDK**: Version 17
- **Target SDK**: Android 34 (Android 14)
- **Minimum SDK**: Android 24 (Android 7.0)

### Getting Started

1. **Clone the repository:**
   ```bash
   git clone https://github.com/GregorMondragon/bananaleafdetector.git
   cd bananaleafdetector
   ```

2. **Open in Android Studio:**
   - Launch Android Studio.
   - Select **Open** and choose the `bananaleafdetector` directory.
   - Allow Gradle to sync dependencies.

3. **Build and Run:**
   - Connect an Android device with USB Debugging enabled (or start an emulator with camera emulation configured).
   - Click **Run 'app'** (`Shift + F10`).

---

## 🧪 Training & Machine Learning Models

Complete retraining guides and automation instructions are documented in [RETRAINING_GUIDE.md](RETRAINING_GUIDE.md).

Model training pipelines and conversion utilities can be found in [`training-notebooks/`](training-notebooks/) and [`scripts/`](scripts/):
- **`BananaLeafModel_Final.ipynb`**: Complete dataset preprocessing, feature extraction, cross-validation, and `.bin` model export in Google Colab.
- **`scripts/train_pipeline.py`**: Automated end-to-end local training pipeline that balances datasets, trains models, writes `.bin` files, and updates Kotlin scalers automatically.
- **`training-notebooks/convert_pkl_to_bin.py`**: Exports Scikit-Learn Random Forest trees into the custom binary structure consumed by the Android app.

---

## 📄 License & Attribution

Developed for academic research and agricultural disease management thesis study. All rights reserved.
