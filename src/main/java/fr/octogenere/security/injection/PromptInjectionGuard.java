package fr.octogenere.security.injection;

/**
 * Point d'entrée unique pour rendre un contenu de projet sûr à insérer dans un
 * prompt (pattern Facade) : scan + délimitation en un seul appel. Le contenu
 * suspect n'est pas bloqué ici - il est quand même délimité et renvoyé, à
 * charge pour l'appelant (construction du prompt, génération du rapport) de
 * décider quoi faire de {@link ScanResult#suspicious()} (log, mention dans le
 * rapport, etc.). Bloquer un fichier entier sur une simple heuristique
 * risquerait trop de faux positifs.
 */
public final class PromptInjectionGuard {
    private final PromptInjectionScanner scanner;

    public PromptInjectionGuard(PromptInjectionScanner scanner) {
        if (scanner == null) {
            throw new IllegalArgumentException("scanner ne peut pas être nul");
        }
        this.scanner = scanner;
    }

    public static PromptInjectionGuard createDefault() {
        return new PromptInjectionGuard(new HeuristicPromptInjectionScanner());
    }

    public GuardedContent protect(String label, String rawContent) {
        ScanResult scanResult = scanner.scan(rawContent);
        String safeFragment = UntrustedContentWrapper.wrap(label, rawContent);
        return new GuardedContent(safeFragment, scanResult);
    }

    public record GuardedContent(String safePromptFragment, ScanResult scanResult) {
    }
}
