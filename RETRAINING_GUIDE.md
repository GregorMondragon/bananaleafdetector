# Banana Leaf Disease Detector — Model Retraining & Automation Guide 🧪🤖

This guide covers complete instructions for retraining the Banana Leaf Disease Detection machine learning models, both in the cloud (**Google Colab**) and on your **local machine**, as well as how to **automate the entire pipeline using the Google Antigravity AI Agent** and follow a **manual checklist**.

---

## 📑 Table of Contents

1. [Model Architecture Overview](#1-model-architecture-overview)
2. [Dataset Structure Requirements](#2-dataset-structure-requirements)
3. [Method 1: Retraining in Google Colab (Cloud)](#3-method-1-retraining-in-google-colab-cloud)
4. [Method 2: Retraining on Your Local Computer](#4-method-2-retraining-on-your-local-computer)
5. [Method 3: Automated Retraining via Google Antigravity Agent AI](#5-method-3-automated-retraining-via-google-antigravity-agent-ai)
   - [Ready-to-Use Antigravity AI Prompts](#ready-to-use-antigravity-ai-prompts)
   - [Autonomous Agent Execution Workflow](#autonomous-agent-execution-workflow)
6. [Method 4: Complete Manual Step-by-Step Checklist](#6-method-4-complete-manual-step-by-step-checklist)
7. [Validating Binary Models & Kotlin Scalers](#7-validating-binary-models--kotlin-scalers)
8. [Troubleshooting & Common Retraining Gotchas](#8-troubleshooting--common-retraining-gotchas)

---

## 1. Model Architecture Overview

The app avoids heavy TensorFlow/PyTorch runtimes by executing a lightweight, native 2-Stage Random Forest pipeline:

```
[ Input Image (200x200 BGR) ]
               │
               ▼
[ Leaf Segmentation (K-Means / Otsu) ]
               │
               ▼
[ Stage 1: Leaf Presence Gate ]
 ├── Model: `leaf_detector.bin` (200 Trees, 2 Classes)
 ├── Features (10): veg_ratio, circularity, largest_blob_ratio, aspect, extent,
 │                  hue_mean, hue_std, sat_mean, val_mean, edge_density
 └── Normalized via: `FeatureScaler.transformLeaf()`
               │
      [ Leaf Verified? ]
         ├── NO  ──> Exit: "No Banana Leaf Detected"
         └── YES ──> Pass to Stage 2
               │
               ▼
[ Stage 2: Multi-Head Disease Classifiers ]
 ├── Features (15): H/S/V mean, std, skewness (9 color moments) + 6 GLCM texture features
 ├── Normalized via: `FeatureScaler.transformDisease()`
 └── Three Independent Heads:
     ├── `sigatoka.bin`        (200 Trees, 15 features) ── Threshold: 0.38
     ├── `cordana.bin`         (400 Trees, 15 features) ── Threshold: 0.38
     └── `pestalotiopsis.bin`  (200 Trees, 15 features) ── Threshold: 0.38
```

---

## 2. Dataset Structure Requirements

Ensure your images are formatted in standard single-label subfolders:

```
dataset/
├── Cordana/             # Banana leaves infected with Cordana leaf spot
├── Healthy/             # Healthy green banana leaves (no lesions)
├── Pestalotiopsis/      # Leaves with Pestalotiopsis necrotic spots
└── Sigatoka/            # Leaves with Black / Yellow Sigatoka streaks
```

- **Supported Formats**: `.jpg`, `.jpeg`, `.png`, `.webp`
- **Recommended Count**: 300 to 800+ original high-resolution photos per class.
- **Background Variety**: Natural field foliage, banana stalks, sunlight variations, and overcast conditions.

---

## 3. Method 1: Retraining in Google Colab (Cloud)

Google Colab allows you to run training on Google's cloud infrastructure without utilizing local CPU/GPU resources.

### Step-by-Step Instructions:

1. **Upload Notebook to Google Drive / Colab**:
   - Go to [Google Colaboratory](https://colab.research.google.com/).
   - Click **File > Upload Notebook** and select:
     `training-notebooks/BananaLeafModel_Final.ipynb`.

2. **Upload Your Dataset**:
   - Zip your dataset folder into `dataset.zip`.
   - In Colab, click the **Files** icon in the left sidebar and upload `dataset.zip`.
   - In an empty cell, extract the dataset:
     ```python
     !unzip -q dataset.zip -d /content/dataset
     ```

3. **Install Required Libraries**:
   Run this in Colab to ensure compatible versions:
   ```bash
   !pip install opencv-python-headless scikit-learn scikit-image joblib numpy pandas
   ```

4. **Run the Notebook**:
   - Set `DATASET_DIR = "/content/dataset"`.
   - Click **Runtime > Run all** (`Ctrl + F9`).
   - The notebook executes:
     - Image preprocessing and background masking.
     - Extraction of 10 leaf gate features and 15 disease features.
     - Augmented data generation with `StratifiedGroupKFold` (preventing image leakage).
     - Hyperparameter tuning (`n_estimators`, tree depth) via `GridSearchCV`.
     - Cross-validation evaluation and F1 score generation.
     - Serializing models into `model.zip` (containing `.pkl` files and scalers).

5. **Download the Trained Artifacts**:
   - Download `model.zip` from Colab to your workstation.
   - Extract `model.zip` into a local directory (e.g., `model/`).

6. **Convert `.pkl` to Android `.bin` Format**:
   - Open your local terminal in the project directory.
   - Edit `training-notebooks/convert_pkl_to_bin.py` to point `MODEL_DIR` to your extracted folder.
   - Run the conversion script:
     ```powershell
     python training-notebooks/convert_pkl_to_bin.py
     ```
   - This writes:
     - `leaf_detector.bin`
     - `cordana.bin`
     - `sigatoka.bin`
     - `pestalotiopsis.bin`
     - `FeatureScaler_snippet.txt` (updated Kotlin array values)

7. **Deploy to Android App**:
   - Copy the 4 `.bin` files into `app/src/main/assets/`.
   - Copy the array contents of `FeatureScaler_snippet.txt` into `app/src/main/java/com/thesis/bananaleaf/FeatureScaler.kt`.

---

## 4. Method 2: Retraining on Your Local Computer

If you have Python installed locally, you can use the fully automated Python script `scripts/train_pipeline.py`.

### Step-by-Step Instructions:

1. **Set Up Local Python Environment**:
   Open PowerShell in the project root:
   ```powershell
   # Create virtual environment
   python -m venv .venv

   # Activate virtual environment
   .\.venv\Scripts\Activate.ps1

   # Install dependencies
   pip install opencv-python scikit-learn scikit-image joblib numpy pandas
   ```

2. **Run the Automated Training Script**:
   Run `train_pipeline.py` pointing to your local dataset:
   ```powershell
   python scripts/train_pipeline.py --data_dir "C:\Users\User\Downloads\BananaLeaf_Dataset"
   ```

3. **What `train_pipeline.py` Does Automatically**:
   - ✅ Extracts botanical and lesion features in parallel using multi-core processing (`ProcessPoolExecutor`).
   - ✅ Balances classes with targeted augmentation multipliers.
   - ✅ Fits `StandardScaler` for Stage 1 (10 features) and Stage 2 (15 features).
   - ✅ Trains all four Random Forest models.
   - ✅ Converts models directly into `.bin` files and saves them to `app/src/main/assets/`.
   - ✅ **Automatically updates [FeatureScaler.kt](file:///c:/Users/User/Downloads/bananaleafdetector-main/app/src/main/java/com/thesis/bananaleaf/FeatureScaler.kt)** with the exact mean and scale arrays!
   - ✅ Updates `app/src/main/assets/model_metadata.json`.

4. **Recompile the Android App**:
   ```powershell
   .\gradlew.bat assembleDebug
   ```

---

## 5. Method 3: Automated Retraining via Google Antigravity Agent AI

Google Antigravity AI can act as an autonomous machine learning engineer. You can prompt the AI agent to execute, validate, and deploy retrained models end-to-end without manual intervention.

### Ready-to-Use Antigravity AI Prompts

Simply copy and paste any of these prompts directly into the **Google Antigravity Chat**:

#### 💡 Prompt 1: Full Retraining, Asset Replacement & Build Verification
```markdown
Antigravity, please retrain the banana leaf disease models using the local dataset located at "C:\Users\User\Downloads\BananaLeaf_Dataset". 
Follow these steps:
1. Run scripts/train_pipeline.py using the virtual environment.
2. Verify that the four .bin files in app/src/main/assets/ are updated.
3. Verify that FeatureScaler.kt has been regenerated with matching feature dimensions.
4. Execute `.\gradlew.bat assembleDebug` and confirm that the project compiles cleanly without errors.
```

#### 💡 Prompt 2: Convert Colab Model Output and Deploy to Phone
```markdown
Antigravity, I downloaded a new `model.zip` from Google Colab and extracted it into `C:\Users\User\Downloads\new_models`.
Please:
1. Run `training-notebooks/convert_pkl_to_bin.py` configured for this directory.
2. Replace `leaf_detector.bin`, `cordana.bin`, `sigatoka.bin`, and `pestalotiopsis.bin` in `app/src/main/assets/`.
3. Update `FeatureScaler.kt` with the new mean and scale double arrays.
4. Install the debug APK to my USB-connected Android device using `.\gradlew.bat installDebug` and launch the app.
```

#### 💡 Prompt 3: Check for Feature Leakage & Validate Model Headers
```markdown
Antigravity, inspect `training-notebooks/BananaLeafModel_Final.ipynb` and `scripts/train_pipeline.py`:
1. Check if the cross-validation logic strictly prevents augmented copies of the same image from appearing in both training and test sets.
2. Inspect the raw binary headers of all four `.bin` files in `app/src/main/assets/` to ensure tree counts and feature counts match what `RandomForestModel.kt` expects (10 features for leaf gate, 15 features for disease heads).
```

### Autonomous Agent Execution Workflow

When you issue a prompt to Antigravity:
1. **Interactive Planning**: Antigravity reviews the file paths and creates a step-by-step execution plan.
2. **Autonomous Tool Calls**: The agent executes PowerShell commands, runs Python training, verifies file outputs, and inspects diffs.
3. **Automatic Code Generation**: If scalers or thresholds changed, Antigravity edits [FeatureScaler.kt](file:///c:/Users/User/Downloads/bananaleafdetector-main/app/src/main/java/com/thesis/bananaleaf/FeatureScaler.kt) and [DiagnoseLeafUseCase.kt](file:///c:/Users/User/Downloads/bananaleafdetector-main/app/src/main/java/com/thesis/bananaleaf/domain/usecase/DiagnoseLeafUseCase.kt).
4. **Build & Test**: Antigravity triggers `assembleDebug` to confirm no compilation issues before handing back control.

---

## 6. Method 4: Complete Manual Step-by-Step Checklist

For developers who prefer complete manual control, follow this sequential checklist:

- [ ] **Step 1: Inspect Dataset**
  - Verify that each folder (`Cordana`, `Healthy`, `Pestalotiopsis`, `Sigatoka`) contains only valid banana leaf images.
  - Remove duplicate, corrupt, or non-banana images.

- [ ] **Step 2: Check Feature Consistency**
  - Verify feature counts:
    - **Leaf Gate**: Exactly **10 features** (`veg_ratio`, `circularity`, `largest_blob_ratio`, `aspect`, `extent`, `hue_mean`, `hue_std`, `sat_mean`, `val_mean`, `edge_density`).
    - **Disease Heads**: Exactly **15 features** (9 HSV moments + 6 GLCM textures).

- [ ] **Step 3: Run Model Training**
  - Train Stage 1 (`leaf_gate_model.pkl`) using 2 classes (Non-leaf vs Banana Leaf).
  - Train Stage 2 (`cordana`, `sigatoka`, `pestalotiopsis` models) using binary classifiers (0 = Negative/Healthy, 1 = Positive/Diseased).

- [ ] **Step 4: Export to Binary Format**
  - Convert `.pkl` files to `.bin` using little-endian 32-bit integer/float format.
  - Verify binary file headers:
    ```
    leaf_detector.bin  -> numFeatures: 10, numClasses: 2
    cordana.bin        -> numFeatures: 15, numClasses: 2
    sigatoka.bin       -> numFeatures: 15, numClasses: 2
    pestalotiopsis.bin -> numFeatures: 15, numClasses: 2
    ```

- [ ] **Step 5: Replace App Assets**
  - Copy all four `.bin` files to `app/src/main/assets/`.

- [ ] **Step 6: Sync Kotlin Scalers**
  - Open `app/src/main/java/com/thesis/bananaleaf/FeatureScaler.kt`.
  - Update `LEAF_MEAN` and `LEAF_SCALE` (10 elements each).
  - Update `DISEASE_MEAN` and `DISEASE_SCALE` (15 elements each).

- [ ] **Step 7: Clean and Build Android Project**
  ```powershell
  .\gradlew.bat clean assembleDebug
  ```

- [ ] **Step 8: Field Smoke Test**
  - Install to device: `.\gradlew.bat installDebug`.
  - Test on a real banana leaf and verify that the reticle turns green.
  - Test on a non-leaf background (e.g., desk, wall, clothing) and verify rejection.

---

## 7. Validating Binary Models & Kotlin Scalers

You can verify the header bytes of any `.bin` file directly using PowerShell to confirm they match Android requirements:

```powershell
# PowerShell script to inspect .bin header
$file = "app\src\main\assets\leaf_detector.bin"
$bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $file).Path)
$numTrees = [System.BitConverter]::ToInt32($bytes, 0)
$numFeatures = [System.BitConverter]::ToInt32($bytes, 4)
$numClasses = [System.BitConverter]::ToInt32($bytes, 8)
Write-Host "$file -> Trees: $numTrees, Features: $numFeatures, Classes: $numClasses"
```

**Expected Results:**
- `leaf_detector.bin`: `Trees: 200, Features: 10, Classes: 2`
- `cordana.bin`: `Trees: 400, Features: 15, Classes: 2`
- `sigatoka.bin`: `Trees: 200, Features: 15, Classes: 2`
- `pestalotiopsis.bin`: `Trees: 200, Features: 15, Classes: 2`

---

## 8. Troubleshooting & Common Retraining Gotchas

### 1. "Feature size mismatch" Crash at Runtime
- **Symptom**: App crashes on capture with `IllegalArgumentException: Leaf feature size mismatch: expected 10, got X`.
- **Cause**: The scaler array in `FeatureScaler.kt` or the feature vector in `ImageProcessor.kt` was modified to have a different number of features than the model expects.
- **Fix**: Ensure `FeatureScaler.kt` matches the 10-feature leaf gate and 15-feature disease heads.

### 2. High Training Accuracy but Poor Field Detection (Data Leakage)
- **Symptom**: Model reports 98% accuracy in notebook but misidentifies real leaves in field tests.
- **Cause**: Image augmentations (`_aug1.jpg`, `_aug2.jpg`) of the same original image ended up in both the training set and the test set.
- **Fix**: Use `StratifiedGroupKFold` grouped by original filename before applying augmentations.

### 3. Model Predicts Everything as Sigatoka
- **Symptom**: Model diagnoses almost every spot as Sigatoka.
- **Cause**: Imbalanced training dataset where Sigatoka had 4x more samples than Cordana or Pestalotiopsis.
- **Fix**: Rebalance augmentations so each class has approximately 900–1,200 samples before training.

---

*For further assistance or agent automation scripts, refer to [INSTRUCTIONS.md](INSTRUCTIONS.md) and [README.md](README.md).*
