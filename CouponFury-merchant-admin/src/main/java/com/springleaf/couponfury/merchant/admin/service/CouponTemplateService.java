package com.springleaf.couponfury.merchant.admin.service;

import com.springleaf.couponfury.merchant.admin.dto.req.CouponTemplateSaveReqDTO;

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
}
