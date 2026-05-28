# Server Enhanced Mod

A NeoForge server-side mod for Minecraft 1.21, designed for internal use. It provides utility features to enhance server administration and monitoring.

## Features

### Prometheus Metrics Exporter
Exposes server metrics via an HTTP endpoint (`/metrics`) in Prometheus format for monitoring and alerting.

- Configurable port (default: `9225`, set to `0` to disable)
- Zero external dependencies — uses plain Java sockets

### Player Death Notification
Sends a webhook notification (HTTP POST with JSON body) when a player dies, including the death message.

- Configurable URL and auth header

### Location Cache
In-game command system (`/location`) for saving, retrieving, and removing named coordinates — useful for quickly sharing points of interest among players.

## Configuration

Server config is generated at `serverconfig/serverenhancedmod-server.toml`:

```toml
[living-death-notification]
url = ""
auth = ""

[prometheus]
port = 9225
```

## Build

```bash
./gradlew build
```

Output jar is located in `build/libs/`.

## Release

Push a version tag to trigger a GitHub Actions build and publish to GitHub Releases:

```bash
git tag v1.0.0
git push origin v1.0.0
```
