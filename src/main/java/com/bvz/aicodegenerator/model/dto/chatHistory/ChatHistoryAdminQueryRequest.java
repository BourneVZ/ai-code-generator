package com.bvz.aicodegenerator.model.dto.chatHistory;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员查询聊天记录请求
 */
@Data
public class ChatHistoryAdminQueryRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 消息状态
     */
    private String messageStatus;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 最后一条记录的创建时间
     * 管理端继续向前加载时，传当前列表最早一条消息的 createTime
     */
    private LocalDateTime lastCreateTime;

    /**
     * 分页大小，默认 10
     */
    private Integer pageSize = 10;

    private static final long serialVersionUID = 1L;
}
