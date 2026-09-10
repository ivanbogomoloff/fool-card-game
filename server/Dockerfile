# syntax=docker/dockerfile:1

# --- build (alpine легче bookworm) ---
FROM golang:1.22-alpine AS build
WORKDIR /src
RUN apk add --no-cache git ca-certificates
COPY src/go.mod src/go.sum ./
RUN go mod download
COPY src/ ./
ARG TARGETARCH
RUN CGO_ENABLED=0 GOOS=linux GOARCH=${TARGETARCH} \
    go build -trimpath -ldflags="-s -w" -o /out/server ./cmd/server

# --- только бинарник (опциональный target) ---
FROM scratch AS bin
COPY --from=build /out/server /server

# --- runtime: ~2MB base + CA (нужны для LE/TLS на этапе 2) ---
# distroless/static заметно меньше debian-slim; scratch ещё меньше, но без CA/user.
FROM gcr.io/distroless/static-debian12:nonroot AS runtime
WORKDIR /
COPY --from=build /out/server /server
ENV LOG_DIR=/logs
EXPOSE 8080 80 443
USER nonroot:nonroot
ENTRYPOINT ["/server"]
