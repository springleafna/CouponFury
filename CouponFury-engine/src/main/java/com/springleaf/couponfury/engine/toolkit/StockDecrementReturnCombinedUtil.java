package com.springleaf.couponfury.engine.toolkit;

/**
 * 扣减优惠券模板库存复合返回工具类
 * firstField: 请求是否成功：有 3 个参数，0 代表请求成功，1 代表优惠券已被领取完，2 代表用户已经达到领取上限。
 * secondField: 用户领取次数：初始化为 0，每次领取成功后自增加 1。
 */
public final class StockDecrementReturnCombinedUtil {

    /**
     * 2^14 > 9999, 所以用 14 位来表示第二个字段
     */
    private static final int SECOND_FIELD_BITS = 14;

    /**
     * 从组合的 int 中提取第一个字段（0、1或2）
     */
    public static long extractFirstField(long combined) {
        return (combined >> SECOND_FIELD_BITS) & 0b11; // 0b11 即二进制的 11，用于限制结果为 2 位
    }

    /**
     * 从组合的 int 中提取第二个字段（0 到 9999 之间的数字）
     */
    public static long extractSecondField(long combined) {
        return combined & ((1 << SECOND_FIELD_BITS) - 1);
    }
}
