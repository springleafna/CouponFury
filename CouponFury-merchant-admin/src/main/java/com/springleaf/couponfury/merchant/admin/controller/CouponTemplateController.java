package com.springleaf.couponfury.merchant.admin.controller;


import com.github.pagehelper.PageInfo;
import com.springleaf.couponfury.framework.idempotent.NoDuplicateSubmit;
import com.springleaf.couponfury.framework.result.Result;
import com.springleaf.couponfury.framework.web.Results;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTemplateNumberReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTemplatePageQueryReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTemplateSaveReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.req.PageParamReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.resp.CouponTemplatePageQueryRespDTO;
import com.springleaf.couponfury.merchant.admin.dto.resp.CouponTemplateQueryRespDTO;
import com.springleaf.couponfury.merchant.admin.service.CouponTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优惠券模板控制层
 */
@RestController
@Tag(name = "优惠券模板管理")
public class CouponTemplateController {
    @Resource
    private CouponTemplateService couponTemplateService;

    @NoDuplicateSubmit
    @Operation(summary = "商家创建优惠券模板")
    @PostMapping("/api/merchant-admin/coupon-template/create")
    public Result<Void> createCouponTemplate(@RequestBody CouponTemplateSaveReqDTO requestParam) {
        couponTemplateService.createCouponTemplate(requestParam);
        return Results.success();
    }

    @Operation(summary = "分页查询优惠券模板")
    @GetMapping("/api/merchant-admin/coupon-template/page")
    public Result<PageInfo<CouponTemplatePageQueryRespDTO>> pageQueryCouponTemplate(CouponTemplatePageQueryReqDTO requestParam, PageParamReqDTO pageParam) {
        return Results.success(couponTemplateService.pageQueryCouponTemplate(requestParam, pageParam));
    }

    @Operation(summary = "查询优惠券模板详情")
    @GetMapping("/api/merchant-admin/coupon-template/find")
    public Result<CouponTemplateQueryRespDTO> findCouponTemplate(Long couponTemplateId) {
        return Results.success(couponTemplateService.findCouponTemplateById(couponTemplateId));
    }

    @Operation(summary = "增加优惠券模板发行量")
    @NoDuplicateSubmit(message = "请勿短时间内重复增加优惠券发行量")
    @PostMapping("/api/merchant-admin/coupon-template/increase-number")
    public Result<Void> increaseNumberCouponTemplate(@RequestBody CouponTemplateNumberReqDTO requestParam) {
        couponTemplateService.increaseNumberCouponTemplate(requestParam);
        return Results.success();
    }

    @Operation(summary = "结束优惠券模板")
    @PostMapping("/api/merchant-admin/coupon-template/terminate")
    public Result<Void> terminateCouponTemplate(Long couponTemplateId) {
        couponTemplateService.terminateCouponTemplate(couponTemplateId);
        return Results.success();
    }
}
