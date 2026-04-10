package com.bvz.aicodegenerator.model.dto.chatHistory;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用聊天记录查询请求
 */
@Data
public class ChatHistoryQueryRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 最后一条记录的创建时间
     * 前端下次加载更早记录时，传当前列表最早一条消息的 createTime
     */
    private LocalDateTime lastCreateTime;

    /**
     * 分页大小，默认 10
     */
    private Integer pageSize = 10;

    private static final long serialVersionUID = 1L;
}
