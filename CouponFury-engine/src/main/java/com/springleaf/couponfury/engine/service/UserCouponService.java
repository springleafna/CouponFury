package com.springleaf.couponfury.engine.service;

import com.springleaf.couponfury.engine.dto.req.CouponTemplateRedeemReqDTO;

/**
 * 用户优惠券业务逻辑层

 */
public interface UserCouponService {

    /**
     * 用户兑换优惠券
     *
     * @param requestParam 请求参数
     */
    void redeemUserCoupon(CouponTemplateRedeemReqDTO requestParam);
}
