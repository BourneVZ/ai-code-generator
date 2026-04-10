package com.bvz.aicodegenerator.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话历史 实体类
 *
 * @author <a href="https://github.com/BourneVZ">BVZ</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_history")
public class ChatHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 消息类型：user / ai
     */
    @Column("messageType")
    private String messageType;

    /**
     * 消息状态：success / error
     */
    @Column("messageStatus")
    private String messageStatus;

    /**
     * 错误信息
     */
    @Column("errorMessage")
    private String errorMessage;

    /**
     * 应用 id
     */
    @Column("appId")
    private Long appId;

    /**
     * 创建用户 id
     */
    @Column("userId")
    private Long userId;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;

}
