package com.echo.ai;

import com.echo.pojo.Message;

/**
 * AI 聊天：接收用户发给 bot 的消息，调用 LLM 流式生成回复并推送回发送者。
 */
public interface AiChatService {

    /**
     * 处理一条发给 AI 助手的用户消息（幂等；非阻塞，流式回调在 langchain4j 线程上执行）。
     *
     * @param userId   提问的人类用户 id
     * @param userMsg  已持久化的用户消息
     * @param streamId 前端用于关联流式气泡的键（一般取用户消息的 clientMessageId）
     */
    void handleUserMessage(Long userId, Message userMsg, String streamId);

    /** 协作式停止一个正在生成的流。 */
    void cancel(Long userId, String streamId);
}
