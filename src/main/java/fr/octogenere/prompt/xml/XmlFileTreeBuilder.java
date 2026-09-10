package fr.octogenere.prompt.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;
import java.util.stream.Stream;

public class XmlFileTreeBuilder {

    // Les dossiers à ignorer systématiquement
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", ".idea", ".vscode", "target", "build", "node_modules"
    );

    public static String BuildXmlFileTree(String folderPath) throws Exception {
        Path rootPath = Paths.get(folderPath);

        // Initialisation du constructeur de document XML
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        Element projectElement = doc.createElement("project");
        doc.appendChild(projectElement);

        // Parcours récursif
        addDirectory(doc, projectElement, rootPath, rootPath);

        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            
            // Pretty print configuration
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            // Use StringWriter to capture the output stream
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            DOMSource source = new DOMSource(doc);
            
            // Perform the transformation
            transformer.transform(source, result);
            
            return writer.toString();
            
        } catch (TransformerException e) {
            throw new RuntimeException("Failed to convert XML Document to String", e);
        }
    }

    private static void addDirectory(Document doc, Element parentElement, Path currentDir, Path rootDir) {
        // Files.list n'est pas récursif par défaut (comme iterdir en Python)
        try (Stream<Path> paths = Files.list(currentDir)) {
            paths.sorted().forEach(path -> {
                String fileName = path.getFileName().toString();

                if (IGNORED_DIRS.contains(fileName)) {
                    return; // Équivalent au "continue" dans la boucle
                }

                Path relativePath = rootDir.relativize(path);

                if (Files.isDirectory(path)) {
                    Element folderElement = doc.createElement("folder");
                    folderElement.setAttribute("path", relativePath.toString());
                    parentElement.appendChild(folderElement);
                    
                    addDirectory(doc, folderElement, path, rootDir);
                    
                } else if (Files.isRegularFile(path)) {
                    Element fileElement = doc.createElement("file");
                    fileElement.setAttribute("path", relativePath.toString());
                    parentElement.appendChild(fileElement);

                    try {
                        String content = Files.readString(path, StandardCharsets.UTF_8);
                        fileElement.setTextContent(content);
                    } catch (MalformedInputException e) {
                        // Équivalent à UnicodeDecodeError
                        fileElement.setAttribute("binary", "true");
                    } catch (AccessDeniedException e) {
                        // Équivalent à PermissionError
                        fileElement.setAttribute("binary", "true");
                    } catch (IOException e) {
                        // Fallback pour toute autre erreur de lecture
                        fileElement.setAttribute("binary", "true");
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("Impossible de lire le dossier : " + currentDir);
        }
    }
}