package fr.octogenere.project;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Lecture de l'arborescence, sans exécuter ni lire le contenu des fichiers. */
public class ProjectExplorerService {
    private static final Set<String> IGNORED_DIRECTORIES =
            Set.of(".git", ".gradle", ".idea", "target", "build", "node_modules");
    private static final int MAX_ENTRIES = 5_000;
    private static final int MAX_DEPTH = 32;

    public record FileNode(String name, boolean isDirectory, List<FileNode> children) {
        public FileNode {
            children = List.copyOf(children);
        }
    }

    public FileNode exploreProject(Path rootDirectory) throws IOException {
        if (rootDirectory == null || !Files.isDirectory(rootDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Le projet doit être un dossier existant, et non un lien symbolique.");
        }
        // Le compteur appartient à ce parcours seulement, même si le service est réutilisé.
        return buildNode(rootDirectory.toAbsolutePath().normalize(), 0, new int[]{0});
    }

    private FileNode buildNode(Path path, int depth, int[] entryCount) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("L'exploration a été interrompue.");
        }
        if (depth > MAX_DEPTH) {
            throw new IOException("Arborescence trop profonde (limite : " + MAX_DEPTH + ").");
        }
        boolean directory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
        List<FileNode> children = new ArrayList<>();
        if (directory) {
            List<Path> entries = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    if (Files.isSymbolicLink(child) || (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                            && IGNORED_DIRECTORIES.contains(child.getFileName().toString()))) {
                        continue;
                    }
                    if (++entryCount[0] > MAX_ENTRIES) {
                        throw new IOException("Projet trop volumineux pour l'explorateur (limite : "
                                + MAX_ENTRIES + " entrées).");
                    }
                    entries.add(child);
                }
            }
            entries.sort(Comparator.comparing((Path child) ->
                    !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
                    .thenComparing(child -> child.getFileName().toString()));
            for (Path child : entries) {
                children.add(buildNode(child, depth + 1, entryCount));
            }
        }
        String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        return new FileNode(name, directory, children);
    }
}
