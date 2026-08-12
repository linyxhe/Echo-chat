package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.KbChunkMapper;
import com.echo.mapper.KbDocumentMapper;
import com.echo.pojo.KbChunk;
import com.echo.pojo.KbDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库异步索引任务：PENDING → INDEXING → READY / FAILED。
 *
 * <p>注意：{@code @Async} 方法必须由另一个 bean 调用（自调用会绕过代理同步执行），
 * 故由 KbController 触发，而不是 KbService 内部调用。复刻 FileFinalizationService 的
 * 去重 Set + 状态机 + finally 清理模式。</p>
 */
@Service
public class KbIndexWorker {

    private static final Logger log = LoggerFactory.getLogger(KbIndexWorker.class);

    /** 进行中的文档 id，避免同一文档被重复触发。 */
    private final Set<Long> indexing = ConcurrentHashMap.newKeySet();

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final KbService kbService;

    @Autowired
    public KbIndexWorker(KbDocumentMapper documentMapper, KbChunkMapper chunkMapper, KbService kbService) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.kbService = kbService;
    }

    @Async
    public void indexDocument(Long docId, byte[] bytes, String filename, String contentType) {
        if (docId == null) return;
        if (!indexing.add(docId)) return; // 去重

        try {
            KbDocument doc = documentMapper.selectById(docId);
            if (doc == null || !"PENDING".equals(doc.getStatus())) return;

            doc.setStatus("INDEXING");
            doc.setErrorMessage(null);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);

            String text = kbService.extractText(filename, bytes);
            if (!StringUtils.hasText(text)) {
                kbService.markFailed(docId, "无法从文档提取文本（可能为空或扫描件）");
                return;
            }
            kbService.indexText(docId, text);
        } catch (Exception e) {
            log.error("知识库异步索引失败 doc=" + docId, e);
            kbService.markFailed(docId, e.getMessage());
        } finally {
            indexing.remove(docId);
        }
    }

    /**
     * 断点恢复：进程重启后重新索引卡在 PENDING/INDEXING 的文档（复用已存 content，不读原始文件）。
     * 先清该文档残留分片再重建，避免唯一键冲突与半成品污染。
     */
    @Async
    public void recoverDocument(Long docId) {
        if (docId == null) return;
        if (!indexing.add(docId)) return; // 去重

        try {
            KbDocument doc = documentMapper.selectById(docId);
            if (doc == null) return;
            String status = doc.getStatus();
            if (!"PENDING".equals(status) && !"INDEXING".equals(status)) return;

            chunkMapper.delete(new QueryWrapper<KbChunk>().eq("document_id", docId));
            doc.setStatus("INDEXING");
            doc.setErrorMessage(null);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);

            kbService.indexText(docId, doc.getContent());
        } catch (Exception e) {
            log.error("知识库索引恢复失败 doc=" + docId, e);
            kbService.markFailed(docId, "索引恢复失败：" + e.getMessage());
        } finally {
            indexing.remove(docId);
        }
    }
}
