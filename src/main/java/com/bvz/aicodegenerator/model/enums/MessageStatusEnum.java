package com.bvz.aicodegenerator.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum MessageStatusEnum {

    SUCCESS("成功", "success"),
    ERROR("失败", "error");

    private final String text;

    private final String value;

    MessageStatusEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值
     * @return 枚举
     */
    public static MessageStatusEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (MessageStatusEnum anEnum : MessageStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
