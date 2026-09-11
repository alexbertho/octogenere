package fr.octogenere.analysis;

/** Notifications du moteur : aucune dépendance à JavaFX. */
public interface AnalysisObserver {
    void onProgressUpdate(double progress, String statusMessage);

    void onNewLogAdded(String message);
}
