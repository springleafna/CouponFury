package com.springleaf.couponfury.engine.controller;

import com.springleaf.couponfury.engine.dto.req.CouponTemplateRemindCreateReqDTO;
import com.springleaf.couponfury.engine.service.CouponTemplateRemindService;
import com.springleaf.couponfury.framework.idempotent.NoDuplicateSubmit;
import com.springleaf.couponfury.framework.result.Result;
import com.springleaf.couponfury.framework.web.Results;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优惠券模板控制层
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "优惠券预约提醒管理")
public class CouponTemplateRemindController {

    private final CouponTemplateRemindService couponTemplateRemindService;

    @Operation(summary = "发出优惠券预约提醒请求")
    @NoDuplicateSubmit(message = "请勿短时间内重复提交预约提醒请求")
    @PostMapping("/api/engine/coupon-template-remind/create")
    public Result<Void> createCouponRemind(@RequestBody CouponTemplateRemindCreateReqDTO requestParam) {
        couponTemplateRemindService.createCouponRemind(requestParam);
        return Results.success();
    }
}
