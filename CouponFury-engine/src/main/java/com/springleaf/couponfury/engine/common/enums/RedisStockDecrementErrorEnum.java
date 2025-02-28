package com.springleaf.couponfury.engine.common.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis 扣减优惠券库存错误枚举（优化版）
 */
public enum RedisStockDecrementErrorEnum {

    SUCCESS(0, "成功"),
    STOCK_INSUFFICIENT(1, "优惠券已被领取完啦"),
    LIMIT_REACHED(2, "用户已经达到领取上限");

    @Getter
    private final long code;
    @Getter
    private final String message;

    private static final Map<Long, RedisStockDecrementErrorEnum> CODE_MAP = new HashMap<>();

    static {
        for (RedisStockDecrementErrorEnum value : values()) {
            CODE_MAP.put(value.code, value);
        }
    }

    RedisStockDecrementErrorEnum(long code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 根据 code 获取错误描述
     */
    public static String getMessageByCode(long code) {
        RedisStockDecrementErrorEnum status = CODE_MAP.get(code);
        return status != null ? status.getMessage() : "未知错误";
    }

    /**
     * 判断是否失败（code != 0）
     */
    public static boolean isFailedCode(long code) {
        return code != SUCCESS.code;
    }
}
