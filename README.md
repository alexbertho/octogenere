# OctoGenere

Projet de M1 dans le cadre de l'UE Software Engineering & Design pattern, réalisé en groupe de 8.

L'objectif est de développer une application Java permettant d'évaluer un projet logiciel à l'aide d'analyses automatiques et d'un modèle de langage (LLM), puis de générer un rapport LaTeX. L'interface graphique sera réalisée avec JavaFX.

Le sujet complet est disponible dans [sujet2627-m1-en.pdf](docs/sujet2627-m1-en.pdf).

## État actuel

L'interface JavaFX permet de sélectionner un dossier, de choisir les critères, de lancer une analyse avec Gemini ou OpenAI et de générer un rapport LaTeX à partir des résultats validés. Les fichiers source sont transmis sous forme d'un contexte XML borné ; les dossiers générés et les fichiers sensibles courants sont exclus.

Le projet utilise Java 21, JavaFX 21.0.11 et Maven.

## Prérequis

- Un JDK 21 ou supérieur.
- Maven 3.9 ou supérieur.
- Une clé API Google Gemini ou OpenAI disposant de l'accès au modèle choisi.

## Configuration et lancement

Copier `.env.example` en `.env`, choisir le fournisseur puis renseigner la clé correspondante :

```dotenv
LLM_PROVIDER=gemini
GOOGLE_API_KEY=
GOOGLE_MODEL=gemini-3.8-flash
```

Pour utiliser OpenAI :

```dotenv
LLM_PROVIDER=openai
OPENAI_API_KEY=
OPENAI_MODEL=gpt-4.1-mini
```

Lancer ensuite l'interface graphique depuis la racine du projet :

```bash
mvn javafx:run
```

L'application démarre même si la configuration LLM est absente ou invalide. Le détail apparaît dans les logs de l'interface et l'analyse peut être relancée après correction et redémarrage. Sélectionner un dossier, choisir les critères, lancer l'analyse, puis cliquer sur « GÉNÉRER LE RAPPORT LATEX ». Chaque export crée un dossier distinct dans `target/reports/`, contenant `rapport-evaluation.tex` et le logo. La compilation PDF reste une étape séparée.

Le programme lit le fichier `.env` au démarrage. Les variables d'environnement du système, lorsqu'elles sont définies, sont prioritaires. Le fichier `.env` est ignoré par Git ; `.env.example` doit rester sans secret.

Avec Gemini, le sélecteur interroge `models.list` en arrière-plan et propose uniquement les modèles compatibles avec `generateContent`. Si cette liste est vide ou inaccessible, `GOOGLE_MODEL` reste le modèle sélectionné.

Le même point d'entrée peut aussi être lancé avec le plugin Maven Exec :

```bash
mvn compile exec:java
```

Lancer les tests avec un serveur simulé local, sans clé API réelle ni appel à OpenAI :

```bash
mvn test
```

Les tests graphiques s'activent séparément et nécessitent une session graphique (ils ouvrent temporairement des fenêtres) :

```bash
mvn -Doctogenere.ui.tests=true -Dtest=UiWorkflowTest test
```

## Module de Sécurité : Execution dans un environnement protégé Sandbox

`fr.octogenere.security` fournis deux fonctionnalités, utilisables dans n'importe quel module :

- **`security.sandbox`** - Execute une commande sur le projet a analyser. Fait au sein d'un environnement Docker temporaire et non priviligié (pas d'utilisateur root, aucun accès internet, filesystem en lecture seule formis sur un filesystem temporaire, limites sur CPU/Memoire/Processus/Temps, reinitialisation forcée de l'environnement). Point d'entrée : `SandboxService.createDefault().run(projectDir, command)`.
- **`security.injection`** - Prépare le contenu d'un fichier avant qu'il soit envoyer au LLM : le fichier est délimités en sections explicites et les tentatives de prompt injection sont commentées et annotées (ex : commentaires demandant au modele d'ignorer les instructions précedentes), sans pour autant qu'il n'y ai de perte de données. Point d'entrée : `PromptInjectionGuard.createDefault().protect(fileName, content)`.

### Prérequis

- JDK 21 et Maven
- [Docker](https://www.docker.com/) : installé et en cours d'execution - utilisé uniquement pour executer le code dans l'environnement Sandbox ou lancer ces tests d'intégrations. Le reste (dont `mvn test`) fonctionne sans.

### Lancer le projet

1. Construire l'image de la Sandbox au moins une fois (attention : penser à reconstruire en cas d'un changement de `docker/Dockerfile` ou bien de `docker/entrypoint.sh`) :

   ```bash
   docker build -t octogenere/sandbox:1.0 -f docker/Dockerfile docker
   ```

2. Utilisation dans Java :

   ```java
   SandboxService sandbox = SandboxService.createDefault();
   SandboxResult result = sandbox.run(projectDir, List.of("mvn", "-q", "-DskipTests", "compile"));
   // result.exitCode(), result.stdout(), result.stderr(), result.timedOut()

   PromptInjectionGuard guard = PromptInjectionGuard.createDefault();
   PromptInjectionGuard.GuardedContent guarded = guard.protect("Foo.java", fileContent);  //Foo.java optional and purely cosmetic!, the fileContent string is the actual source code
   String safeToSendToTheLlm = guarded.safePromptFragment();
   ```

   **`SandboxService.run`**
   - In: `projectDir` (`Path`), `command` (`List<String>`).
   - Out: `SandboxResult` record — `exitCode` (`int`), `stdout` (`String`), `stderr` (`String`), `timedOut` (`boolean`), `duration` (`Duration`).

   **`PromptInjectionGuard.protect`**
   - In: `label` (`String`, cosmetic only), `content` (`String`, the file's text).
   - Out: `GuardedContent` record — `safePromptFragment` (`String`), `scanResult` (`ScanResult` record: `riskLevel` (`RiskLevel`), `findings` (`List<Finding>`)).

### Execution des tests

- `mvn test` execute tout hormis les tests qui dépendent de Docker (ils s'appelent `*IT.java`, Surefire les ignorent par défaut) - cela inclu `security.sandbox` et `security.injection`, utilisant `FakeSandboxExecutor` plutot qu'une vraie instance de Docker.
- To also run the real Docker integration tests (build the image first, see above):
- Pour executer les veritables tests d'integration de Docker (commencer par construire les images, voir ci-dessus) : 
  ```bash
  mvn -Dtest=fr.octogenere.security.sandbox.DockerSandboxExecutorIT test
  ```

## Pipeline d'analyse

1. L'UI charge le dossier et les critères définis dans `src/main/resources/analysis/criteria.json`.
2. Les fichiers texte utiles sont filtrés, limités en nombre et en taille, protégés contre l'injection de prompt puis représentés en XML.
3. Le fournisseur configuré reçoit un prompt exigeant une réponse JSON stricte.
4. La réponse est validée : critères attendus, scores maximaux, champs obligatoires et absence de données supplémentaires.
5. Les mêmes résultats servent à l'affichage et à la génération du rapport LaTeX.

Les erreurs de configuration, d'authentification, de quota, de réseau et de format de réponse sont remontées dans l'interface sans afficher le corps privé des erreurs du fournisseur.

L'architecture sépare l'interface graphique de la logique métier, permet de remplacer le fournisseur LLM et garde les composants testables indépendamment. Les projets analysés sont considérés comme non fiables : toute exécution de leur code doit être isolée conformément au sujet.

## Équipe

| Partie | Membres |
| --- | --- |
| IA | Mathéo Darnaudguilhem, Reese Dode, Alice Durand |
| Interface graphique — JavaFX | Alexia Gastaud |
| Architecture logicielle | Sébastien Lallement, Gilles Conrad, Louis Ciebiera, Alexandre Bertho |

## Branches

- `main` : versions validées du projet.
- `dev` : développement en cours.
