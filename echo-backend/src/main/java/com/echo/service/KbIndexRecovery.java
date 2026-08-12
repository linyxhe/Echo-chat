package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.echo.mapper.KbDocumentMapper;
import com.echo.pojo.KbDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库索引断点恢复：应用启动完成后，把卡在 PENDING/INDEXING 的文档交给
 * KbIndexWorker.recoverDocument 从已存 content 重建索引（进程重启不丢在途索引）。
 */
@Component
public class KbIndexRecovery {

    private static final Logger log = LoggerFactory.getLogger(KbIndexRecovery.class);

    private final KbDocumentMapper documentMapper;
    private final KbIndexWorker kbIndexWorker;

    @Autowired
    public KbIndexRecovery(KbDocumentMapper documentMapper, KbIndexWorker kbIndexWorker) {
        this.documentMapper = documentMapper;
        this.kbIndexWorker = kbIndexWorker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        try {
            List<KbDocument> stuck = documentMapper.selectList(new QueryWrapper<KbDocument>()
                    .in("status", "PENDING", "INDEXING"));
            if (stuck.isEmpty()) return;
            log.info("知识库索引恢复：发现 {} 个中断文档，开始重建", stuck.size());
            for (KbDocument d : stuck) {
                kbIndexWorker.recoverDocument(d.getId());
            }
        } catch (Exception e) {
            log.error("知识库索引恢复失败", e);
        }
    }
}
