This directory is the local development fallback consumed by the shared Runtime Gradle plugin:

- `agent/go-service` - Android arm64 build of `agent/go-service`
- `maafw/` - MaaFramework Android runtime files required by the Go agent

The manually triggered `Publish Android Runtime` workflow rebuilds the project-specific agents, merges the verified MaaFramework Android libraries, and publishes the authoritative zip plus SHA-256 checksum. Local files here are not used as release provenance.
