package com.searchengine.loader;

import com.searchengine.document.DocumentEntity;
import com.searchengine.document.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Service
public class DocumentLoaderService {
    private static final Logger log = LoggerFactory.getLogger(DocumentLoaderService.class);

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
                    String raw = Files.readString(file);
                    String content = file.getFileName().toString().endsWith(".md") ? stripMarkdown(raw) : raw;
                    String url = "local://file/" + title.toLowerCase(java.util.Locale.ROOT).replace(" ", "-");

                    DocumentEntity entity = new DocumentEntity(title, url, content);
                    documentRepository.save(entity);
                    loaded++;
                } catch (IOException e) {
                    log.warn("Failed to load document {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read documents directory {}: {}", dir, e.getMessage());
        }

        return loaded;
    }

    /** Plain text for indexing and snippets: drops heading marks, code fences, emphasis and link syntax. */
    static String stripMarkdown(String md) {
        return md.replaceAll("(?m)^```.*$", "")
                .replaceAll("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*", "")
                .replaceAll("(?m)^[ \\t]*[-*+][ \\t]+", "")
                .replaceAll("\\*\\*|__|`", "")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private boolean isDocumentFile(String filename) {
        return filename.endsWith(".txt") || filename.endsWith(".md");
    }

    private String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }
}
