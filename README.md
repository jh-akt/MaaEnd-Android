# MaaEnd Android

This directory contains the standalone Android Root-only MVP host app for MaaEnd.

See `docs/SESSION_SUMMARY.md` for the latest handoff summary and known next steps.

## Runtime staging

The Android app expects prebuilt runtime artifacts to be staged under:

- `runtime/agent/go-service`
- `runtime/maafw/`

Use `tools/prepare_android_runtime.py` from the repository root to stage them.

By default this project reads shared assets from the sibling `../MaaEnd/assets` directory.

If the runtime directory is empty, the app still boots and the Root bootstrap chain works, but only the `AndroidOpenGame` fallback path is expected to function.
