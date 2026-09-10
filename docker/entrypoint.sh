#!/bin/sh
# Entrypoint of the sandbox container. Runs as the non-root "sandbox" user.
# /input is the read-only mount of the analyzed project's directory: it can't be
# written to, even by mistake. Its content is copied into /workspace (a tmpfs
# mounted when the container starts, see DockerCommandBuilder) before running
# the requested command, which is allowed to write there (.class files, target/, etc.).
set -eu

# The default HOME (/home/sandbox) is on the read-only rootfs: tools like Maven
# (~/.m2/repository) need a writable HOME, so we point it at the tmpfs instead.
export HOME=/workspace

cp -r /input/. /workspace/ 2>/dev/null || true
cd /workspace

exec timeout --signal=KILL "${SANDBOX_TIMEOUT_SECONDS:?SANDBOX_TIMEOUT_SECONDS is not set}" "$@"
