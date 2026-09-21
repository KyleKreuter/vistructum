#!/bin/zsh
set -euo pipefail

if [[ -z "${KAGGLE_USERNAME:-}" ]]; then
  echo "KAGGLE_USERNAME is required (your kaggle.com username)" >&2
  exit 2
fi

TRAIN_DIR="$(cd "$(dirname "$0")" && pwd)"
SUITE="${SUITE:-exp9}"
if [[ "$SUITE" == "r2" ]]; then
  KERNEL_SLUG="vistructum-r2"
  METADATA_TEMPLATE="$TRAIN_DIR/kaggle_kernel/kernel-metadata-r2.json"
  DATASET_SLUG="vistructum-r2-data"
elif [[ "$SUITE" == "r3" ]]; then
  KERNEL_SLUG="vistructum-r3"
  METADATA_TEMPLATE="$TRAIN_DIR/kaggle_kernel/kernel-metadata-r3.json"
  DATASET_SLUG="vistructum-r3-data"
elif [[ "$SUITE" == "r4" ]]; then
  KERNEL_SLUG="vistructum-r4"
  METADATA_TEMPLATE="$TRAIN_DIR/kaggle_kernel/kernel-metadata-r4.json"
  DATASET_SLUG="vistructum-r4-data"
elif [[ "$SUITE" == "r5" ]]; then
  KERNEL_SLUG="vistructum-r5"
  METADATA_TEMPLATE="$TRAIN_DIR/kaggle_kernel/kernel-metadata-r5.json"
  DATASET_SLUG="vistructum-r5-data"
elif [[ "$SUITE" == "r6" ]]; then
  KERNEL_SLUG="vistructum-r6"
  METADATA_TEMPLATE="$TRAIN_DIR/kaggle_kernel/kernel-metadata-r6.json"
  DATASET_SLUG="vistructum-r6-data"
elif [[ "$SUITE" == "r7" ]]; then
  KERNEL_SLUG="vistructum-r7"
  METADATA_TEMPLATE="$TRAIN_DIR/kaggle_kernel/kernel-metadata-r7.json"
  DATASET_SLUG="vistructum-r7-data"
elif [[ "$SUITE" == "workflow" ]]; then
  KERNEL_SLUG="vistructum-workflow"
  METADATA_TEMPLATE="$TRAIN_DIR/kaggle_kernel/kernel-metadata-workflow.json"
  DATASET_SLUG="vistructum-pools"
else
  KERNEL_SLUG="vistructum-exp9"
  METADATA_TEMPLATE="$TRAIN_DIR/kaggle_kernel/kernel-metadata.json"
  DATASET_SLUG="vistructum-exp9-data"
fi
STAGE="$(mktemp -d /tmp/kaggle-vistructum-XXXXXX)"
KERNEL_STAGE="$STAGE/kernel"
DATA_STAGE="$STAGE/dataset"
trap 'rm -rf "$STAGE"' EXIT

mkdir -p "$KERNEL_STAGE" "$DATA_STAGE/data" "$DATA_STAGE/data-exp-c" "$DATA_STAGE/data-r2" "$DATA_STAGE/data-r3" "$DATA_STAGE/data-r4" "$DATA_STAGE/data-r5" "$DATA_STAGE/data-r6m" "$DATA_STAGE/data-r6s" "$DATA_STAGE/data-r7"
if [[ "$SUITE" == "workflow" ]]; then
  cp "$TRAIN_DIR/bootstrap.py" "$KERNEL_STAGE/"
  mkdir -p "$DATA_STAGE/backgrounds-ground" "$DATA_STAGE/backgrounds-sky" "$DATA_STAGE/mined"
  cp "$TRAIN_DIR"/data-r3/backgrounds-ground/*.npy "$DATA_STAGE/backgrounds-ground/"
  cp "$TRAIN_DIR"/data-r3/backgrounds-sky/*.npy "$DATA_STAGE/backgrounds-sky/"
  cp "$TRAIN_DIR"/data-r6m/mined/*.npy "$DATA_STAGE/mined/"
  rm -rf "$DATA_STAGE/data" "$DATA_STAGE/data-exp-c" "$DATA_STAGE/data-r2" "$DATA_STAGE/data-r3" "$DATA_STAGE/data-r4" "$DATA_STAGE/data-r5" "$DATA_STAGE/data-r6m" "$DATA_STAGE/data-r6s" "$DATA_STAGE/data-r7"
else
cp "$TRAIN_DIR/kaggle_run.py" "$TRAIN_DIR/train.py" "$KERNEL_STAGE/"
sed -e "s/__KAGGLE_USER__/$KAGGLE_USERNAME/g" -e "s/^DEFAULT_SUITE = .*/DEFAULT_SUITE = \"$SUITE\"/" \
  "$TRAIN_DIR/kaggle_run.py" > "$KERNEL_STAGE/kaggle_run.py"
sed "s/__KAGGLE_USER__/$KAGGLE_USERNAME/g" \
  "$METADATA_TEMPLATE" > "$KERNEL_STAGE/kernel-metadata.json"
for suite_dir in data data-exp-c data-r2 data-r3 data-r4 data-r5 data-r6m data-r6s data-r7; do
  if [[ -n $(print "$TRAIN_DIR/$suite_dir"/*.npz(N)) ]]; then
    cp "$TRAIN_DIR/$suite_dir"/*.npz "$TRAIN_DIR/$suite_dir/manifest.json" "$DATA_STAGE/$suite_dir/"
  fi
done
cp "$TRAIN_DIR/train.py" "$DATA_STAGE/train.py"
if [[ "$SUITE" == "r2" ]]; then
  rm -rf "$DATA_STAGE/data" "$DATA_STAGE/data-exp-c" "$DATA_STAGE/data-r3"
elif [[ "$SUITE" == "r3" ]]; then
  rm -rf "$DATA_STAGE/data" "$DATA_STAGE/data-exp-c" "$DATA_STAGE/data-r2" "$DATA_STAGE/data-r4"
elif [[ "$SUITE" == "r4" ]]; then
  rm -rf "$DATA_STAGE/data" "$DATA_STAGE/data-exp-c" "$DATA_STAGE/data-r2" "$DATA_STAGE/data-r3" "$DATA_STAGE/data-r5"
elif [[ "$SUITE" == "r5" ]]; then
  rm -rf "$DATA_STAGE/data" "$DATA_STAGE/data-exp-c" "$DATA_STAGE/data-r2" "$DATA_STAGE/data-r3" "$DATA_STAGE/data-r4"
elif [[ "$SUITE" == "r6" ]]; then
  rm -rf "$DATA_STAGE/data" "$DATA_STAGE/data-exp-c" "$DATA_STAGE/data-r2" "$DATA_STAGE/data-r3" "$DATA_STAGE/data-r4" "$DATA_STAGE/data-r5" "$DATA_STAGE/data-r7"
elif [[ "$SUITE" == "r7" ]]; then
  rm -rf "$DATA_STAGE/data" "$DATA_STAGE/data-exp-c" "$DATA_STAGE/data-r2" "$DATA_STAGE/data-r3" "$DATA_STAGE/data-r4" "$DATA_STAGE/data-r5" "$DATA_STAGE/data-r6m" "$DATA_STAGE/data-r6s"
fi
fi
cat > "$DATA_STAGE/dataset-metadata.json" <<EOF
{
  "title": "$DATASET_SLUG",
  "id": "$KAGGLE_USERNAME/$DATASET_SLUG",
  "licenses": [{"name": "CC0-1.0"}]
}
EOF

if [[ "${DRY_RUN:-0}" == "1" ]]; then
  find "$STAGE" -type f | sort
  echo "DRY_RUN: staged only, no upload"
  trap - EXIT
  exit 0
fi

KAGGLE=(python3 -m kaggle)
if "${KAGGLE[@]}" datasets list --user "$KAGGLE_USERNAME" 2>/dev/null | grep -q "$DATASET_SLUG"; then
  "${KAGGLE[@]}" datasets version -p "$DATA_STAGE" --dir-mode zip -m "vistructum $SUITE training data"
else
  "${KAGGLE[@]}" datasets create -p "$DATA_STAGE" --dir-mode zip
fi
"${KAGGLE[@]}" kernels push -p "$KERNEL_STAGE"
