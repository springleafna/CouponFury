package com.springleaf.couponfury.engine.dao.mapper;

import com.springleaf.couponfury.engine.dao.entity.UserCouponDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserCouponMapper {

    void saveUserCoupon(UserCouponDO userCouponDO);

    UserCouponDO getUserCouponByCouponIdAndUserId(@Param("couponId") Long couponId, @Param("userId") Long userId);

    int updateUserCouponStatus(@Param("couponId") Long couponId, @Param("userId") Long userId, @Param("oldStatus") Integer oldStatus, @Param("freshStatus") Integer freshStatus);
}
