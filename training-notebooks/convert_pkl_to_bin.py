"""
Converts the retrained scikit-learn RandomForestClassifier .pkl models
(from model.zip / Colab) into the .bin format RandomForestModel.kt reads,
and extracts the two StandardScaler .pkl files into Kotlin array literals
for FeatureScaler.kt.

Binary layout (little-endian) -- must match RandomForestModel.kt exactly:
  int32  numTrees
  int32  numFeatures
  int32  numClasses
  repeated numTrees times:
    int32 numNodes
    repeated numNodes times:
      int32   featureIndex   (-2 if leaf)
      float32 threshold
      int32   leftChild      (-1 if leaf)
      int32   rightChild     (-1 if leaf)
      float32 x numClasses
"""
import struct, os, joblib, numpy as np, json

MODEL_DIR = "/home/claude/work/model/model"
OUT_DIR = "/home/claude/work/out_bin"

MODELS = [
    ("leaf_gate_model.pkl", "leaf_detector.bin"),
    ("disease_cordana_model.pkl", "cordana.bin"),
    ("disease_sigatoka_model.pkl", "sigatoka.bin"),
    ("disease_pestalotiopsis_model.pkl", "pestalotiopsis.bin"),
]

def export_rf_to_bin(model, out_path):
    n_classes = model.n_classes_
    n_features = model.n_features_in_
    assert list(model.classes_) == list(range(n_classes)), \
        f"unexpected classes_ order: {model.classes_}"
    with open(out_path, "wb") as f:
        f.write(struct.pack("<iii", len(model.estimators_), n_features, n_classes))
        total_nodes = 0
        for est in model.estimators_:
            tree = est.tree_
            n_nodes = tree.node_count
            total_nodes += n_nodes
            f.write(struct.pack("<i", n_nodes))
            for i in range(n_nodes):
                feature = int(tree.feature[i])
                threshold = float(tree.threshold[i])
                left = int(tree.children_left[i])
                right = int(tree.children_right[i])
                counts = tree.value[i][0]
                total = counts.sum()
                probs = (counts / total) if total > 0 else np.zeros(n_classes)
                f.write(struct.pack("<ifii", feature, threshold, left, right))
                f.write(struct.pack(f"<{n_classes}f", *probs.astype(np.float32)))
    size_kb = os.path.getsize(out_path) / 1024
    print(f"  wrote {out_path} ({size_kb:.1f} KB) trees={len(model.estimators_)} "
          f"features={n_features} classes={n_classes} total_nodes={total_nodes}")
    return {"trees": len(model.estimators_), "features": n_features,
            "classes": n_classes, "nodes": total_nodes}

def read_bin_header(path):
    with open(path, "rb") as f:
        numTrees, numFeatures, numClasses = struct.unpack("<iii", f.read(12))
    return numTrees, numFeatures, numClasses

os.makedirs(OUT_DIR, exist_ok=True)
header_report = {}
print("Exporting Random Forest models to .bin ...")
for pkl_name, bin_name in MODELS:
    model = joblib.load(os.path.join(MODEL_DIR, pkl_name))
    out_path = os.path.join(OUT_DIR, bin_name)
    header_report[bin_name] = export_rf_to_bin(model, out_path)

# sanity re-read of headers, same check RandomForestModel.kt does on load
print("\nVerifying headers by re-reading each .bin file...")
for pkl_name, bin_name in MODELS:
    t, feat, cls = read_bin_header(os.path.join(OUT_DIR, bin_name))
    print(f"  {bin_name}: numTrees={t} numFeatures={feat} numClasses={cls}")

def kt_array(name, values):
    body = ",\n        ".join(repr(float(v)) for v in values)
    return f"    private val {name} = doubleArrayOf(\n        {body}\n    )"

print("\nExporting scaler values to Kotlin snippet...")
leaf_scaler = joblib.load(os.path.join(MODEL_DIR, "leaf_gate_scaler.pkl"))
disease_scaler = joblib.load(os.path.join(MODEL_DIR, "disease_scaler.pkl"))

kt_code = "\n\n".join([
    kt_array("LEAF_MEAN", leaf_scaler.mean_),
    kt_array("LEAF_SCALE", leaf_scaler.scale_),
    kt_array("DISEASE_MEAN", disease_scaler.mean_),
    kt_array("DISEASE_SCALE", disease_scaler.scale_),
])
with open(os.path.join(OUT_DIR, "FeatureScaler_snippet.txt"), "w") as f:
    f.write(kt_code)
print("Wrote FeatureScaler_snippet.txt")

with open(os.path.join(OUT_DIR, "header_report.json"), "w") as f:
    json.dump(header_report, f, indent=2)
print("\nDone.")
