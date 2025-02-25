package com.springleaf.couponfury.distribution.common.constant;

/**
 * 分发优惠券服务 Redis 缓存常量类
 */
public final class DistributionRedisConstant {

    /**
     * 优惠券模板推送执行进度 Key
     * 记录 Excel 执行到哪一行
     */
    public static final String TEMPLATE_TASK_EXECUTE_PROGRESS_KEY = "coupon-fury_distribution:template-task-execute-progress:%s";

    /**
     * 批量保存领取用户券用户 Key
     */
    public static final String TEMPLATE_TASK_EXECUTE_BATCH_USER_KEY = "coupon-fury_distribution:template-task-execute-batch-user:%s";
}
