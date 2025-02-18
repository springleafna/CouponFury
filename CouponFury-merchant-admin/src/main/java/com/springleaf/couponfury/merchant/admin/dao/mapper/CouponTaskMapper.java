package com.springleaf.couponfury.merchant.admin.dao.mapper;

import com.springleaf.couponfury.merchant.admin.dao.entity.CouponTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CouponTaskMapper {
    public void saveCouponTask(CouponTaskDO couponTaskDO);

    void updateSendNumById(@Param("id") long id, @Param("sendNum") int sendNum);

    CouponTaskDO selectById(Long id);
}
