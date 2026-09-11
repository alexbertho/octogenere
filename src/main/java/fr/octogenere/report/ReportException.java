package fr.octogenere.report;

/** Signale qu'un rapport n'a pas pu être généré. */
public class ReportException extends RuntimeException {
    public ReportException(String message) {
        super(message);
    }

    public ReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
