package com.bvz.aicodegenerator.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天记录分页结果
 */
@Data
public class ChatHistoryPageVO implements Serializable {

    /**
     * 记录列表，按时间正序返回，便于前端直接渲染
     */
    private List<ChatHistoryVO> records;

    /**
     * 是否还有更早的消息
     */
    private Boolean hasMore;

    /**
     * 下一次向前加载时使用的时间游标
     */
    private LocalDateTime nextLastCreateTime;

    private static final long serialVersionUID = 1L;
}
