package com.searchengine.loader;

import com.searchengine.document.DocumentEntity;
import com.searchengine.document.DocumentRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Service
public class DocumentLoaderService {

    private final DocumentRepository documentRepository;

    public DocumentLoaderService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Loads documents from the data/documents directory.
     * Supports .txt and .md files.
     * Each file becomes one document with its filename as the title.
     */
    public int loadDocumentsFromDirectory(String directoryPath) {
        Path dir = Paths.get(directoryPath);
        
        if (!Files.exists(dir)) {
            return 0;
        }

        int loaded = 0;
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> documentFiles = files
                    .filter(path -> isDocumentFile(path.getFileName().toString()))
                    .toList();

            for (Path file : documentFiles) {
                try {
                    String title = removeExtension(file.getFileName().toString());
                    String content = Files.readString(file);
                    String url = "local://file/" + title.toLowerCase().replace(" ", "-");

                    DocumentEntity entity = new DocumentEntity(title, url, content);
                    documentRepository.save(entity);
                    loaded++;
                } catch (IOException e) {
                    System.err.println("Failed to load document: " + file + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read documents directory: " + e.getMessage());
        }

        return loaded;
    }

    private boolean isDocumentFile(String filename) {
        return filename.endsWith(".txt") || filename.endsWith(".md");
    }

    private String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }
}
