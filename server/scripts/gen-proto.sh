#!/usr/bin/env sh
# Генерация Go-кода из server/proto в src/internal/pb.
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
PROTO_DIR="$ROOT/proto"
OUT_DIR="$ROOT/src/internal/pb"
mkdir -p "$OUT_DIR"

protoc \
  -I "$PROTO_DIR" \
  --go_out="$OUT_DIR" --go_opt=paths=source_relative \
  --go-grpc_out="$OUT_DIR" --go-grpc_opt=paths=source_relative \
  "$PROTO_DIR"/common.proto \
  "$PROTO_DIR"/game.proto \
  "$PROTO_DIR"/auth.proto \
  "$PROTO_DIR"/matchmaking.proto \
  "$PROTO_DIR"/session.proto

echo "generated into $OUT_DIR"
