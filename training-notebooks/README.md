# Training notebooks

This app has been through three separate training experiments. Only **one**
of them produced the models this app actually ships and reads.

## ⚠️ Pending retrain (2026-09-15 fixes not yet baked into `assets/*.bin`)

Two real bugs were found and fixed in `BananaLeafModel_Final.ipynb` after
the `.bin` files below were exported, so **the currently-shipped models
were trained BEFORE these fixes existed**:

1. **Train/test leakage.** The split (Stage 1 leaf-gate + Stage 2 disease
   heads) used a plain `train_test_split` on individual images. Augmented
   copies of a photo (`leaf_aug1.jpg`, `leaf_aug2.jpg`, ...) could land on
   the opposite side of the split from their original, so the model was
   sometimes "tested" on a leaf it had effectively already seen in
   training -- inflating test accuracy without improving real-world
   detection. Fixed with `StratifiedGroupKFold`, grouped by original
   filename (with an `assert` that catches any remaining leak).
2. **Backwards augmentation multipliers.** `AUGMENT_CLASSES` gave the
   class with the MOST originals (Sigatoka, 462) the highest multiplier
   (3x -> 1,848 final images), while the class with the FEWEST originals
   (Pestalotiopsis, 330) got none (0x -> stayed at 330) -- widening the
   imbalance instead of fixing it. This lines up with which disease heads
   ended up weakest (Cordana/Pestalotiopsis) vs strongest (Sigatoka) in
   `training_summary.json`. Rebalanced to Sigatoka 1x / Healthy 2x /
   Cordana 3x / Pestalotiopsis 3x (~900-1,300 images per class).

**Before re-running:** delete the old `_aug*.jpg` files from
`SINGLE_LABEL/*` on Drive (they were generated with the old multipliers)
and delete/rename the cached `features_all.csv`, or the old and new
augmented copies will both be counted. Then re-run the notebook from the
augmentation cell down, re-export the four `.bin` files + `FeatureScaler`
values, and replace everything in `app/src/main/assets/` -- the numbers
in the table below and in `FeatureScaler.kt` will be stale until that
retrain happens.

## ✅ Current (use this one): `BananaLeafModel_Final.ipynb`

This is the source of truth. Confirmed by reading the raw header bytes of
the `.bin` files in `app/src/main/assets/` (last retrained 2026-09-13, via
`model.zip` from Colab converted through `extract_rf_binary.py`-style
export -- tree counts changed because GridSearchCV picked different
`n_estimators` per head this run; feature counts did not):

| File | trees | features | classes |
|---|---|---|---|
| `leaf_detector.bin` | 200 | 10 | 2 |
| `cordana.bin` | 400 | 15 | 2 |
| `sigatoka.bin` | 200 | 15 | 2 |
| `pestalotiopsis.bin` | 200 | 15 | 2 |

That 10 leaf-gate-feature / 15 disease-feature split matches this
notebook's feature extraction, `ImageProcessor.kt`, `FeatureScaler.kt`, and
`assets/model_metadata.json` exactly. If you retrain, retrain from here,
re-run its "Kotlin (Android) port status" and "Android app sync note"
cells, and re-check the `.bin` header feature counts against the table
above before replacing the assets.

✅ Fixed: this retrain flipped which disease head is weakest (Sigatoka is
now the strongest, f1 0.93; Cordana is now weakest, f1 0.7411; Pestalotiopsis
is in between, f1 0.7616 -- see `assets/training_summary.json`). The
hand-tuned `CORDANA_THRESHOLD` / `SIGATOKA_THRESHOLD` /
`PESTALOTIOPSIS_THRESHOLD` constants in `MainActivity.kt` have been
reassigned to match: weakest head (Cordana) now gets the highest threshold
(0.65), strongest head (Sigatoka) gets the lowest (0.55), same 0.55/0.60/0.65
spread as before, just reordered. This is a principled reordering based on
overall f1, not a re-derived PR-curve sweep (model_report.txt from this
retrain doesn't include a per-class precision-at-0.5 breakdown) -- re-tune
from a real PR curve if field false-positive/negative rates say otherwise.

## ⚠️ Legacy (do not use) — `legacy/`

Two earlier/parallel experiments, kept only for reference:

- **`bananaLeaf_MULTILABEL.ipynb`** — different feature set (17 features:
  lesion color/texture + green_ratio + texture_std), different leaf
  threshold (0.65, tuned for *its* features). This is very likely where
  the old `LEAF_PRESENT_THRESHOLD = 0.65f` in `MainActivity.kt` came
  from — copied over even though it doesn't match the models actually in
  `assets/`. That's been corrected to `0.5f`.
- **`bananaLeaf_MULTILABEL_DETECTION.ipynb`** — a different, 33-feature,
  single-scaler, ONNX-based pipeline (`isleaf_model.onnx`, etc.). Its
  outputs (`scaler_params.json`, `feature_names.json`, `thresholds.json`)
  were previously sitting in `app/src/main/assets/` unused and have been
  removed — nothing in this app's Kotlin code reads ONNX models.

Neither legacy notebook's exported models are compatible with
`RandomForestModel.kt`'s `.bin` reader as currently used by this app
(wrong feature counts), and `extract_rf_binary.py` in this folder also
points at stale hardcoded paths (`/mnt/user-data/uploads/best_model.pkl`,
a single `rf_model.bin`) from an even earlier single-model layout — it
predates the four-separate-`.bin`-files approach the app uses now.
