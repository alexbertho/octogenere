#!/bin/sh
# Point d'entrée du conteneur sandbox. Tourne en tant qu'utilisateur non-root "sandbox".
# /input est le montage en lecture seule du dossier du projet analysé : on ne peut
# donc pas y écrire, même par erreur. On copie son contenu dans /workspace (un tmpfs
# monté au démarrage du conteneur, cf. DockerCommandBuilder) avant d'exécuter la
# commande demandée, qui elle a le droit d'écrire (fichiers .class, target/, etc.).
set -eu

# HOME par défaut (/home/sandbox) est sur le rootfs en lecture seule : des outils comme Maven
# (~/.m2/repository) ont besoin d'un HOME inscriptible, donc on le fait pointer vers le tmpfs.
export HOME=/workspace

cp -r /input/. /workspace/ 2>/dev/null || true
cd /workspace

exec timeout --signal=KILL "${SANDBOX_TIMEOUT_SECONDS:?SANDBOX_TIMEOUT_SECONDS non défini}" "$@"
