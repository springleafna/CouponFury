package com.springleaf.couponfury.engine.common.enums;

import lombok.Getter;

/**
 * 优惠券模板状态枚举
 */
public enum CouponTemplateStatusEnum {

    /**
     * 0: 表示优惠券处于生效中的状态。
     */
    ACTIVE(0),

    /**
     * 1: 表示优惠券已经结束，不可再使用。
     */
    ENDED(1);

    @Getter
    private final int status;

    CouponTemplateStatusEnum(int status) {
        this.status = status;
    }
}
