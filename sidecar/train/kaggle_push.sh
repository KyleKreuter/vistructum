#!/bin/bash
set -euo pipefail

if [[ -z "${KAGGLE_USERNAME:-}" ]]; then
  echo "KAGGLE_USERNAME is required (your kaggle.com username)" >&2
  exit 2
fi

TRAIN_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG="${1:-configs/mask-v1.yaml}"
REF="${2:-$(git -C "$TRAIN_DIR" rev-parse HEAD)}"

if [[ ! "$REF" =~ ^[0-9a-f]{40}$ ]]; then
  echo "REF must be a full 40-char commit sha, got: $REF" >&2
  exit 2
fi
if [[ ! -f "$TRAIN_DIR/$CONFIG" ]]; then
  echo "config not found: $TRAIN_DIR/$CONFIG" >&2
  exit 2
fi

SLUG="vistructum-$(basename "$CONFIG" .yaml)"
STAGE="$(mktemp -d /tmp/kaggle-vistructum-XXXXXX)"
trap 'rm -rf "$STAGE"' EXIT

sed -e "s#__VISTRUCTUM_REF__#$REF#g" -e "s#__VISTRUCTUM_CONFIG__#$CONFIG#g" \
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
