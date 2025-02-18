package com.springleaf.couponfury.merchant.admin.controller;

import com.springleaf.couponfury.framework.idempotent.NoDuplicateSubmit;
import com.springleaf.couponfury.framework.result.Result;
import com.springleaf.couponfury.framework.web.Results;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTaskCreateReqDTO;
import com.springleaf.couponfury.merchant.admin.service.CouponTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "优惠券推送任务管理")
public class CouponTaskController {

    @Resource
    private CouponTaskService couponTaskService;

    @Operation(summary = "创建优惠券推送任务")
    @NoDuplicateSubmit(message = "请勿短时间内重复提交优惠券推送任务")
    @PostMapping("/api/merchant-admin/coupon-task/create")
    public Result<Void> createCouponTask(@RequestBody CouponTaskCreateReqDTO requestParam) {
        couponTaskService.createCouponTask(requestParam);
        return Results.success();
    }

}
