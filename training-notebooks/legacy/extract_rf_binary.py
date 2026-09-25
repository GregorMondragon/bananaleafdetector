"""
Extracts a trained scikit-learn RandomForestClassifier into a compact binary
format that the Android app reads at runtime with a small tree-walking
interpreter (RandomForestModel.kt).

Why not m2cgen codegen? This forest has 400 trees / ~158k total nodes.
m2cgen's direct Java code-gen produced a 20MB source file whose "score"
logic would exceed the JVM's 64KB-per-method bytecode limit and fail to
compile. A data-driven interpreter (data file + one generic walk function)
sidesteps that entirely and is the standard approach for deploying forests
this large on-device.

Binary layout (little-endian):
  int32  numTrees
  int32  numFeatures
  int32  numClasses
  repeated numTrees times:
    int32 numNodes
    repeated numNodes times (one record per tree node, in sklearn's
    internal node order — index 0 is always that tree's root):
      int32   featureIndex   (-2 if leaf)
      float32 threshold
      int32   leftChild      (-1 if leaf)
      int32   rightChild     (-1 if leaf)
      float32 x numClasses   (class probability distribution at this node;
                               only meaningful/used at leaves, but stored
                               for every node to keep record size fixed)
"""
import struct
import joblib
import numpy as np

MODEL_PATH = "/mnt/user-data/uploads/best_model.pkl"
OUT_PATH = "/home/claude/BananaLeafDetector/app/src/main/assets/rf_model.bin"


def main():
    model = joblib.load(MODEL_PATH)
    n_classes = model.n_classes_
    n_features = model.n_features_in_

    with open(OUT_PATH, "wb") as f:
        f.write(struct.pack("<iii", len(model.estimators_), n_features, n_classes))

        for est in model.estimators_:
            tree = est.tree_
            n_nodes = tree.node_count
            f.write(struct.pack("<i", n_nodes))

            for i in range(n_nodes):
                feature = int(tree.feature[i])          # -2 == leaf (TREE_UNDEFINED)
                threshold = float(tree.threshold[i])
                left = int(tree.children_left[i])        # -1 for leaves
                right = int(tree.children_right[i])

                counts = tree.value[i][0]                 # shape (n_classes,)
                total = counts.sum()
                probs = (counts / total) if total > 0 else np.zeros(n_classes)

                f.write(struct.pack("<ifii", feature, threshold, left, right))
                f.write(struct.pack(f"<{n_classes}f", *probs.astype(np.float32)))

    import os
    size_mb = os.path.getsize(OUT_PATH) / (1024 * 1024)
    print(f"Wrote {OUT_PATH} ({size_mb:.2f} MB), {len(model.estimators_)} trees, "
          f"{sum(e.tree_.node_count for e in model.estimators_)} total nodes")


if __name__ == "__main__":
    main()
