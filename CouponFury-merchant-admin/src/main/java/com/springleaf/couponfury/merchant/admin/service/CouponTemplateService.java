package com.springleaf.couponfury.merchant.admin.service;

import com.github.pagehelper.PageInfo;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTemplateNumberReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTemplatePageQueryReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTemplateSaveReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.req.PageParamReqDTO;
import com.springleaf.couponfury.merchant.admin.dto.resp.CouponTemplatePageQueryRespDTO;
import com.springleaf.couponfury.merchant.admin.dto.resp.CouponTemplateQueryRespDTO;

/**
 * 优惠券模板业务逻辑层
 */
public interface CouponTemplateService {
    /**
     * 创建商家优惠券模板
     *
     * @param requestParam 请求参数
     */
    void createCouponTemplate(CouponTemplateSaveReqDTO requestParam);

    /**
     * 分页查询商家优惠券模板
     *
     * @param requestParam 请求参数
     * @param pageParam    分页参数
     * @return 分页结果
     */
    PageInfo<CouponTemplatePageQueryRespDTO> pageQueryCouponTemplate(CouponTemplatePageQueryReqDTO requestParam, PageParamReqDTO pageParam);

    /**
     * 查询优惠券模板详情
     * 后管接口并不存在并发，直接查询数据库即可
     *
     * @param couponTemplateId 优惠券模板 ID
     * @return 优惠券模板详情
     */
    CouponTemplateQueryRespDTO findCouponTemplateById(Long couponTemplateId);

    /**
     * 结束优惠券模板
     *
     * @param couponTemplateId 优惠券模板 ID
     */
    void terminateCouponTemplate(Long couponTemplateId);

    /**
     * 增加优惠券模板发行量
     *
     * @param requestParam 请求参数
     */
    void increaseNumberCouponTemplate(CouponTemplateNumberReqDTO requestParam);
}
