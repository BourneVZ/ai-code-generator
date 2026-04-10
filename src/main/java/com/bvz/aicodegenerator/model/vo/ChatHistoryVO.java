package com.bvz.aicodegenerator.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ChatHistoryVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 消息类型文本
     */
    private String messageTypeText;

    /**
     * 消息状态
     */
    private String messageStatus;

    /**
     * 消息状态文本
     */
    private String messageStatusText;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 关联用户信息
     */
    private UserVO user;

    private static final long serialVersionUID = 1L;
}
