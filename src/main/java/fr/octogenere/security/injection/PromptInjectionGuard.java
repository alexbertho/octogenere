package fr.octogenere.security.injection;

/**
 * Single entry point to make a piece of project content safe to insert into a
 * prompt (Facade pattern): scan + delimiting in one call. Suspicious content
 * is not blocked here - it is still delimited and returned, leaving it up to
 * the caller (prompt building, report generation) to decide what to do with
 * {@link ScanResult#suspicious()} (log it, mention it in the report, etc.).
 * Blocking a whole file on a simple heuristic alone would risk too many false
 * positives.
 */
public final class PromptInjectionGuard {
    private final PromptInjectionScanner scanner;

    public PromptInjectionGuard(PromptInjectionScanner scanner) {
        if (scanner == null) {
            throw new IllegalArgumentException("scanner cannot be null");
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
