package com.springleaf.couponfury.engine.service;

import com.springleaf.couponfury.engine.dto.req.CouponCreatePaymentReqDTO;
import com.springleaf.couponfury.engine.dto.req.CouponProcessPaymentReqDTO;
import com.springleaf.couponfury.engine.dto.req.CouponProcessRefundReqDTO;
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

    /**
     * 创建优惠券结算单记录
     *
     * @param requestParam 创建优惠券结算单请求参数
     */
    void createPaymentRecord(CouponCreatePaymentReqDTO requestParam);

    /**
     * 处理订单支付操作，修改结算单为已支付
     *
     * @param requestParam 处理优惠券结算单请求参数
     */
    void processPayment(CouponProcessPaymentReqDTO requestParam);

    /**
     * 处理订单退款操作，修改结算单为已退款并回滚优惠券
     *
     * @param requestParam 处理优惠券结算单退款请求参数
     */
    void processRefund(CouponProcessRefundReqDTO requestParam);
}
