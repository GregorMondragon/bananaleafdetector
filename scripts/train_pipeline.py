import os
import sys
import glob
import re
import struct
import json
import time
from concurrent.futures import ProcessPoolExecutor, as_completed
import numpy as np
import cv2
from skimage.feature import graycomatrix, graycoprops

# Preprocessing & Feature Extraction definitions matching Android app
IMG_SIZE = 200
GLCM_PROPS = ["contrast", "dissimilarity", "homogeneity", "energy", "correlation", "ASM"]
LEAF_FEATURE_NAMES = [
    "veg_ratio", "circularity", "largest_blob_ratio", "aspect", "extent",
    "hue_mean", "hue_std", "sat_mean", "val_mean", "edge_density"
]
DISEASE_FEATURE_NAMES = (
    [f"{c}_{stat}" for c in ["H", "S", "V"] for stat in ["mean", "std", "skew"]] +
    [f"glcm_{p}" for p in GLCM_PROPS]
)
DISEASES = ["cordana", "sigatoka", "pestalotiopsis"]

def preprocess(img_bgr, size=IMG_SIZE):
    resized = cv2.resize(img_bgr, (size, size), interpolation=cv2.INTER_AREA)
    return cv2.GaussianBlur(resized, (5, 5), 0)

def segment_leaf(img_bgr):
    img = preprocess(img_bgr)
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    Z = hsv.reshape((-1, 3)).astype(np.float32)
    criteria = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 20, 0.5)
    _, labels, _ = cv2.kmeans(Z, 2, None, criteria, 5, cv2.KMEANS_PP_CENTERS)
    labels = labels.reshape(hsv.shape[:2])

    sat = hsv[:, :, 1]
    otsu_thresh, _ = cv2.threshold(sat, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    c0_sat = sat[labels == 0].mean() if np.any(labels == 0) else 0.0
    c1_sat = sat[labels == 1].mean() if np.any(labels == 1) else 0.0
    leaf_label = 0 if c0_sat >= c1_sat else 1

    if max(c0_sat, c1_sat) < otsu_thresh:
        lower = np.array([15, 25, 25])
        upper = np.array([95, 255, 255])
        mask = cv2.inRange(hsv, lower, upper)
    else:
        mask = np.where(labels == leaf_label, 255, 0).astype(np.uint8)

    kernel = np.ones((5, 5), np.uint8)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel)
    return img, hsv, mask

def leaf_presence_features(img_bgr):
    img, hsv, mask = segment_leaf(img_bgr)
    total = mask.size
    veg_ratio = float((mask > 0).sum()) / total

    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if contours:
        c = max(contours, key=cv2.contourArea)
        area = cv2.contourArea(c)
        perim = cv2.arcLength(c, True) + 1e-6
        circularity = 4 * np.pi * area / (perim * perim)
        largest_blob_ratio = area / total
        x, y, w, h = cv2.boundingRect(c)
        aspect = float(w) / (h + 1e-6)
        extent = float(area) / (w * h + 1e-6)
    else:
        circularity = largest_blob_ratio = aspect = extent = 0.0

    h_chan = hsv[:, :, 0].astype(np.float64)
    s_chan = hsv[:, :, 1].astype(np.float64)
    v_chan = hsv[:, :, 2].astype(np.float64)
    hue_mean, hue_std = float(h_chan.mean()), float(h_chan.std())
    sat_mean = float(s_chan.mean())
    val_mean = float(v_chan.mean())

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(gray, 50, 150)
    edge_density = float((edges > 0).sum()) / total

    return [veg_ratio, circularity, largest_blob_ratio, aspect, extent,
            hue_mean, hue_std, sat_mean, val_mean, edge_density]

def color_moments(hsv_img, mask):
    feats = []
    m = mask > 0
    if m.sum() < 20:
        m = np.ones_like(mask, dtype=bool)
    for ch in range(3):
        chan = hsv_img[:, :, ch][m].astype(np.float64)
        mean = float(chan.mean())
        std = float(chan.std())
        third_moment = float(((chan - mean) ** 3).mean())
        skew = float(np.sign(third_moment) * (abs(third_moment) ** (1.0 / 3.0)))
        feats.extend([mean, std, skew])
    return feats

def glcm_features(gray_img, mask):
    m = mask > 0
    if m.sum() < 20:
        roi = gray_img
    else:
        ys, xs = np.where(m)
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        roi = gray_img[y0:y1, x0:x1]
    roi = (roi // 4).astype(np.uint8)
    glcm = graycomatrix(roi, distances=[1], angles=[0, np.pi/4, np.pi/2, 3*np.pi/4],
                        levels=64, symmetric=True, normed=True)
    return [float(graycoprops(glcm, prop).mean()) for prop in GLCM_PROPS]

def disease_features(img_bgr):
    img, hsv, mask = segment_leaf(img_bgr)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    return color_moments(hsv, mask) + glcm_features(gray, mask)

def extract_one_image(row_info):
    path, is_leaf, cname, yc, ys, yp = row_info
    img = cv2.imread(path)
    if img is None:
        return None
    try:
        lf = leaf_presence_features(img)
        df = disease_features(img) if is_leaf else [0.0] * len(DISEASE_FEATURE_NAMES)
        return [path, is_leaf, cname, yc, ys, yp] + lf + df
    except Exception as e:
        return None

def export_rf_to_bin(model, out_path):
    n_classes = model.n_classes_
    n_features = model.n_features_in_
    assert list(model.classes_) == list(range(n_classes)), f"unexpected classes_: {model.classes_}"
    with open(out_path, "wb") as f:
        f.write(struct.pack("<iii", len(model.estimators_), n_features, n_classes))
        for est in model.estimators_:
            tree = est.tree_
            f.write(struct.pack("<i", tree.node_count))
            for i in range(tree.node_count):
                feature = int(tree.feature[i])
                threshold = float(tree.threshold[i])
                left = int(tree.children_left[i])
                right = int(tree.children_right[i])
                counts = tree.value[i][0]
                total = counts.sum()
                probs = (counts / total) if total > 0 else np.zeros(n_classes)
                f.write(struct.pack("<ifii", feature, threshold, left, right))
                f.write(struct.pack(f"<{n_classes}f", *probs.astype(np.float32)))
    print(f"Exported {out_path} ({os.path.getsize(out_path)/1024:.1f} KB)")

def main():
    print("=" * 60)
    print("Banana Leaf Disease Detection - High Confidence Retraining")
    print("=" * 60)

    # 1. Locate Dataset
    candidates = [
        r"C:\Users\User\Downloads\Banana_Leaf_Datasets\Banana_Leaf_Datasets",
        r"C:\Users\User\Downloads\Banana_Leaf_Datasets",
        r"C:\Users\User\Downloads\BananaLeaf_Dataset\BananaLeaf_Dataset",
        r"C:\Users\User\Downloads\BananaLeaf_Dataset"
    ]
    dataset_root = None
    for c in candidates:
        if os.path.exists(os.path.join(c, "SINGLE_LABEL")) and os.path.exists(os.path.join(c, "NEGATIVE")):
            dataset_root = c
            break

    if not dataset_root:
        print("ERROR: Dataset directory not found in candidate paths.")
        return

    print(f"Using dataset at: {dataset_root}")

    # 2. Build Manifest
    single_dir = os.path.join(dataset_root, "SINGLE_LABEL")
    multi_dir = os.path.join(dataset_root, "MULTI_LABEL")
    neg_dir = os.path.join(dataset_root, "NEGATIVE")

    manifest = []
    def multihot(name):
        n = name.lower()
        return [1 if d in n else 0 for d in DISEASES]

    for base_dir in [single_dir, multi_dir]:
        if not os.path.exists(base_dir):
            continue
        for folder in sorted(os.listdir(base_dir)):
            fpath = os.path.join(base_dir, folder)
            if not os.path.isdir(fpath):
                continue
            mh = multihot(folder)
            for fname in os.listdir(fpath):
                if fname.lower().endswith(('.jpg', '.jpeg', '.png', '.bmp')):
                    p = os.path.join(fpath, fname)
                    manifest.append((p, 1, folder, mh[0], mh[1], mh[2]))

    if os.path.exists(neg_dir):
        for folder in sorted(os.listdir(neg_dir)):
            fpath = os.path.join(neg_dir, folder)
            if not os.path.isdir(fpath):
                continue
            for fname in os.listdir(fpath):
                if fname.lower().endswith(('.jpg', '.jpeg', '.png', '.bmp')):
                    p = os.path.join(fpath, fname)
                    manifest.append((p, 0, f"NEGATIVE/{folder}", 0, 0, 0))

    print(f"Found {len(manifest)} total images in manifest.")

    # 3. Extract Features
    cache_csv = os.path.join(dataset_root, "features_all_cache.csv")
    cols = ["path", "is_leaf", "class", "y_cordana", "y_sigatoka", "y_pesta"] + LEAF_FEATURE_NAMES + DISEASE_FEATURE_NAMES
    
    import pandas as pd
    from sklearn.preprocessing import StandardScaler
    from sklearn.ensemble import RandomForestClassifier
    from sklearn.model_selection import StratifiedKFold
    from sklearn.metrics import classification_report, accuracy_score, f1_score

    if os.path.exists(cache_csv):
        print(f"Loading cached features from: {cache_csv}")
        df = pd.read_csv(cache_csv)
    else:
        print(f"Extracting features using all available CPU cores...")
        t0 = time.time()
        results = []
        with ProcessPoolExecutor(max_workers=os.cpu_count()) as executor:
            futures = [executor.submit(extract_one_image, m) for m in manifest]
            count = 0
            for fut in as_completed(futures):
                res = fut.result()
                if res is not None:
                    results.append(res)
                count += 1
                if count % 200 == 0 or count == len(manifest):
                    print(f"  Processed {count}/{len(manifest)} images ({count*100/len(manifest):.1f}%)...")
        print(f"Feature extraction finished in {time.time()-t0:.1f}s. Valid samples: {len(results)}")
        df = pd.DataFrame(results, columns=cols)
        df.to_csv(cache_csv, index=False)
        print(f"Saved cached features to {cache_csv}")

    # 4. Prepare Datasets for Stage 1 (Leaf Gate) and Stage 2 (Disease Heads)
    X1 = df[LEAF_FEATURE_NAMES].values
    y1 = df["is_leaf"].values.astype(int)

    leaf_mask = df["is_leaf"] == 1
    df_leaves = df[leaf_mask].reset_index(drop=True)
    X2 = df_leaves[DISEASE_FEATURE_NAMES].values
    Y2 = df_leaves[["y_cordana", "y_sigatoka", "y_pesta"]].values.astype(int)

    # 5. Fit StandardScalers
    leaf_scaler = StandardScaler()
    X1_scaled = leaf_scaler.fit_transform(X1)

    disease_scaler = StandardScaler()
    X2_scaled = disease_scaler.fit_transform(X2)

    # 6. Train Models with High-Confidence Hyperparameters
    print("\n--- Training Stage 1: Leaf Gate Model (400 trees) ---")
    leaf_model = RandomForestClassifier(
        n_estimators=400,
        max_depth=20,
        min_samples_leaf=2,
        class_weight="balanced",
        random_state=42,
        n_jobs=-1
    )
    leaf_model.fit(X1_scaled, y1)
    acc1 = accuracy_score(y1, leaf_model.predict(X1_scaled))
    print(f"Leaf Gate Training Accuracy: {acc1*100:.2f}%")

    disease_models = {}
    print("\n--- Training Stage 2: Disease Heads (400 trees each) ---")
    for i, dname in enumerate(DISEASES):
        print(f"Training {dname.capitalize()} model...")
        clf = RandomForestClassifier(
            n_estimators=400,
            max_depth=20,
            min_samples_leaf=2,
            class_weight="balanced",
            random_state=42,
            n_jobs=-1
        )
        clf.fit(X2_scaled, Y2[:, i])
        acc_d = accuracy_score(Y2[:, i], clf.predict(X2_scaled))
        print(f"  {dname.capitalize()} Training Accuracy: {acc_d*100:.2f}%")
        disease_models[dname] = clf

    # 7. Export .bin models directly into Android assets
    workspace_root = r"c:\Users\User\Downloads\bananaleafdetector-main"
    assets_dir = os.path.join(workspace_root, "app", "src", "main", "assets")
    os.makedirs(assets_dir, exist_ok=True)

    print("\n--- Exporting Binary Models to Android assets/ ---")
    export_rf_to_bin(leaf_model, os.path.join(assets_dir, "leaf_detector.bin"))
    export_rf_to_bin(disease_models["cordana"], os.path.join(assets_dir, "cordana.bin"))
    export_rf_to_bin(disease_models["sigatoka"], os.path.join(assets_dir, "sigatoka.bin"))
    export_rf_to_bin(disease_models["pestalotiopsis"], os.path.join(assets_dir, "pestalotiopsis.bin"))

    # 8. Update FeatureScaler.kt
    def kt_array(name, values):
        body = ",\n        ".join(repr(float(v)) for v in values)
        return f"    private val {name} = doubleArrayOf(\n        {body}\n    )"

    feature_scaler_kt = os.path.join(
        workspace_root, "app", "src", "main", "java", "com", "thesis", "bananaleaf", "FeatureScaler.kt"
    )
    kt_code = f"""package com.thesis.bananaleaf

/**
 * StandardScalers fitted on the newly balanced dataset (4,498 images).
 * Generated automatically by train_pipeline.py.
 */
object FeatureScaler {{
{kt_array("LEAF_MEAN", leaf_scaler.mean_)}

{kt_array("LEAF_SCALE", leaf_scaler.scale_)}

{kt_array("DISEASE_MEAN", disease_scaler.mean_)}

{kt_array("DISEASE_SCALE", disease_scaler.scale_)}

    fun transformLeaf(features: DoubleArray): DoubleArray {{
        require(features.size == LEAF_MEAN.size) {{
            "Leaf feature size mismatch: expected ${{LEAF_MEAN.size}}, got ${{features.size}}"
        }}
        return DoubleArray(features.size) {{ i ->
            (features[i] - LEAF_MEAN[i]) / LEAF_SCALE[i]
        }}
    }}

    fun transformDisease(features: DoubleArray): DoubleArray {{
        require(features.size == DISEASE_MEAN.size) {{
            "Disease feature size mismatch: expected ${{DISEASE_MEAN.size}}, got ${{features.size}}"
        }}
        return DoubleArray(features.size) {{ i ->
            (features[i] - DISEASE_MEAN[i]) / DISEASE_SCALE[i]
        }}
    }}
}}
"""
    with open(feature_scaler_kt, "w") as f:
        f.write(kt_code)
    print(f"Updated {feature_scaler_kt}")

    # 9. Update model_metadata.json
    metadata = {
        "leaf_feature_names": LEAF_FEATURE_NAMES,
        "disease_feature_names": DISEASE_FEATURE_NAMES,
        "disease_order": ["Cordana", "Sigatoka", "Pestalotiopsis"],
        "leaf_gate_threshold": 0.55,
        "disease_threshold": 0.38,
        "img_size": 200,
        "hsv_lower": [15, 25, 25],
        "hsv_upper": [95, 255, 255]
    }
    with open(os.path.join(assets_dir, "model_metadata.json"), "w") as f:
        json.dump(metadata, f, indent=2)
    print("Updated model_metadata.json")

    print("\n" + "=" * 60)
    print("SUCCESS: Retraining complete, .bin models exported, and Kotlin scalers updated!")
    print("=" * 60)

if __name__ == "__main__":
    main()
