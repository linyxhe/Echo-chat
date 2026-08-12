package com.echo.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.KbChunkMapper;
import com.echo.mapper.KbDocumentMapper;
import com.echo.pojo.KbChunk;
import com.echo.pojo.KbDocument;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库（RAG）：本地中文嵌入（bge-small-zh-v1.5，512 维，无 API key）分块向量化，
 * 向量与文本持久化到 kb_document / kb_chunk，内存缓存余弦检索。
 *
 * <p>检索时 query 前加 BGE 中文指令前缀（bge 为不对称模型：query 侧加指令，passage 侧不加）。</p>
 */
@Service
public class KbService {

    private static final Logger log = LoggerFactory.getLogger(KbService.class);

    /** BGE 中文检索指令：检索 query 嵌入前须加此前缀。 */
    private static final String BGE_QUERY_INSTRUCTION = "为这个句子生成表示以用于检索相关文章：";

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;

    @Value("${app.ai.kb.enabled:true}")
    private boolean enabled;

    @Value("${app.ai.kb.chunk-size:500}")
    private int chunkSize;

    @Value("${app.ai.kb.chunk-overlap:50}")
    private int chunkOverlap;

    @Value("${app.ai.kb.top-k:4}")
    private int topK;

    @Value("${app.ai.kb.min-score:0.55}")
    private double minScore;

    @Value("${app.ai.kb.max-chunks-per-document:2}")
    private int maxChunksPerDocument;

    @Value("${app.ai.kb.semantic-fallback-score:0.68}")
    private double semanticFallbackScore;

    @Value("${app.ai.kb.max-doc-size:1048576}")
    private long maxDocSize;

    private volatile EmbeddingModel embeddingModel;
    private volatile List<ChunkVec> vecCache;

    /** 面向 RAG 调用方的检索命中，保留可展示的来源元数据。 */
    public record SearchHit(long chunkId, long documentId, String content, String filename,
                            String category, double score, boolean privateDocument) {}

    private record DocumentMeta(String filename, String category, boolean aiEnabled,
                                Long ownerId, Long assistantId) {}
    private record ChunkVec(long chunkId, long documentId, String content, float[] vec,
                            String filename, String category, boolean aiEnabled,
                            Long ownerId, Long assistantId) {}
    private record Scored(ChunkVec v, double score) {}

    @Autowired
    public KbService(KbDocumentMapper documentMapper, KbChunkMapper chunkMapper) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 懒加载嵌入模型（构造时加载 ONNX，首次较慢）。 */
    private EmbeddingModel model() {
        if (embeddingModel == null) {
            synchronized (this) {
                if (embeddingModel == null) {
                    embeddingModel = new BgeSmallZhV15EmbeddingModel();
                }
            }
        }
        return embeddingModel;
    }

    public long getDocumentCount() {
        return documentMapper.selectCount(null);
    }

    public long getChunkCount() {
        return chunkMapper.selectCount(null);
    }

    public String getModelName() {
        return "bge-small-zh-v1.5（本地 512 维）";
    }

    /** 返回可供用户创建 AI 助手选择的已就绪知识库分类。 */
    public List<String> listCategories() {
        return documentMapper.selectList(new QueryWrapper<KbDocument>()
                        .select("distinct category")
                        .eq("status", "READY")
                        .eq("ai_enabled", true)
                        .isNotNull("category")
                        .ne("category", "")
                        .orderByAsc("category"))
                .stream()
                .map(KbDocument::getCategory)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    /** 管理员使用的全部已就绪分类（包含仅管理员可见的分类）。 */
    public List<String> listAllCategories() {
        return documentMapper.selectList(new QueryWrapper<KbDocument>()
                        .select("distinct category")
                        .eq("status", "READY")
                        .isNotNull("category")
                        .ne("category", "")
                        .orderByAsc("category"))
                .stream()
                .map(KbDocument::getCategory)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    /** 文档列表（可选按文件名/分类过滤；列表瘦身，不返回 content 大文本）。 */
    public List<KbDocument> listDocuments(String keyword) {
        QueryWrapper<KbDocument> qw = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like("filename", keyword).or().like("category", keyword));
        }
        qw.orderByDesc("created_at");
        List<KbDocument> list = documentMapper.selectList(qw);
        for (KbDocument d : list) {
            d.setContent(null);
        }
        return list;
    }

    /** 完整文档（含 content，供预览）。 */
    public KbDocument getDocument(Long id) {
        return documentMapper.selectById(id);
    }

    /** 更新文档分类。 */
    public boolean updateCategory(Long id, String category) {
        return updateAccess(id, category, null);
    }

    public boolean updateAccess(Long id, String category, Boolean aiEnabled) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) return false;
        doc.setCategory(StringUtils.hasText(category) ? category.trim() : null);
        if (aiEnabled != null) doc.setAiEnabled(aiEnabled);
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);
        vecCache = null;
        return true;
    }

    public boolean deleteDocument(Long id) {
        chunkMapper.delete(new QueryWrapper<KbChunk>().eq("document_id", id));
        int del = documentMapper.deleteById(id);
        if (del > 0) {
            vecCache = null;
        }
        return del > 0;
    }

    public List<KbDocument> listPrivateDocuments(Long ownerId, Long assistantId) {
        if (ownerId == null || assistantId == null) return List.of();
        List<KbDocument> list = documentMapper.selectList(new QueryWrapper<KbDocument>()
                .eq("owner_id", ownerId)
                .eq("assistant_id", assistantId)
                .orderByDesc("created_at"));
        for (KbDocument d : list) d.setContent(null);
        return list;
    }

    public boolean deletePrivateDocument(Long ownerId, Long assistantId, Long documentId) {
        if (ownerId == null || assistantId == null || documentId == null) return false;
        KbDocument doc = documentMapper.selectOne(new QueryWrapper<KbDocument>()
                .eq("id", documentId)
                .eq("owner_id", ownerId)
                .eq("assistant_id", assistantId));
        return doc != null && deleteDocument(documentId);
    }

    private static final List<String> SUPPORTED_EXT = List.of("txt", "md", "markdown", "text", "pdf", "docx");

    /** 提交上传：校验格式并插入 PENDING 文档，随后由 KbIndexWorker 异步索引（不阻塞请求）。 */
    public KbDocument submitUpload(String filename, String contentType, String category, byte[] bytes, Long adminId) {
        return submitUpload(filename, contentType, category, true, bytes, adminId);
    }

    public KbDocument submitUpload(String filename, String contentType, String category, boolean aiEnabled,
                                   byte[] bytes, Long adminId) {
        if (!enabled) throw new IllegalStateException("知识库未启用");
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("文件为空");
        if (!SUPPORTED_EXT.contains(extensionOf(filename))) {
            throw new IllegalArgumentException("仅支持 .txt/.md/.pdf/.docx");
        }
        KbDocument doc = new KbDocument();
        doc.setFilename(filename);
        doc.setContentType(contentType);
        doc.setCategory(StringUtils.hasText(category) ? category.trim() : null);
        doc.setAiEnabled(aiEnabled);
        doc.setChunkCount(0);
        doc.setStatus("PENDING");
        doc.setCreatedBy(adminId);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(doc);
        return doc;
    }

    public KbDocument submitPrivateUpload(String filename, String contentType, byte[] bytes,
                                          Long ownerId, Long assistantId) {
        if (!enabled) throw new IllegalStateException("知识库未启用");
        if (ownerId == null || assistantId == null) throw new IllegalArgumentException("助手信息无效");
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("文件为空");
        if (!SUPPORTED_EXT.contains(extensionOf(filename))) {
            throw new IllegalArgumentException("仅支持 .txt/.md/.pdf/.docx");
        }
        KbDocument doc = new KbDocument();
        doc.setFilename(filename);
        doc.setContentType(contentType);
        doc.setCategory(null);
        doc.setAiEnabled(true);
        doc.setChunkCount(0);
        doc.setStatus("PENDING");
        doc.setCreatedBy(ownerId);
        doc.setOwnerId(ownerId);
        doc.setAssistantId(assistantId);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(doc);
        return doc;
    }

    /** 按扩展名抽取文本。PDF 扫描件/无文本层、docx 损坏等会抛异常。 */
    public String extractText(String filename, byte[] bytes) throws Exception {
        String ext = extensionOf(filename);
        switch (ext) {
            case "txt":
            case "md":
            case "markdown":
            case "text":
                return new String(bytes, StandardCharsets.UTF_8);
            case "pdf":
                try (PDDocument doc = Loader.loadPDF(bytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setSortByPosition(true); // 改善多栏/表格阅读顺序
                    return stripper.getText(doc);
                }
            case "docx":
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                    XWPFWordExtractor ex = new XWPFWordExtractor(doc);
                    try {
                        return ex.getText();
                    } finally {
                        ex.close();
                    }
                }
            default:
                throw new IllegalArgumentException("不支持的文件类型：" + ext);
        }
    }

    /** 分块 + 嵌入 + 落库，成功后置 READY（失败抛异常，由 KbIndexWorker 置 FAILED）。 */
    public void indexText(Long docId, String text) throws Exception {
        if (!StringUtils.hasText(text)) throw new IllegalArgumentException("无法从文档提取文本（可能为空或扫描件）");
        if (text.getBytes(StandardCharsets.UTF_8).length > maxDocSize) {
            throw new IllegalArgumentException("文档文本超过大小上限（" + (maxDocSize / 1024 / 1024) + "MB）");
        }
        EmbeddingModel model = model();
        List<String> chunks = splitText(text, chunkSize, chunkOverlap);
        if (chunks.isEmpty()) throw new IllegalArgumentException("文档无可索引内容");

        List<TextSegment> segments = chunks.stream().map(TextSegment::from).collect(Collectors.toList());
        Response<List<Embedding>> resp = model.embedAll(segments);
        List<Embedding> embeddings = resp.content();
        int count = 0;
        for (int i = 0; i < chunks.size() && i < embeddings.size(); i++) {
            KbChunk c = new KbChunk();
            c.setDocumentId(docId);
            c.setChunkIndex(i);
            c.setContent(chunks.get(i));
            c.setEmbedding(JSON.toJSONString(embeddings.get(i).vector()));
            c.setCreatedAt(LocalDateTime.now());
            chunkMapper.insert(c);
            count++;
        }
        if (count == 0) throw new IllegalArgumentException("索引失败：未生成任何分片");

        KbDocument doc = documentMapper.selectById(docId);
        if (doc != null) {
            doc.setContent(text); // 持久化抽取文本（预览 + 断点恢复依赖）
            doc.setChunkCount(count);
            doc.setStatus("READY");
            doc.setErrorMessage(null);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
        }
        vecCache = null;
    }

    /** 标记索引失败（KbIndexWorker 调用）；同时清该文档残留分片，避免 FAILED 半成品被检索。 */
    public void markFailed(Long docId, String msg) {
        try {
            chunkMapper.delete(new QueryWrapper<KbChunk>().eq("document_id", docId));
            KbDocument doc = documentMapper.selectById(docId);
            if (doc != null) {
                doc.setChunkCount(0);
                doc.setStatus("FAILED");
                doc.setErrorMessage(msg != null && msg.length() > 1000 ? msg.substring(0, 1000) : msg);
                doc.setUpdatedAt(LocalDateTime.now());
                documentMapper.updateById(doc);
            }
            vecCache = null;
        } catch (Exception e) {
            log.error("标记知识库文档失败 doc=" + docId, e);
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /** 检索知识库，返回按相关度降序的片段文本（低于 minScore 不返回）。 */
    public List<String> search(String query, int topK, double minScore) {
        return searchHits(query, (Collection<String>) null, topK, minScore).stream()
                .map(SearchHit::content)
                .collect(Collectors.toList());
    }

    /** 按可选分类检索知识库；分类为空时保持全库检索。 */
    public List<String> search(String query, String category, int topK, double minScore) {
        return searchHits(query, category, topK, minScore).stream()
                .map(SearchHit::content)
                .collect(Collectors.toList());
    }

    /** 带来源元数据的知识库检索，供 AI Gateway、预览和后续对话游戏使用。 */
    public List<SearchHit> searchHits(String query, String category, int topK, double minScore) {
        return searchHits(query, category == null ? null : List.of(category), null, null, topK, minScore);
    }

    public List<SearchHit> searchHits(String query, Collection<String> categories, int topK, double minScore) {
        return searchHits(query, categories, null, null, topK, minScore);
    }

    /** 检索公共文档 + 当前助手私有文档；私有文档不受公共分类选择限制。 */
    public List<SearchHit> searchHits(String query, Collection<String> categories,
                                      Long ownerId, Long assistantId, int topK, double minScore) {
        if (!enabled || !StringUtils.hasText(query)) return List.of();
        try {
            List<ChunkVec> vecs = vectors();
            if (vecs.isEmpty()) return List.of();
            Set<String> normalizedCategories = categories == null ? Set.of() : categories.stream()
                    .filter(StringUtils::hasText)
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            EmbeddingModel model = model();
            float[] qv = model.embed(BGE_QUERY_INSTRUCTION + query).content().vector();

            List<Scored> scored = new ArrayList<>();
            for (ChunkVec v : vecs) {
                if (!v.aiEnabled()) continue;
                boolean privateDocument = ownerId != null && assistantId != null
                        && ownerId.equals(v.ownerId()) && assistantId.equals(v.assistantId());
                boolean publicDocument = v.ownerId() == null && v.assistantId() == null;
                if (!publicDocument && !privateDocument) continue;
                if (publicDocument && !normalizedCategories.isEmpty()
                        && !normalizedCategories.contains(String.valueOf(v.category()).trim().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                scored.add(new Scored(v, cosine(qv, v.vec())));
            }
            scored.sort((a, b) -> Double.compare(b.score(), a.score()));

            List<SearchHit> out = new ArrayList<>();
            Map<Long, Integer> chunksByDocument = new HashMap<>();
            Set<String> seenChunks = new HashSet<>();
            int perDocumentLimit = Math.max(maxChunksPerDocument, 1);
            for (Scored s : scored) {
                if (s.score() < minScore) break;
                ChunkVec v = s.v();
                // 没有任何词面锚点时要求更高的语义分数，拦截“泛相似”误召回。
                if (!hasLexicalAnchor(query, v.content()) && s.score() < semanticFallbackScore) continue;
                int documentCount = chunksByDocument.getOrDefault(v.documentId(), 0);
                if (documentCount >= perDocumentLimit) continue;
                String dedupeKey = v.documentId() + "\n" + v.content().trim();
                if (!seenChunks.add(dedupeKey)) continue;
                boolean privateDocument = v.ownerId() != null && v.assistantId() != null;
                out.add(new SearchHit(v.chunkId(), v.documentId(), v.content(), v.filename(), v.category(), s.score(), privateDocument));
                chunksByDocument.put(v.documentId(), documentCount + 1);
                if (out.size() >= topK) break;
            }
            return out;
        } catch (Exception e) {
            log.error("知识库检索失败", e);
            return List.of();
        }
    }

    /**
     * 中文按连续二字片段、英文按完整词做轻量词面校验；仅用于降低误召回，不能替代向量检索。
     */
    private boolean hasLexicalAnchor(String query, String content) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(content)) return false;
        String q = query.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\u4e00-\\u9fff]", "");
        String c = content.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\u4e00-\\u9fff]", "");
        if (q.length() < 2 || c.length() < 2) return false;
        for (int i = 0; i < q.length() - 1; i++) {
            if (c.contains(q.substring(i, i + 2))) return true;
        }
        String[] words = query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+", -1);
        for (String word : words) {
            if (word.length() >= 2 && c.contains(word)) return true;
        }
        return false;
    }

    public List<String> search(String query) {
        return search(query, null, topK, minScore);
    }

    public List<String> search(String query, String category) {
        return search(query, category, topK, minScore);
    }

    public List<SearchHit> searchHits(String query, String category) {
        return searchHits(query, category == null ? null : List.of(category), null, null, topK, minScore);
    }

    public List<SearchHit> searchHits(String query, Collection<String> categories) {
        return searchHits(query, categories, topK, minScore);
    }

    public List<SearchHit> searchHits(String query, Collection<String> categories,
                                      Long ownerId, Long assistantId) {
        return searchHits(query, categories, ownerId, assistantId, topK, minScore);
    }

    /** 加载全部分片向量（缓存；ingest/delete 后失效）。 */
    private List<ChunkVec> vectors() {
        List<ChunkVec> cached = vecCache;
        if (cached != null) return cached;
        synchronized (this) {
            cached = vecCache;
            if (cached == null) {
                Map<Long, DocumentMeta> metaByDocument = new HashMap<>();
                for (KbDocument document : documentMapper.selectList(
                        new QueryWrapper<KbDocument>().select("id", "filename", "category", "ai_enabled", "owner_id", "assistant_id"))) {
                    metaByDocument.put(document.getId(), new DocumentMeta(document.getFilename(), document.getCategory(),
                            !Boolean.FALSE.equals(document.getAiEnabled()), document.getOwnerId(), document.getAssistantId()));
                }
                List<KbChunk> rows = chunkMapper.selectList(
                        new QueryWrapper<KbChunk>().orderByAsc("document_id", "chunk_index"));
                List<ChunkVec> list = new ArrayList<>();
                for (KbChunk r : rows) {
                    try {
                        List<Float> floats = JSON.parseArray(r.getEmbedding(), Float.class);
                        float[] v = new float[floats.size()];
                        for (int i = 0; i < v.length; i++) v[i] = floats.get(i);
                        DocumentMeta meta = metaByDocument.get(r.getDocumentId());
                        list.add(new ChunkVec(r.getId(), r.getDocumentId(), r.getContent(), v,
                                meta == null ? null : meta.filename(),
                                meta == null ? null : meta.category(),
                                meta == null || meta.aiEnabled(),
                                meta == null ? null : meta.ownerId(),
                                meta == null ? null : meta.assistantId()));
                    } catch (Exception ignored) {
                        // 跳过解析失败的分片
                    }
                }
                vecCache = list;
                return list;
            }
            return cached;
        }
    }

    /** 简易字符级分块：优先在换行/句末断点切分，带重叠。 */
    private List<String> splitText(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        String t = text.replace("\r\n", "\n").trim();
        if (t.isEmpty()) return chunks;
        int start = 0;
        while (start < t.length()) {
            int end = Math.min(start + size, t.length());
            if (end < t.length()) {
                int cut = findBreak(t, start, end);
                if (cut > start) end = cut;
            }
            chunks.add(t.substring(start, end).trim());
            if (end >= t.length()) break;
            start = Math.max(0, end - overlap);
        }
        return chunks;
    }

    private int findBreak(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '；' || c == '，' || c == ' ') {
                return i + 1;
            }
        }
        return -1;
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
