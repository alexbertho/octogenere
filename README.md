# OctoGenere

Projet de M1 dans le cadre de l'UE Software Engineering & Design pattern, réalisé en groupe de 8.

L'objectif est de développer une application Java permettant d'évaluer un projet logiciel à l'aide d'analyses automatiques et d'un modèle de langage (LLM), puis de générer un rapport LaTeX. L'interface graphique sera réalisée avec JavaFX.

Le sujet complet est disponible dans [sujet2627-m1-en.pdf](docs/sujet2627-m1-en.pdf).

## État actuel

Un premier programme en console permet d'envoyer une question à OpenAI et d'afficher sa réponse.

Le projet utilise Java 21 et Maven. La version de JavaFX reste à choisir pour l'intégration de l'interface graphique.

## Prérequis

- Un JDK 21 ou supérieur.
- Maven 3.9 ou supérieur.
- Une clé API OpenAI disposant de l'accès au modèle choisi pour les appels réels.

## Configuration et lancement

Depuis la racine du projet, copier `.env.example` en `.env` si ce fichier n'existe pas encore. Renseigner sa clé dans `OPENAI_API_KEY` et choisir le modèle avec `OPENAI_MODEL` :

```dotenv
OPENAI_API_KEY=
OPENAI_MODEL=gpt-4.1-mini
```

Le programme lit le fichier `.env` au démarrage. Les variables d'environnement du système, lorsqu'elles sont définies, sont prioritaires. Le fichier `.env` est ignoré par Git ; `.env.example` doit rester sans secret.

Compiler et lancer la question de démonstration :

```bash
mvn compile exec:java
```

Envoyer sa propre question :

```bash
mvn compile exec:java -Dexec.args="Explique le rôle d'une interface Java."
```

Lancer les tests avec un serveur simulé local, sans clé API réelle ni appel à OpenAI :

```bash
mvn test
```

## Exécution isolée (sandbox) et défense contre l'injection de prompt

Le paquet `fr.octogenere.security` fournit deux choses, utilisables indépendamment du reste de l'application :

- `security.sandbox` : exécute une commande sur un projet analysé dans un conteneur Docker jetable, sans privilèges (utilisateur non-root, réseau coupé par défaut, filesystem en lecture seule sauf un tmpfs de travail, quotas CPU/mémoire/process, timeout). Point d'entrée : `SandboxService.createDefault().run(dossierProjet, commande)`.
- `security.injection` : prépare le contenu d'un fichier avant de l'envoyer à un LLM (délimitation explicite + repérage heuristique de tentatives d'injection de prompt, ex. un commentaire demandant d'ignorer les instructions précédentes). Point d'entrée : `PromptInjectionGuard.createDefault().protect(nomFichier, contenu)`.

Les tests unitaires (`mvn test`) ne nécessitent pas Docker : ils utilisent un `FakeSandboxExecutor`. Pour lancer aussi les tests d'intégration qui utilisent réellement Docker, construire d'abord l'image :

```bash
docker build -t octogenere/sandbox:1.0 -f docker/Dockerfile docker
mvn -Dtest=fr.octogenere.security.sandbox.DockerSandboxExecutorIT test
```

## Fonctionnalités prévues

- Importer un projet depuis un dossier local ou une archive et afficher son arborescence.
- Choisir des critères d'évaluation et lancer des analyses, dont une partie utilisera un vrai LLM.
- Afficher la progression, les erreurs et les résultats dans l'interface JavaFX.
- Générer automatiquement un rapport d'évaluation au format LaTeX.

L'architecture devra séparer l'interface graphique de la logique métier, permettre de remplacer le fournisseur LLM et rendre les composants testables indépendamment. Les projets analysés seront considérés comme non fiables : toute exécution de leur code devra être isolée conformément au sujet.

## Équipe

| Partie | Membres |
| --- | --- |
| IA | Mathéo Darnaudguilhem, Reese Dode, Alice Durand |
| Interface graphique — JavaFX | Alexia Gastaud |
| Architecture logicielle | Sébastien Lallement, Gilles Conrad, Louis Ciebiera, Alexandre Bertho |

## Branches

- `main` : versions validées du projet.
- `dev` : développement en cours.
