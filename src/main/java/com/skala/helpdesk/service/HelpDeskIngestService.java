package com.skala.helpdesk.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.skala.helpdesk.config.HelpDeskProperties;

/** day02의 읽기→분할→메타데이터→임베딩/저장 파이프라인을 그대로 확장한다. */
@Service
public class HelpDeskIngestService {

    private static final String DOCUMENT_PATTERN = "classpath:/helpdesk-docs/*.md";
    private final VectorStore vectorStore;
    private final HelpDeskProperties properties;
    private final Map<String, List<String>> indexedIds = new ConcurrentHashMap<>();

    public HelpDeskIngestService(VectorStore vectorStore, HelpDeskProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public record IngestResult(
            String source, String version, int chunks, int chunkSize, int minChunkSizeChars) {}

    public record ChunkView(
            String source, String version, int chunk, double score, String preview) {}

    public List<IngestResult> ingestSamples() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(DOCUMENT_PATTERN);
            return java.util.Arrays.stream(resources)
                    .sorted(Comparator.comparing(resource -> String.valueOf(resource.getFilename())))
                    .map(this::ingest)
                    .toList();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("실습 규정 문서를 읽을 수 없습니다.", error);
        }
    }

    private synchronized IngestResult ingest(Resource resource) {
        String source = sourceName(resource);
        HelpDeskProperties.Ingest options = properties.ingest();
        String version = options.version();
        List<Document> raw = new TikaDocumentReader(resource).get();
        List<Document> chunks = TokenTextSplitter.builder()
                .withChunkSize(options.chunkSize())
                .withMinChunkSizeChars(options.minChunkSizeChars())
                .withKeepSeparator(true)
                .build()
                .apply(raw);

        List<Document> indexed = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            Document chunk = chunks.get(index);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("source", source);
            metadata.put("version", version);
            metadata.put("chunk", index);
            indexed.add(new Document(stableId(source, index, chunk.getText()), chunk.getText(), metadata));
        }

        List<String> previous = indexedIds.getOrDefault(source, List.of());
        if (!previous.isEmpty()) vectorStore.delete(previous);
        vectorStore.add(indexed);
        indexedIds.put(source, indexed.stream().map(Document::getId).toList());
        return new IngestResult(
                source, version, indexed.size(), options.chunkSize(), options.minChunkSizeChars());
    }

    /** 성공 메시지가 아니라 실제 검색 결과·유사도·메타데이터를 확인한다. */
    public List<ChunkView> inspect(String query, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("검색어는 비어 있을 수 없습니다.");
        }
        if (topK < 1 || topK > 20) {
            throw new IllegalArgumentException("topK는 1~20이어야 합니다.");
        }
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
        if (hits == null) return List.of();
        return hits.stream()
                .map(document -> new ChunkView(
                        metadata(document, "source"),
                        metadata(document, "version"),
                        Integer.parseInt(metadata(document, "chunk")),
                        document.getScore() == null ? 0.0 : document.getScore(),
                        preview(document.getText())))
                .toList();
    }

    private String sourceName(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) return "unknown";
        int extension = filename.lastIndexOf('.');
        return extension > 0 ? filename.substring(0, extension) : filename;
    }

    private String stableId(String source, int index, String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((source + ':' + index + ':' + text).getBytes(StandardCharsets.UTF_8));
            return source + '-' + HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", error);
        }
    }

    private String metadata(Document document, String key) {
        return String.valueOf(document.getMetadata().getOrDefault(key, key.equals("chunk") ? "0" : "unknown"));
    }

    private String preview(String text) {
        String compact = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return compact.length() <= 200 ? compact : compact.substring(0, 200) + "…";
    }
}
