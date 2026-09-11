package fr.octogenere.prompt.xml;

import fr.octogenere.security.injection.PromptInjectionGuard;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Produit une représentation XML déterministe et bornée des fichiers texte d'un projet. */
public final class XmlFileTreeBuilder {
    static final int MAX_FILES = 120;
    static final long MAX_FILE_BYTES = 64 * 1024L;
    static final long MAX_TOTAL_BYTES = 512 * 1024L;
    private static final int MAX_DISCOVERED_FILES = 5_000;

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".idea", ".vscode", ".gradle", "target", "build", "dist", "out",
            "node_modules", "vendor", "coverage", ".next", ".cache");
    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            "java", "kt", "kts", "scala", "groovy", "gradle", "js", "jsx", "ts", "tsx",
            "py", "rb", "go", "rs", "c", "cc", "cpp", "h", "hpp", "cs", "php", "swift",
            "sql", "html", "css", "scss", "sass", "less", "vue", "svelte", "xml", "json",
            "yml", "yaml", "toml", "properties", "md", "adoc", "txt", "sh", "bash", "zsh",
            "fish", "ps1", "bat", "cmd");
    private static final Set<String> SPECIAL_TEXT_FILES = Set.of(
            "dockerfile", "makefile", "jenkinsfile", "procfile", "pom.xml", "build.gradle",
            "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "package.json",
            "tsconfig.json", ".gitignore", ".dockerignore", ".env.example");
    private static final Set<String> SECRET_EXTENSIONS = Set.of(
            "pem", "key", "p12", "pfx", "jks", "keystore");

    private final PromptInjectionGuard injectionGuard;

    public XmlFileTreeBuilder() {
        this(PromptInjectionGuard.createDefault());
    }

    XmlFileTreeBuilder(PromptInjectionGuard injectionGuard) {
        this.injectionGuard = injectionGuard;
    }

    public ProjectContext build(Path projectDirectory) throws IOException {
        if (projectDirectory == null || !Files.isDirectory(projectDirectory)) {
            throw new IllegalArgumentException("Sélectionne un dossier de projet existant.");
        }
        Path root = projectDirectory.toAbsolutePath().normalize();
        ScanState state = collectCandidates(root);
        state.candidates.sort(Comparator.comparing(path -> normalizedRelativePath(root, path)));

        List<IncludedFile> included = new ArrayList<>();
        long totalBytes = 0;
        for (Path path : state.candidates) {
            if (included.size() >= MAX_FILES) {
                state.omittedFiles++;
                state.truncated = true;
                continue;
            }
            String relativePath = normalizedRelativePath(root, path);
            if (!isSupportedTextFile(path) || isSensitive(path)) {
                state.omittedFiles++;
                continue;
            }

            long size;
            try {
                size = Files.size(path);
            } catch (IOException error) {
                state.omittedFiles++;
                continue;
            }
            if (size > MAX_FILE_BYTES || totalBytes + size > MAX_TOTAL_BYTES) {
                state.omittedFiles++;
                state.truncated = true;
                continue;
            }

            try {
                byte[] bytes = Files.readAllBytes(path);
                String content = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
                PromptInjectionGuard.GuardedContent guarded = injectionGuard.protect(relativePath, content);
                included.add(new IncludedFile(relativePath, guarded.safePromptFragment()));
                totalBytes += bytes.length;
                if (guarded.scanResult().suspicious()) {
                    state.suspiciousFiles.add(relativePath);
                }
            } catch (IOException error) {
                state.omittedFiles++;
            }
        }

        if (included.isEmpty()) {
            throw new IOException("Aucun fichier texte exploitable n'a été trouvé dans le projet.");
        }
        String xml = renderXml(root, included, state);
        return new ProjectContext(xml, included.size(), state.omittedFiles, state.truncated,
                state.suspiciousFiles);
    }

    private ScanState collectCandidates(Path root) throws IOException {
        ScanState state = new ScanState();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (!directory.equals(root)
                        && IGNORED_DIRECTORIES.contains(directory.getFileName().toString().toLowerCase(Locale.ROOT))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && !Files.isSymbolicLink(file)) {
                    if (state.candidates.size() >= MAX_DISCOVERED_FILES) {
                        state.omittedFiles++;
                        state.truncated = true;
                        return FileVisitResult.TERMINATE;
                    }
                    state.candidates.add(file);
                } else {
                    state.omittedFiles++;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException error) {
                state.omittedFiles++;
                return FileVisitResult.CONTINUE;
            }
        });
        return state;
    }

    private String renderXml(Path root, List<IncludedFile> files, ScanState state) throws IOException {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element project = document.createElement("project");
            Path fileName = root.getFileName();
            project.setAttribute("name", fileName == null ? root.toString() : fileName.toString());
            project.setAttribute("includedFiles", Integer.toString(files.size()));
            project.setAttribute("omittedFiles", Integer.toString(state.omittedFiles));
            project.setAttribute("truncated", Boolean.toString(state.truncated));
            document.appendChild(project);

            Map<String, Element> folders = new HashMap<>();
            folders.put("", project);
            for (IncludedFile included : files) {
                String[] components = included.relativePath.split("/");
                Element parent = project;
                StringBuilder folderPath = new StringBuilder();
                for (int index = 0; index < components.length - 1; index++) {
                    if (!folderPath.isEmpty()) {
                        folderPath.append('/');
                    }
                    folderPath.append(components[index]);
                    String key = folderPath.toString();
                    Element existing = folders.get(key);
                    if (existing == null) {
                        existing = document.createElement("folder");
                        existing.setAttribute("path", key);
                        parent.appendChild(existing);
                        folders.put(key, existing);
                    }
                    parent = existing;
                }
                Element file = document.createElement("file");
                file.setAttribute("path", included.relativePath);
                file.setTextContent(included.guardedContent);
                parent.appendChild(file);
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toString();
        } catch (Exception error) {
            throw new IOException("Impossible de construire le contexte XML du projet.", error);
        }
    }

    private boolean isSupportedTextFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (SPECIAL_TEXT_FILES.contains(name)) {
            return true;
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 && SOURCE_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private boolean isSensitive(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.equals(".env") || (name.startsWith(".env.") && !name.equals(".env.example"))
                || name.equals("credentials.json") || name.equals("service-account.json")) {
            return true;
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && SECRET_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private String normalizedRelativePath(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private record IncludedFile(String relativePath, String guardedContent) {
    }

    private static final class ScanState {
        private final List<Path> candidates = new ArrayList<>();
        private final List<String> suspiciousFiles = new ArrayList<>();
        private int omittedFiles;
        private boolean truncated;
    }
}
