package com.springleaf.couponfury.merchant.admin.dao.mapper;

import com.springleaf.couponfury.merchant.admin.dao.entity.CouponTaskDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CouponTaskMapper {
    public void saveCouponTask(CouponTaskDO couponTaskDO);
}
