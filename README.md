# OctoGenere

Projet de M1 dans le cadre de l'UE Software Engineering & Design pattern, réalisé en groupe de 8.

L'objectif est de développer une application Java permettant d'évaluer un projet logiciel à l'aide d'analyses automatiques et d'un modèle de langage (LLM), puis de générer un rapport LaTeX. L'interface graphique sera réalisée avec JavaFX.

Le sujet complet est disponible dans [sujet2627-m1-en.pdf](docs/sujet2627-m1-en.pdf).

## État actuel

L'interface JavaFX permet de sélectionner un dossier, de lancer une analyse de démonstration sans appel API et de générer un rapport LaTeX. Le programme console OpenAI reste disponible.

Le projet utilise Java 21, JavaFX 21.0.11 et Maven.

## Prérequis

- Un JDK 21 ou supérieur.
- Maven 3.9 ou supérieur.
- Une clé API OpenAI disposant de l'accès au modèle choisi pour les appels réels.

## Configuration et lancement

Lancer l'interface graphique depuis la racine du projet :

```bash
mvn javafx:run
```

Ce mode fonctionne sans `.env` ni clé API. Les scores sont fictifs et identifiés comme une démonstration dans l'interface et le rapport. Sélectionner un dossier, choisir les critères, lancer l'analyse, puis cliquer sur « GÉNÉRER LE RAPPORT LATEX ». Chaque export crée un dossier distinct dans `target/reports/`, contenant `rapport-evaluation.tex` et le logo. La compilation PDF reste une étape séparée.

Pour le programme console OpenAI :

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

Les tests graphiques s'activent séparément et nécessitent une session graphique (ils ouvrent temporairement des fenêtres) :

```bash
mvn -Doctogenere.ui.tests=true -Dtest=UiWorkflowTest test
```

## Security module: sandboxed execution & prompt-injection defense

`fr.octogenere.security` provides two independent things, usable by any other module:

- **`security.sandbox`** — runs a command on an analyzed project inside a disposable, unprivileged Docker container (non-root user, network disabled by default, read-only filesystem except a writable scratch tmpfs, CPU/memory/process/time limits, forced cleanup even on timeout). Entry point: `SandboxService.createDefault().run(projectDir, command)`.
- **`security.injection`** — prepares a file's content before it's sent to an LLM: wraps it in explicit delimiters and heuristically flags prompt-injection attempts (e.g. a comment asking the model to ignore its previous instructions), without dropping the content. Entry point: `PromptInjectionGuard.createDefault().protect(fileName, content)`.

### Prerequisites

- JDK 21 and Maven (same as the rest of the project).
- [Docker](https://www.docker.com/) installed and running — only needed to actually execute code in the sandbox or run its integration tests. Everything else (including `mvn test`) works without it.

### Getting it running

1. Build the sandbox image once (rebuild it any time `docker/Dockerfile` or `docker/entrypoint.sh` changes):

   ```bash
   docker build -t octogenere/sandbox:1.0 -f docker/Dockerfile docker
   ```

2. Use it from Java:

   ```java
   SandboxService sandbox = SandboxService.createDefault();
   SandboxResult result = sandbox.run(projectDir, List.of("mvn", "-q", "-DskipTests", "compile"));
   // result.exitCode(), result.stdout(), result.stderr(), result.timedOut()

   PromptInjectionGuard guard = PromptInjectionGuard.createDefault();
   PromptInjectionGuard.GuardedContent guarded = guard.protect("Foo.java", fileContent);  //Foo.java optional and purely cosmetic!, the fileContent string is the actual source code
   String safeToSendToTheLlm = guarded.safePromptFragment();
   ```

### Running the tests

- `mvn test` runs everything except Docker-dependent tests (they're named `*IT.java`, so Surefire skips them by default) — this includes all of `security.sandbox` and `security.injection`, using a `FakeSandboxExecutor` instead of real Docker.
- To also run the real Docker integration tests (build the image first, see above):

  ```bash
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
