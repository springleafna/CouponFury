package com.springleaf.couponfury.engine.toolkit;

import com.springleaf.couponfury.framework.exception.ClientException;

/**
 * 优惠券预约提醒位图生成工具
 * 每个提醒类型占用 12 位，支持最多 5 种类型（long 类型 64 位）
 * 每个时间间隔为 5 分钟，最大支持 60 分钟提醒（12 个间隔）
 */
public class CouponTemplateRemindUtil {

    // 每个类型占用的二进制位数（12位可表示60分钟，5分钟一个间隔）
    private static final int BITS_PER_TYPE = 12;

    // 时间间隔（单位：分钟）
    private static final int MINUTES_PER_INTERVAL = 5;

    // 支持的最大提醒时间（60分钟）
    private static final int MAX_REMIND_MINUTES = MINUTES_PER_INTERVAL * BITS_PER_TYPE;

    // 支持的最大提醒类型（0~4）
    private static final int MAX_TYPE = (Long.SIZE / BITS_PER_TYPE) - 1;

    /**
     * 根据提醒时间和类型生成位图
     * @param remindTime 提醒时间（单位分钟，必须为5的倍数，范围[5,60]）
     * @param type       提醒类型（范围[0,4]）
     * @return 位图值（64位long，每位代表一个提醒时间点）
     * 类型0：位范围 0~11。
     * 类型1：位范围 12~23。
     * 类型2：位范围 24~35，依此类推。
     * 示例：
     * type=0，remindTime=10分钟 → bitPosition = 0*12 + 1 = 1。
     * type=1，remindTime=15分钟 → bitPosition = 1*12 + 2 = 14。
     * 将数字 1 左移 bitPosition 位，生成仅该位置为1的long值。
     * 示例：
     * bitPosition=1 → 1L << 1 = 2（二进制 000...10）。
     * bitPosition=14 → 1L << 14 = 16384（二进制 000...010000000000000000）。
     * 多个提醒时间和类型可通过 按位或（|） 合并成一个最终位图。
     */
    public static long calculateBitMap(int remindTime, int type) {
        validateParameters(remindTime, type);
        int intervalIndex = (remindTime / MINUTES_PER_INTERVAL) - 1;
        int bitPosition = type * BITS_PER_TYPE + intervalIndex;
        return 1L << bitPosition;
    }

    /**
     * 参数校验
     */
    private static void validateParameters(int remindTime, int type) {
        // 校验提醒时间范围
        if (remindTime < MINUTES_PER_INTERVAL) {
            throw new ClientException(
                    String.format("提醒时间不能小于%d分钟", MINUTES_PER_INTERVAL)
            );
        }
        if (remindTime > MAX_REMIND_MINUTES) {
            throw new ClientException(
                    String.format("提醒时间不能超过%d分钟", MAX_REMIND_MINUTES)
            );
        }

        // 校验是否为5的倍数
        if (remindTime % MINUTES_PER_INTERVAL != 0) {
            throw new ClientException(
                    String.format("提醒时间必须是%d分钟的倍数", MINUTES_PER_INTERVAL)
            );
        }

        // 校验提醒类型范围
        if (type < 0 || type > MAX_TYPE) {
            throw new ClientException(
                    String.format("提醒类型参数无效，取值范围[0,%d]", MAX_TYPE)
            );
        }
    }
}