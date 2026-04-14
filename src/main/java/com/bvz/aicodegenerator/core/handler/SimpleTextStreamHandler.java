package com.bvz.aicodegenerator.core.handler;

import cn.hutool.core.util.StrUtil;
import com.bvz.aicodegenerator.model.entity.User;
import com.bvz.aicodegenerator.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 简单文本流处理器
 * 处理 HTML 和 MULTI_FILE 类型的流式响应
 */
@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理传统流（HTML, MULTI_FILE）
     * 直接收集完整的文本响应
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        StringBuilder aiReplyBuilder = new StringBuilder();
        return originFlux
                .doOnNext(aiReplyBuilder::append)
                .doOnComplete(() -> {
                    // 流式响应完成后，添加AI消息到对话历史
                    String aiResponse = aiReplyBuilder.toString();
                    chatHistoryService.saveAiMessage(appId, loginUser.getId(), aiResponse);
                })
                .doOnError(throwable -> {
                    // 如果AI回复失败，也要记录错误消息
                    String aiResponse = aiReplyBuilder.toString();
                    String errorMessage = StrUtil.blankToDefault(throwable.getMessage(), "AI 回复失败");
                    chatHistoryService.saveAiErrorMessage(appId, loginUser.getId(), aiResponse, errorMessage);
                });

    }

}
