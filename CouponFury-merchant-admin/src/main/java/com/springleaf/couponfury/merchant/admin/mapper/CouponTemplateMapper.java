package com.springleaf.couponfury.merchant.admin.mapper;

import com.springleaf.couponfury.merchant.admin.dao.entity.CouponTemplateDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CouponTemplateMapper {
    void saveCouponTemplate(CouponTemplateDO couponTemplateDO);
}
