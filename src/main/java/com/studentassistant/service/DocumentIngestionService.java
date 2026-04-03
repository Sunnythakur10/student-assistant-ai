package com.studentassistant.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public String ingestFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File must have a name");
        }

        String lower = originalFilename.toLowerCase(Locale.ROOT);
        List<Document> documents;

        if (lower.endsWith(".txt")) {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            documents = List.of(new Document(content));
        } else if (lower.endsWith(".pdf")) {
            PagePdfDocumentReader reader = new PagePdfDocumentReader(file.getResource());
            documents = reader.get();
        } else {
            throw new IllegalArgumentException("Unsupported file type. Only .pdf and .txt are supported.");
        }

        TokenTextSplitter splitter = createTokenTextSplitter();
        List<Document> splitDocs = splitter.split(documents);

        vectorStore.add(splitDocs);

        log.info("Ingested {} chunks from {}", splitDocs.size(), originalFilename);
        return "Successfully ingested " + splitDocs.size() + " chunks from " + originalFilename;
    }

    @PostConstruct
    public void seedSampleDocuments() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:documents/*.txt");
            if (resources.length == 0) {
                log.warn("No sample documents found under classpath:documents/");
                return;
            }

            TokenTextSplitter splitter = createTokenTextSplitter();
            for (Resource resource : resources) {
                try {
                    String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                    Document doc = new Document(content);
                    List<Document> splitDocs = splitter.split(List.of(doc));
                    vectorStore.add(splitDocs);
                    log.info("Seeded sample document: {}", resource.getFilename());
                } catch (Exception ex) {
                    log.warn("Failed to seed document: {}", resource.getFilename(), ex);
                }
            }
        } catch (Exception ex) {
            log.warn("Sample document seeding skipped: {}", ex.getMessage());
        }
    }

    private static TokenTextSplitter createTokenTextSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(100)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(1000)
                .withKeepSeparator(false)
                .build();
    }
}
