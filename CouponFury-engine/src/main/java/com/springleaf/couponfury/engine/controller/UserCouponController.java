package com.springleaf.couponfury.engine.controller;

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
}
