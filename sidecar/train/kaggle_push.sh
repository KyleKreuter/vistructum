#!/bin/bash
set -euo pipefail

if [[ -z "${KAGGLE_USERNAME:-}" ]]; then
  echo "KAGGLE_USERNAME is required (your kaggle.com username)" >&2
  exit 2
fi

TRAIN_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIGS="${1:-configs/mask-v1.yaml,configs/scan-v1.yaml}"
REF="${2:-$(git -C "$TRAIN_DIR" rev-parse HEAD)}"
SLUG="${KERNEL_SLUG:-vistructum-train}"

if [[ ! "$REF" =~ ^[0-9a-f]{40}$ ]]; then
  echo "REF must be a full 40-char commit sha, got: $REF" >&2
  exit 2
fi
IFS=',' read -ra CONFIG_LIST <<< "$CONFIGS"
for config in "${CONFIG_LIST[@]}"; do
  if [[ ! -f "$TRAIN_DIR/$config" ]]; then
    echo "config not found: $TRAIN_DIR/$config" >&2
    exit 2
  fi
done
git -C "$TRAIN_DIR" fetch -q origin
if [[ -z "$(git -C "$TRAIN_DIR" branch -r --contains "$REF" 2>/dev/null)" ]]; then
  echo "commit $REF is not on any remote branch; push it first, the kernel clones from GitHub" >&2
  exit 2
fi

STAGE="$(mktemp -d /tmp/kaggle-vistructum-XXXXXX)"
trap 'rm -rf "$STAGE"' EXIT

sed -e "s#__VISTRUCTUM_REF__#$REF#g" -e "s#__VISTRUCTUM_CONFIGS__#$CONFIGS#g" \
  "$TRAIN_DIR/bootstrap.py" > "$STAGE/bootstrap.py"
sed -e "s/__KAGGLE_USER__/$KAGGLE_USERNAME/g" -e "s/__KERNEL_SLUG__/$SLUG/g" \
  "$TRAIN_DIR/kaggle_kernel/kernel-metadata.json" > "$STAGE/kernel-metadata.json"

if [[ "${DRY_RUN:-0}" == "1" ]]; then
  find "$STAGE" -type f | sort
  echo "DRY_RUN: staged only, no push"
  trap - EXIT
  exit 0
fi

python3 -m kaggle kernels push -p "$STAGE"
echo "pushed $SLUG at $REF with $CONFIGS; fetch results with: kaggle kernels output $KAGGLE_USERNAME/$SLUG -p results/"
