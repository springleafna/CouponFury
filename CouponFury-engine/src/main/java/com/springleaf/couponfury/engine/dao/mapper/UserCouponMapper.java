package com.springleaf.couponfury.engine.dao.mapper;

import com.springleaf.couponfury.engine.dao.entity.UserCouponDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserCouponMapper {

    void saveUserCoupon(UserCouponDO userCouponDO);
}
