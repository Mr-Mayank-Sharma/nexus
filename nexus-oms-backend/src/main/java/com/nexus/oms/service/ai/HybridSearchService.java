package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Hybrid search service combining:
 * 1. Dense search (pgvector cosine similarity)
 * 2. Sparse search (PostgreSQL full-text search)
 * 3. Reciprocal Rank Fusion (RRF) reranking
 */
@Service
public class HybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);
    private static final int DEFAULT_TOP_K = 10;
    private static final double DENSE_WEIGHT = 0.6;
    private static final double SPARSE_WEIGHT = 0.4;

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public HybridSearchService(
            JdbcTemplate jdbcTemplate,
            @Value("${nexus.ai.openai.api-key:}") String apiKey,
            @Value("${nexus.ai.openai.model:gpt-4o}") String model) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();
    }

    /**
     * Hybrid search: combines dense + sparse + RRF reranking.
     */
    public List<Map<String, Object>> search(String query, UUID tenantId, int topK) {
        if (!enabled) {
            log.info("OpenAI not configured, falling back to sparse-only search");
            return sparseSearch(query, tenantId, topK);
        }

        try {
            // 1. Generate embedding for the query
            float[] queryEmbedding = generateEmbedding(query);

            // 2. Dense search (pgvector cosine similarity)
            List<Map<String, Object>> denseResults = denseSearch(queryEmbedding, tenantId, topK * 2);

            // 3. Sparse search (full-text search)
            List<Map<String, Object>> sparseResults = sparseSearch(query, tenantId, topK * 2);

            // 4. Reciprocal Rank Fusion (RRF)
            return reciprocalRankFusion(denseResults, sparseResults, topK);

        } catch (Exception e) {
            log.error("Hybrid search failed, falling back to sparse: {}", e.getMessage());
            return sparseSearch(query, tenantId, topK);
        }
    }

    /**
     * Dense search using pgvector cosine similarity.
     */
    private List<Map<String, Object>> denseSearch(float[] embedding, UUID tenantId, int limit) {
        try {
            // Convert embedding to PostgreSQL vector format
            String vectorStr = Arrays.toString(embedding)
                .replace("[", "[")
                .replace("]", "]");

            return jdbcTemplate.queryForList(
                "SELECT id, title, content, source_type, source_id, metadata_json, " +
                "1 - (embedding <=> ?::vector) as similarity " +
                "FROM ai_rag_documents " +
                "WHERE tenant_id = ? " +
                "ORDER BY embedding <=> ?::vector " +
                "LIMIT ?",
                vectorStr, tenantId, vectorStr, limit);
        } catch (Exception e) {
            log.warn("Dense search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Sparse search using PostgreSQL full-text search.
     */
    private List<Map<String, Object>> sparseSearch(String query, UUID tenantId, int limit) {
        try {
            // Build tsquery from user input
            String tsQuery = Arrays.stream(query.split("\\s+"))
                .filter(w -> w.length() > 2)
                .map(w -> w + ":*")
                .reduce((a, b) -> a + " & " + b)
                .orElse(query);

            return jdbcTemplate.queryForList(
                "SELECT id, title, content, source_type, source_id, metadata_json, " +
                "ts_rank(to_tsvector('english', coalesce(title, '') || ' ' || coalesce(content, '')), " +
                "to_tsquery('english', ?)) as similarity " +
                "FROM ai_rag_documents " +
                "WHERE tenant_id = ? " +
                "AND to_tsvector('english', coalesce(title, '') || ' ' || coalesce(content, '')) " +
                "@@ to_tsquery('english', ?) " +
                "ORDER BY similarity DESC " +
                "LIMIT ?",
                tsQuery, tenantId, tsQuery, limit);
        } catch (Exception e) {
            log.warn("Sparse search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Reciprocal Rank Fusion: merges dense and sparse result lists.
     * RRF score = Σ (weight / (k + rank)) where k=60 (standard constant).
     */
    private List<Map<String, Object>> reciprocalRankFusion(
            List<Map<String, Object>> denseResults,
            List<Map<String, Object>> sparseResults,
            int topK) {

        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();
        int k = 60; // RRF constant

        // Dense results
        for (int i = 0; i < denseResults.size(); i++) {
            Map<String, Object> doc = denseResults.get(i);
            String id = doc.get("id").toString();
            double rrfScore = DENSE_WEIGHT / (k + i + 1);
            merged.put(id, doc);
            scores.merge(id, rrfScore, Double::sum);
        }

        // Sparse results
        for (int i = 0; i < sparseResults.size(); i++) {
            Map<String, Object> doc = sparseResults.get(i);
            String id = doc.get("id").toString();
            double rrfScore = SPARSE_WEIGHT / (k + i + 1);
            merged.putIfAbsent(id, doc);
            scores.merge(id, rrfScore, Double::sum);
        }

        // Sort by combined RRF score
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> {
                Map<String, Object> doc = new LinkedHashMap<>(merged.get(entry.getKey()));
                doc.put("rrfScore", Math.round(entry.getValue() * 100000.0) / 100000.0);
                return doc;
            })
            .toList();
    }

    /**
     * Generate OpenAI embedding for a text string.
     * Uses text-embedding-3-small (1536 dimensions).
     */
    float[] generateEmbedding(String text) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "text-embedding-3-small");
            body.put("input", text);

            var headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            var entity = new org.springframework.http.HttpEntity<>(body, headers);

            var response = restTemplate.exchange(
                "https://api.openai.com/v1/embeddings",
                org.springframework.http.HttpMethod.POST,
                entity,
                String.class);

            JsonNode json = objectMapper.readTree(response.getBody());
            JsonNode embeddingData = json.get("data").get(0).get("embedding");

            float[] embedding = new float[embeddingData.size()];
            for (int i = 0; i < embeddingData.size(); i++) {
                embedding[i] = (float) embeddingData.get(i).asDouble();
            }
            return embedding;

        } catch (Exception e) {
            log.error("Failed to generate embedding: {}", e.getMessage());
            throw new RuntimeException("Embedding generation failed", e);
        }
    }

    /**
     * Index a document into the RAG store.
     */
    public void indexDocument(UUID tenantId, String sourceType, String sourceId,
                               String title, String content, Map<String, Object> metadata) {
        if (!enabled) {
            log.info("OpenAI not configured, indexing without embedding");
            indexWithoutEmbedding(tenantId, sourceType, sourceId, title, content, metadata);
            return;
        }

        try {
            float[] embedding = generateEmbedding(content);
            String vectorStr = Arrays.toString(embedding);

            jdbcTemplate.update(
                "INSERT INTO ai_rag_documents (tenant_id, source_type, source_id, title, content, embedding, metadata_json, token_count) " +
                "VALUES (?, ?, ?, ?, ?, ?::vector, ?::jsonb, ?) " +
                "ON CONFLICT (tenant_id, source_type, source_id) DO UPDATE SET " +
                "title = EXCLUDED.title, content = EXCLUDED.content, embedding = EXCLUDED.embedding, " +
                "metadata_json = EXCLUDED.metadata_json, updated_at = NOW()",
                tenantId, sourceType, sourceId, title, content,
                vectorStr, objectMapper.writeValueAsString(metadata),
                estimateTokenCount(content));

            log.debug("Indexed document: {}/{}", sourceType, sourceId);

        } catch (Exception e) {
            log.error("Failed to index document {}/{}: {}", sourceType, sourceId, e.getMessage());
        }
    }

    private void indexWithoutEmbedding(UUID tenantId, String sourceType, String sourceId,
                                        String title, String content, Map<String, Object> metadata) {
        try {
            jdbcTemplate.update(
                "INSERT INTO ai_rag_documents (tenant_id, source_type, source_id, title, content, metadata_json, token_count) " +
                "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?) " +
                "ON CONFLICT (tenant_id, source_type, source_id) DO UPDATE SET " +
                "title = EXCLUDED.title, content = EXCLUDED.content, metadata_json = EXCLUDED.metadata_json, updated_at = NOW()",
                tenantId, sourceType, sourceId, title, content,
                objectMapper.writeValueAsString(metadata), estimateTokenCount(content));
        } catch (Exception e) {
            log.warn("Failed to index document without embedding: {}", e.getMessage());
        }
    }

    private int estimateTokenCount(String text) {
        if (text == null) return 0;
        return text.split("\\s+").length;
    }
}
