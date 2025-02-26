package com.springleaf.couponfury.distribution.common.constant;

/**
 * mq幂等 Redis 常量
 */
public class IdempotentRedisConstant {
    /**
     * 幂等防止重复消费 Redis 键前缀
     */
    public static final String IDEMPOTENT_MQ_KEY = "idempotent_mq_consume:%s";
}
