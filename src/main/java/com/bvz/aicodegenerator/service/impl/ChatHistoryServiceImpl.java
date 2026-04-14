package com.bvz.aicodegenerator.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.bvz.aicodegenerator.constant.ChatHistoryConstant;
import com.bvz.aicodegenerator.exception.BusinessException;
import com.bvz.aicodegenerator.exception.ErrorCode;
import com.bvz.aicodegenerator.exception.ThrowUtils;
import com.bvz.aicodegenerator.mapper.AppMapper;
import com.bvz.aicodegenerator.mapper.ChatHistoryMapper;
import com.bvz.aicodegenerator.model.dto.chatHistory.ChatHistoryAdminQueryRequest;
import com.bvz.aicodegenerator.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.bvz.aicodegenerator.model.entity.App;
import com.bvz.aicodegenerator.model.entity.ChatHistory;
import com.bvz.aicodegenerator.model.entity.User;
import com.bvz.aicodegenerator.model.enums.MessageStatusEnum;
import com.bvz.aicodegenerator.model.enums.MessageTypeEnum;
import com.bvz.aicodegenerator.model.enums.UserRoleEnum;
import com.bvz.aicodegenerator.model.vo.ChatHistoryPageVO;
import com.bvz.aicodegenerator.model.vo.ChatHistoryVO;
import com.bvz.aicodegenerator.model.vo.UserVO;
import com.bvz.aicodegenerator.service.ChatHistoryService;
import com.bvz.aicodegenerator.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对话历史 服务层实现
 */
@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Resource
    private AppMapper appMapper;

    @Resource
    private UserService userService;

    @Override
    public void saveUserMessage(Long appId, Long userId, String message) {
        saveChatHistory(appId, userId, message, MessageTypeEnum.USER, MessageStatusEnum.SUCCESS, null);
    }

    @Override
    public void saveAiMessage(Long appId, Long userId, String message) {
        String finalMessage = StrUtil.blankToDefault(message, "AI 未返回内容");
        saveChatHistory(appId, userId, finalMessage, MessageTypeEnum.AI, MessageStatusEnum.SUCCESS, null);
    }

    @Override
    public void saveAiErrorMessage(Long appId, Long userId, String message, String errorMessage) {
        String finalMessage = StrUtil.blankToDefault(message, "AI 回复失败");
        saveChatHistory(appId, userId, finalMessage, MessageTypeEnum.AI, MessageStatusEnum.ERROR, errorMessage);
    }

    @Override
    public ChatHistoryPageVO listAppChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest, User loginUser) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");

        // 1. 只有应用创建者和管理员可以查看应用级聊天历史
        App app = getAccessibleApp(chatHistoryQueryRequest.getAppId(), loginUser);

        // 2. 聊天记录固定使用“时间游标 + pageSize”的查询方式
        int pageSize = normalizePageSize(chatHistoryQueryRequest.getPageSize());
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", app.getId())
                .orderBy("createTime", false);
        if (lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime);
        }

        return buildChatHistoryPageVO(queryWrapper, pageSize);
    }

    @Override
    public ChatHistoryPageVO listChatHistoryVOByPageByAdmin(ChatHistoryAdminQueryRequest chatHistoryAdminQueryRequest) {
        ThrowUtils.throwIf(chatHistoryAdminQueryRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");

        // 管理端同样使用时间游标查询，这样和聊天端的加载方式保持一致
        int pageSize = normalizePageSize(chatHistoryAdminQueryRequest.getPageSize());
        QueryWrapper queryWrapper = getQueryWrapper(chatHistoryAdminQueryRequest);
        return buildChatHistoryPageVO(queryWrapper, pageSize);
    }

    @Override
    public void removeByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 无效");
        QueryWrapper queryWrapper = QueryWrapper.create().eq("appId", appId);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count <= 0) {
            return;
        }
        boolean result = this.remove(queryWrapper);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除应用对话历史失败");
    }

    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            // 直接构造查询条件，起始点为 1 而不是 0，用于排除最新的用户消息
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .orderBy(ChatHistory::getCreateTime, false)
                    .limit(1, maxCount);
            List<ChatHistory> historyList = this.list(queryWrapper);
            if (CollUtil.isEmpty(historyList)) {
                return 0;
            }
            // 反转列表，确保按时间正序（老的在前，新的在后）
            historyList = historyList.reversed();
            // 按时间顺序添加到记忆中
            int loadedCount = 0;
            // 先清理历史缓存，防止重复加载
            chatMemory.clear();
            for (ChatHistory history : historyList) {
                if (MessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    loadedCount++;
                } else if (MessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    chatMemory.add(AiMessage.from(history.getMessage()));
                    loadedCount++;
                }
            }
            log.info("成功为 appId: {} 加载了 {} 条历史对话", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
            // 加载失败不影响系统运行，只是没有历史上下文
            return 0;
        }
    }

    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryAdminQueryRequest chatHistoryAdminQueryRequest) {
        if (chatHistoryAdminQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        String messageType = chatHistoryAdminQueryRequest.getMessageType();
        String messageStatus = chatHistoryAdminQueryRequest.getMessageStatus();
        if (StrUtil.isNotBlank(messageType) && MessageTypeEnum.getEnumByValue(messageType) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息类型错误");
        }
        if (StrUtil.isNotBlank(messageStatus) && MessageStatusEnum.getEnumByValue(messageStatus) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息状态错误");
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", chatHistoryAdminQueryRequest.getAppId())
                .eq("userId", chatHistoryAdminQueryRequest.getUserId())
                .eq("messageType", messageType)
                .eq("messageStatus", messageStatus)
                .like("message", chatHistoryAdminQueryRequest.getMessage());

        // 管理端也使用 createTime 作为时间游标，只查询更早的记录
        if (chatHistoryAdminQueryRequest.getLastCreateTime() != null) {
            queryWrapper.lt("createTime", chatHistoryAdminQueryRequest.getLastCreateTime());
        }

        return queryWrapper.orderBy("createTime", false);
    }

    @Override
    public ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory) {
        if (chatHistory == null) {
            return null;
        }
        ChatHistoryVO chatHistoryVO = new ChatHistoryVO();
        BeanUtil.copyProperties(chatHistory, chatHistoryVO);

        MessageTypeEnum messageTypeEnum = MessageTypeEnum.getEnumByValue(chatHistory.getMessageType());
        if (messageTypeEnum != null) {
            chatHistoryVO.setMessageTypeText(messageTypeEnum.getText());
        }
        MessageStatusEnum messageStatusEnum = MessageStatusEnum.getEnumByValue(chatHistory.getMessageStatus());
        if (messageStatusEnum != null) {
            chatHistoryVO.setMessageStatusText(messageStatusEnum.getText());
        }
        return chatHistoryVO;
    }

    @Override
    public List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList) {
        if (CollUtil.isEmpty(chatHistoryList)) {
            return new ArrayList<>();
        }

        // 批量查询用户和应用信息，避免在列表转换时触发 N+1 查询
        Set<Long> userIds = chatHistoryList.stream()
                .map(ChatHistory::getUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Set<Long> appIds = chatHistoryList.stream()
                .map(ChatHistory::getAppId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        Map<Long, UserVO> userVOMap = new HashMap<>();
        if (CollUtil.isNotEmpty(userIds)) {
            userVOMap = userService.listByIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, userService::getUserVO));
        }

        Map<Long, String> appNameMap = new HashMap<>();
        if (CollUtil.isNotEmpty(appIds)) {
            appNameMap = appMapper.selectListByQuery(QueryWrapper.create().in("id", appIds)).stream()
                    .collect(Collectors.toMap(App::getId, App::getAppName));
        }

        Map<Long, UserVO> finalUserVOMap = userVOMap;
        Map<Long, String> finalAppNameMap = appNameMap;
        return chatHistoryList.stream().map(chatHistory -> {
            ChatHistoryVO chatHistoryVO = getChatHistoryVO(chatHistory);
            chatHistoryVO.setUser(finalUserVOMap.get(chatHistory.getUserId()));
            chatHistoryVO.setAppName(finalAppNameMap.get(chatHistory.getAppId()));
            return chatHistoryVO;
        }).collect(Collectors.toList());
    }

    /**
     * 保存对话历史
     */
    private void saveChatHistory(
            Long appId,
            Long userId,
            String message,
            MessageTypeEnum messageTypeEnum,
            MessageStatusEnum messageStatusEnum,
            String errorMessage
    ) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 无效");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户 ID 无效");
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(messageStatusEnum == null, ErrorCode.PARAMS_ERROR, "消息状态不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message) && StrUtil.isBlank(errorMessage),
                ErrorCode.PARAMS_ERROR, "消息内容和错误信息不能同时为空");

        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .userId(userId)
                .message(message)
                .messageType(messageTypeEnum.getValue())
                .messageStatus(messageStatusEnum.getValue())
                .errorMessage(errorMessage)
                .build();
        saveChatHistoryWithFallback(chatHistory);
    }

    private void saveChatHistoryWithFallback(ChatHistory chatHistory) {
        int[] messageLimits = {Integer.MAX_VALUE, 8000, 2000, 512};
        int[] errorLimits = {Integer.MAX_VALUE, 4000, 1000, 255};
        RuntimeException lastException = null;

        for (int i = 0; i < messageLimits.length; i++) {
            ChatHistory candidate = ChatHistory.builder()
                    .appId(chatHistory.getAppId())
                    .userId(chatHistory.getUserId())
                    .message(limitText(chatHistory.getMessage(), messageLimits[i]))
                    .messageType(chatHistory.getMessageType())
                    .messageStatus(chatHistory.getMessageStatus())
                    .errorMessage(limitText(chatHistory.getErrorMessage(), errorLimits[i]))
                    .build();
            try {
                boolean result = this.save(candidate);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存对话历史失败");
                if (i > 0) {
                    log.warn("聊天记录已按降级长度保存, appId={}, userId={}, step={}",
                            candidate.getAppId(), candidate.getUserId(), i);
                }
                return;
            } catch (RuntimeException e) {
                lastException = e;
                log.warn("保存聊天记录失败，尝试降级重试, appId={}, userId={}, step={}, error={}",
                        candidate.getAppId(), candidate.getUserId(), i, e.getMessage());
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存对话历史失败");
    }

    private String limitText(String text, int maxLength) {
        if (text == null || maxLength == Integer.MAX_VALUE || text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 3) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * 获取当前用户可访问的应用
     */
    private App getAccessibleApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 无效");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        App app = appMapper.selectOneByQuery(QueryWrapper.create().eq("id", appId));
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        boolean isAdmin = UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole());
        ThrowUtils.throwIf(!isAdmin && !app.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权限查看该应用的对话历史");
        return app;
    }

    /**
     * 规范化分页大小
     */
    private int normalizePageSize(Integer pageSize) {
        int finalPageSize = pageSize == null ? ChatHistoryConstant.DEFAULT_CHAT_HISTORY_PAGE_SIZE : pageSize;
        ThrowUtils.throwIf(finalPageSize <= 0, ErrorCode.PARAMS_ERROR, "分页大小无效");
        return Math.min(finalPageSize, ChatHistoryConstant.CHAT_HISTORY_PAGE_SIZE_LIMIT);
    }

    /**
     * 统一执行“多查一条 + 判断是否还有更多 + 返回正序结果”的聊天分页逻辑
     */
    private ChatHistoryPageVO buildChatHistoryPageVO(QueryWrapper queryWrapper, int pageSize) {
        Page<ChatHistory> chatHistoryPage = this.page(Page.of(1, pageSize + 1), queryWrapper);
        List<ChatHistory> chatHistoryList = new ArrayList<>(chatHistoryPage.getRecords());

        boolean hasMore = chatHistoryList.size() > pageSize;
        if (hasMore) {
            chatHistoryList = new ArrayList<>(chatHistoryList.subList(0, pageSize));
        }

        LocalDateTime nextLastCreateTime = hasMore && CollUtil.isNotEmpty(chatHistoryList)
                ? chatHistoryList.get(chatHistoryList.size() - 1).getCreateTime()
                : null;

        // 查询时按时间倒序，返回前转成正序，前端可直接按聊天顺序渲染
        Collections.reverse(chatHistoryList);

        ChatHistoryPageVO chatHistoryPageVO = new ChatHistoryPageVO();
        chatHistoryPageVO.setRecords(getChatHistoryVOList(chatHistoryList));
        chatHistoryPageVO.setHasMore(hasMore);
        chatHistoryPageVO.setNextLastCreateTime(nextLastCreateTime);
        return chatHistoryPageVO;
    }
}
