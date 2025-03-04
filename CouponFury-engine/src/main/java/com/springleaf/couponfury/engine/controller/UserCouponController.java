package com.springleaf.couponfury.engine.controller;

import com.springleaf.couponfury.engine.dto.req.CouponCreatePaymentReqDTO;
import com.springleaf.couponfury.engine.dto.req.CouponProcessPaymentReqDTO;
import com.springleaf.couponfury.engine.dto.req.CouponProcessRefundReqDTO;
import com.springleaf.couponfury.engine.dto.req.CouponTemplateRedeemReqDTO;
import com.springleaf.couponfury.engine.service.UserCouponService;
import com.springleaf.couponfury.framework.result.Result;
import com.springleaf.couponfury.framework.web.Results;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户优惠券控制层
 */
@RestController
@Tag(name = "用户优惠券管理")
public class UserCouponController {

    @Resource
    private UserCouponService userCouponService;

    @Operation(summary = "兑换优惠券模板", description = "存在较高流量场景，可类比“秒杀”业务")
    @PostMapping("/api/engine/user-coupon/redeem")
    public Result<Void> redeemUserCoupon(@RequestBody CouponTemplateRedeemReqDTO requestParam) {
        userCouponService.redeemUserCoupon(requestParam);
        return Results.success();
    }


    @Operation(summary = "创建用户优惠券结算单", description = "用户下单时锁定使用的优惠券，一般由订单系统发起调用")
    @PostMapping("/api/engine/user-coupon/create-payment-record")
    public Result<Void> createPaymentRecord(@RequestBody CouponCreatePaymentReqDTO requestParam) {
        userCouponService.createPaymentRecord(requestParam);
        return Results.success();
    }

    @Operation(summary = "核销优惠券结算单", description = "用户支付后核销使用的优惠券，常规来说应该监听支付后的消息队列事件")
    @PostMapping("/api/engine/user-coupon/process-payment")
    public Result<Void> processPayment(@RequestBody CouponProcessPaymentReqDTO requestParam) {
        userCouponService.processPayment(requestParam);
        return Results.success();
    }

    @Operation(summary = "退款优惠券结算单", description = "用户退款成功后返回使用的优惠券，常规来说应该监听退款成功后的消息队列事件")
    @PostMapping("/api/engine/user-coupon/process-refund")
    public Result<Void> processRefund(@RequestBody CouponProcessRefundReqDTO requestParam) {
        userCouponService.processRefund(requestParam);
        return Results.success();
    }
}
