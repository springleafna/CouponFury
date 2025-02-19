package com.springleaf.couponfury.merchant.admin.dao.mapper;

import com.springleaf.couponfury.merchant.admin.dao.entity.CouponTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface CouponTaskMapper {
    public void saveCouponTask(CouponTaskDO couponTaskDO);

    void updateCouponTaskSendNumById(@Param("id") long id, @Param("sendNum") int sendNum);

    CouponTaskDO selectCouponTaskById(Long id);

    List<CouponTaskDO> selectByStatusAndLimit(@Param("initId") long initId, @Param("now") Date now, @Param("status") int status, @Param("maxLimit") int maxLimit);

    void updateCouponTaskStatusById(@Param("id") Long id, @Param("status") int status);
}
