This directory is retained for local development overrides. To use a complete
runtime here, pass `-PmaafwRuntimeDir=runtime` to Gradle. It must contain both the
Android arm64 agents and `maafw/libMaaFramework.so` with its dependencies.

Default builds instead restore the verified runtime pinned by `runtime.lock.json`
into `build/preparedRuntime/`. The `Publish Android Runtime` workflow remains an
optional way to rebuild the project-specific agents and publish a separate runtime.
