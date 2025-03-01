package com.springleaf.couponfury.engine.dao.mapper;

import com.springleaf.couponfury.engine.dao.entity.CouponTemplateRemindDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CouponTemplateRemindMapper {

    CouponTemplateRemindDO getCouponRemindByUserIdAndCouponTemplateId(@Param("userId") String userId, @Param("couponTemplateId") Long couponTemplateId);

    void saveCouponTemplateRemind(CouponTemplateRemindDO couponTemplateRemindDO);

    void updateCouponTemplateRemindInformation(CouponTemplateRemindDO couponTemplateRemindDO);
}
