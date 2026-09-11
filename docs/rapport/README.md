# Rapport académique OctoGenere

Le rapport complet se trouve dans ce dossier :

- `Rapport-OctoGenere.pdf` : document final ;
- `main.tex` : source d'entrée ;
- `01-introduction.tex` à `04-validation.tex` : chapitres ;
- `assets/` : logo et crédits.

Le document décrit le code au commit `c779781` du 11 septembre 2026 ; les limitations et les évolutions envisagées sont distinguées de l'implémentation.

## Compilation

Depuis la racine, avec XeLaTeX, latexmk, les polices TeX Gyre Pagella et Heros, Latin Modern Mono, ainsi que les paquets LaTeX standards (dont TikZ et tcolorbox) :

```bash
latexmk -xelatex -interaction=nonstopmode -halt-on-error -outdir=target/rapport docs/rapport/main.tex
cp target/rapport/main.pdf docs/rapport/Rapport-OctoGenere.pdf
```

Les fichiers intermédiaires de compilation restent dans `target/rapport`, ignoré par Git.

## Sources et validation

Les noms et numéros étudiants proviennent du brouillon fourni. Les pôles de travail sont repris du README du groupe. Les descriptions techniques ont été rapprochées des classes présentes dans le dépôt ; les schémas sont construits avec TikZ.

`validation-tests.json` conserve les compteurs de la suite Maven exécutée sur la version décrite : 109 tests recensés, 104 réussis, 5 graphiques non exécutés. Les fichiers `*IT` sont exclus de ce total : un ancien rapport d'intégration Docker ne doit pas être additionné à une exécution standard. Aucun rapport Surefire brut ni propriété d'environnement n'est copié dans ce dossier.

Le document n'est pas un exemple d'évaluation produit par le moteur : c'est le rapport technique du projet demandé dans le sujet. Les références externes et les crédits du logo figurent à la fin du PDF.
