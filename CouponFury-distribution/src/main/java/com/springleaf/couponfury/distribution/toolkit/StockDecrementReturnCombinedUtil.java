package com.springleaf.couponfury.distribution.toolkit;

/**
 * 用户优惠券执行 LUA 脚本返回数据｜通过位移形式提高性能，是个小优化
 * 因为预计每 5000 条记录保存一次数据库，2^12 能表示 4096，所以这里采用了 2^13
 */
public class StockDecrementReturnCombinedUtil {

    /**
     * 2^13 > 5000, 所以用 13 位来表示第二个字段
     */
    private static final int SECOND_FIELD_BITS = 13;

    /**
     * 将布尔值decrementFlag和整数userRecord组合成一个整数int
     * decrementFlag转换为1或0，左移 << 13位，占据高位
     * userRecord直接占据低13位
     * 两者通过按位 或 | 运算合并
     * 例如：decrementFlag为true，userRecord为5000，则组合结果为 (1 << 13) | 5000 = 8192 + 5000 = 13192
     */
    public static int combineFields(boolean decrementFlag, int userRecord) {
        return (decrementFlag ? 1 : 0) << SECOND_FIELD_BITS | userRecord;
    }

    /**
     * 从组合的int中提取第一个字段（0或1）
     * 右移13位后判断高位是否为非零。若结果非零，返回true，否则false
     */
    public static boolean extractFirstField(long combined) {
        return (combined >> SECOND_FIELD_BITS) != 0;
    }

    /**
     * 从组合的int中提取第二个字段（1到5000之间的数字）
     * 1 << SECOND_FIELD_BITS：将 1 左移 SECOND_FIELD_BITS（13）位，得到一个二进制数：
     * 100000000000000（共 14 位，其中第 14 位是 1，其余是 0）。对应的十进制值是 2^13 = 8192
     * 减 1：将 8192 - 1 = 8191，对应的二进制是 1111111111111（共 13 个 1）。
     * 这相当于生成了一个低位掩码，它的低 13 位都是 1，高位全部是 0。
     * 按位与 & 运算：将组合后的整数 combined 与掩码 8191（二进制 1111111111111）进行按位与操作。
     * 效果：保留 combined 的低 13 位，将高位全部置 0。
     */
    public static int extractSecondField(int combined) {
        return combined & ((1 << SECOND_FIELD_BITS) - 1);
    }
}
