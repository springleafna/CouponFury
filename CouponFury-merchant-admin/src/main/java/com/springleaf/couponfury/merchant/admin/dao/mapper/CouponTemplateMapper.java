package com.springleaf.couponfury.merchant.admin.dao.mapper;

import com.springleaf.couponfury.merchant.admin.dao.entity.CouponTemplateDO;
import com.springleaf.couponfury.merchant.admin.dto.req.CouponTemplatePageQueryReqDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CouponTemplateMapper {
    /*
    * 保存优惠券模板
     */
    int saveCouponTemplate(CouponTemplateDO couponTemplateDO);

    /*
    * 查询优惠券模板
     */
    List<CouponTemplateDO> listCouponTemplate(CouponTemplateDO couponTemplateDO);

    /*
    * 根据优惠券模板id查询优惠券模板
     */
    CouponTemplateDO getCouponTemplateById(Long couponTemplateId);

    /*
    * 根据优惠券模板id和店铺编号查询优惠券模板
     */
    CouponTemplateDO getCouponTemplateByShopNumberAndId(@Param("shopNumber") Long shopNumber, @Param("couponTemplateId") Long couponTemplateId);

    /**
     * 增加优惠券模板发行量
     *
     * @param shopNumber       店铺编号
     * @param couponTemplateId 优惠券模板 ID
     * @param number           增加发行数量
     */
    int increaseNumberCouponTemplate(@Param("shopNumber") Long shopNumber, @Param("couponTemplateId") Long couponTemplateId, @Param("number") Integer number);

    /**
     * 更新优惠券模板状态
     *
     * @param couponTemplateId 优惠券模板 ID
     * @param shopNumber       店铺编号
     * @param status           状态
     */
    void updateCouponTemplateStatus(@Param("couponTemplateId") Long couponTemplateId, @Param("shopNumber") Long shopNumber, @Param("status") int status);
}
