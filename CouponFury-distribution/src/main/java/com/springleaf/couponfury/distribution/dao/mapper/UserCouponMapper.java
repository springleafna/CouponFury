package com.springleaf.couponfury.distribution.dao.mapper;

import com.springleaf.couponfury.distribution.dao.entity.UserCouponDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户优惠券数据库持久层
 */
@Mapper
public interface UserCouponMapper {

    void saveUserCoupon(UserCouponDO userCouponDO);

    /**
     * 批量保存用户优惠券
     * @param userCouponDOList 用户优惠券列表
     */
    void saveUserCouponList(List<UserCouponDO> userCouponDOList);

    UserCouponDO getUserCouponByCouponTemplateIdAndUserId(@Param("couponTemplateId") Long couponTemplateId, @Param("userId") Long userId);
}
