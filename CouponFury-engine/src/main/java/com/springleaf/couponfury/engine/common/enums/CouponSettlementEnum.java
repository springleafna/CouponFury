package com.springleaf.couponfury.engine.common.enums;

import lombok.Getter;

/**
 * 优惠券结算单状态
 */
public enum CouponSettlementEnum {
    /**
     * 锁定
     */
    LOCKED(0),

    /**
     * 已取消
     */
    CANCELED(1),

    /**
     * 已支付
     */
    PAID(2),

    /**
     * 已退款
     */
    REFUNDED(3);

    @Getter
    private final int code;

    CouponSettlementEnum(int code) {
        this.code = code;
    }
}
