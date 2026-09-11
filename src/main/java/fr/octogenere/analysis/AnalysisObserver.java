package fr.octogenere.analysis;

/**
 * Pattern Observer permettant au moteur d'analyse de notifier l'interface
 * graphique de son avancement.
 * Cette interface garantit que le moteur métier reste totalement indépendant
 * de la technologie d'affichage.
 */
public interface AnalysisObserver {
    /**
     * Notifie l'observateur d'un changement dans la progression de l'analyse.
     *
     * @param progress La progression actuelle (entre 0.0 et 1.0, ou -1.0 pour indéterminé).
     * @param statusMessage Un court message décrivant l'étape en cours (ex: "Analyse du code...").
     */
    void onProgressUpdate(double progress, String statusMessage);
    /**
     * Notifie l'observateur qu'un nouveau message de journalisation (log) est disponible.
     *
     * @param message Le texte brut à afficher dans la console de l'interface.
     */
    void onNewLogAdded(String message);
}
