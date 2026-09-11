package fr.octogenere.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProjectExplorerServiceTest {
    @TempDir
    Path directory;

    @Test
    void listsDirectoriesBeforeFilesAndSkipsGeneratedDirectories() throws IOException {
        Files.createDirectory(directory.resolve("src"));
        Files.createDirectory(directory.resolve("target"));
        Files.createDirectory(directory.resolve(".git"));
        Files.writeString(directory.resolve("README.md"), "Exemple");
        Files.writeString(directory.resolve("src/Hello.java"), "class Hello {}");

        ProjectExplorerService.FileNode root = new ProjectExplorerService().exploreProject(directory);

        assertEquals(List.of("src", "README.md"), root.children().stream().map(
                ProjectExplorerService.FileNode::name).toList());
        assertEquals("Hello.java", root.children().getFirst().children().getFirst().name());
    }

    @Test
    void doesNotFollowSymbolicLinksIncludingCycles() throws IOException {
        try {
            Files.createSymbolicLink(directory.resolve("cycle"), directory);
        } catch (IOException | UnsupportedOperationException error) {
            assumeTrue(false, "Création de liens non disponible sur ce système.");
        }
        assertTrue(new ProjectExplorerService().exploreProject(directory).children().isEmpty());
        assertThrows(IOException.class,
                () -> new ProjectExplorerService().exploreProject(directory.resolve("cycle")));
    }

    @Test
    void rejectsMissingProjectOrAFileInsteadOfAFolder() throws IOException {
        Files.writeString(directory.resolve("file.txt"), "test");
        ProjectExplorerService explorer = new ProjectExplorerService();
        assertThrows(IOException.class, () -> explorer.exploreProject(directory.resolve("absent")));
        assertThrows(IOException.class, () -> explorer.exploreProject(directory.resolve("file.txt")));
    }

    @Test
    void stopsAtTheDepthLimitInsteadOfRecursingForever() throws IOException {
        Path child = directory;
        for (int depth = 0; depth < 33; depth++) {
            child = Files.createDirectory(child.resolve("child"));
        }
        IOException error = assertThrows(IOException.class,
                () -> new ProjectExplorerService().exploreProject(directory));
        assertTrue(error.getMessage().contains("profonde"));
    }
}
