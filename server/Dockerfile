# syntax=docker/dockerfile:1

# --- codegen ---
FROM golang:1.23-alpine AS proto
ARG PROTOC_VERSION=28.3
ARG TARGETARCH
RUN apk add --no-cache unzip curl git \
 && case "${TARGETARCH}" in \
      amd64) PROTOC_ZIP=protoc-${PROTOC_VERSION}-linux-x86_64.zip ;; \
      arm64) PROTOC_ZIP=protoc-${PROTOC_VERSION}-linux-aarch_64.zip ;; \
      *) echo "unsupported arch ${TARGETARCH}" && exit 1 ;; \
    esac \
 && curl -fsSL -o /tmp/protoc.zip \
      "https://github.com/protocolbuffers/protobuf/releases/download/v${PROTOC_VERSION}/${PROTOC_ZIP}" \
 && unzip -q /tmp/protoc.zip -d /usr/local \
 && go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.5 \
 && go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@v1.5.1
WORKDIR /work
COPY proto ./proto
COPY scripts/gen-proto.sh ./scripts/gen-proto.sh
RUN chmod +x scripts/gen-proto.sh \
 && mkdir -p src/internal/pb \
 && ./scripts/gen-proto.sh

# --- build ---
FROM golang:1.23-alpine AS build
WORKDIR /src
RUN apk add --no-cache git ca-certificates
COPY src/go.mod src/go.sum ./
RUN go mod download
COPY src/ ./
COPY --from=proto /work/src/internal/pb/ ./internal/pb/
ARG TARGETARCH
RUN CGO_ENABLED=0 GOOS=linux GOARCH=${TARGETARCH} \
    go build -trimpath -ldflags="-s -w" -o /out/server ./cmd/server \
 && CGO_ENABLED=0 GOOS=linux GOARCH=${TARGETARCH} \
    go build -trimpath -ldflags="-s -w" -o /out/simclient ./cmd/simclient

FROM scratch AS bin
COPY --from=build /out/server /server
COPY --from=build /out/simclient /simclient

FROM gcr.io/distroless/static-debian12:nonroot AS runtime
WORKDIR /
COPY --from=build /out/server /server
ENV LOG_DIR=/logs
ENV ACME_CACHE_DIR=/autocert-cache
EXPOSE 8080 80 443
USER nonroot:nonroot
ENTRYPOINT ["/server"]

FROM gcr.io/distroless/static-debian12:nonroot AS simclient
WORKDIR /
COPY --from=build /out/simclient /simclient
USER nonroot:nonroot
ENTRYPOINT ["/simclient"]
