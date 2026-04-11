package com.bvz.aicodegenerator.service;

import com.bvz.aicodegenerator.model.dto.chatHistory.ChatHistoryAdminQueryRequest;
import com.bvz.aicodegenerator.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.bvz.aicodegenerator.model.entity.ChatHistory;
import com.bvz.aicodegenerator.model.entity.User;
import com.bvz.aicodegenerator.model.vo.ChatHistoryPageVO;
import com.bvz.aicodegenerator.model.vo.ChatHistoryVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.List;

/**
 * 对话历史 服务层
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 保存用户消息
     */
    void saveUserMessage(Long appId, Long userId, String message);

    /**
     * 保存 AI 成功消息
     */
    void saveAiMessage(Long appId, Long userId, String message);

    /**
     * 保存 AI 失败消息
     */
    void saveAiErrorMessage(Long appId, Long userId, String message, String errorMessage);

    /**
     * 查询某个应用的聊天记录
     */
    ChatHistoryPageVO listAppChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest, User loginUser);

    /**
     * 管理员分页查询所有聊天记录
     */
    ChatHistoryPageVO listChatHistoryVOByPageByAdmin(ChatHistoryAdminQueryRequest chatHistoryAdminQueryRequest);

    /**
     * 删除某个应用下的所有聊天记录
     */
    void removeByAppId(Long appId);

    /**
     * 加载某个应用下的聊天记录到内存
     * @param appId
     * @param chatMemory
     * @param maxCount 最多加载多少条
     * @return 加载成功的条数
     */
    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 获取管理员查询条件
     */
    QueryWrapper getQueryWrapper(ChatHistoryAdminQueryRequest chatHistoryAdminQueryRequest);

    /**
     * 获取聊天记录封装
     */
    ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory);

    /**
     * 获取聊天记录封装列表
     */
    List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList);
}
