# Image utilisée pour exécuter le code d'un projet analysé de façon isolée.
# Voir fr.octogenere.security.sandbox.DockerSandboxExecutor / DockerCommandBuilder
# pour les options docker run appliquées en plus de ce qui est défini ici
# (utilisateur non-root, réseau désactivé, lecture seule, quotas de ressources...).
#
# Construction :
#   docker build -t octogenere/sandbox:1.0 -f docker/Dockerfile .

FROM eclipse-temurin:21-jdk-alpine

# maven : pour compiler/tester des projets Maven (le cas d'usage principal attendu).
# coreutils : busybox ne fournit pas `timeout --signal=KILL`, dont on a besoin dans l'entrypoint.
RUN apk add --no-cache maven coreutils

RUN addgroup -g 10001 sandbox \
    # -h /workspace : certains outils (dont la JVM elle-même pour user.home) lisent le home
    # directement dans /etc/passwd plutôt que la variable d'environnement HOME.
    && adduser -D -u 10001 -h /workspace -G sandbox sandbox \
    && mkdir -p /input /workspace \
    && chown -R sandbox:sandbox /workspace

COPY --chmod=755 entrypoint.sh /usr/local/bin/entrypoint.sh

USER sandbox
WORKDIR /workspace

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
