# Image used to execute an analyzed project's code in isolation.
# See fr.octogenere.security.sandbox.DockerSandboxExecutor / DockerCommandBuilder
# for the docker run options applied on top of what's defined here
# (non-root user, network disabled, read-only, resource limits...).
#
# Build:
#   docker build -t octogenere/sandbox:1.0 -f docker/Dockerfile docker

FROM eclipse-temurin:21-jdk-alpine

# maven: to compile/test Maven projects (the main expected use case).
# coreutils: busybox doesn't provide `timeout --signal=KILL`, which the entrypoint needs.
RUN apk add --no-cache maven coreutils

RUN addgroup -g 10001 sandbox \
    # -h /workspace: some tools (including the JVM itself for user.home) read the home
    # directory straight from /etc/passwd rather than the HOME environment variable.
    && adduser -D -u 10001 -h /workspace -G sandbox sandbox \
    && mkdir -p /input /workspace \
    && chown -R sandbox:sandbox /workspace

COPY --chmod=755 entrypoint.sh /usr/local/bin/entrypoint.sh

USER sandbox
WORKDIR /workspace

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
