package com.springleaf.couponfury.distribution;

import com.springleaf.couponfury.distribution.dao.entity.UserCouponDO;
import com.springleaf.couponfury.distribution.dao.mapper.CouponTaskMapper;
import com.springleaf.couponfury.distribution.dao.mapper.UserCouponMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@SpringBootTest
class DistributionApplicationTests {

    @Resource
    private UserCouponMapper userCouponMapper;
    @Test
    void contextLoads() {
        List<UserCouponDO> userCouponList = new ArrayList<>();

        // 第一个 UserCouponDO 对象
        UserCouponDO coupon1 = new UserCouponDO();
        coupon1.setId(1L);
        coupon1.setUserId(1001L);
        coupon1.setCouponTemplateId(2001L);
        coupon1.setReceiveTime(new Date()); // 当前时间
        coupon1.setReceiveCount(1);
        coupon1.setValidStartTime(new Date()); // 当前时间
        coupon1.setValidEndTime(new Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000)); // 7天后
        coupon1.setUseTime(null); // 未使用
        coupon1.setSource(0); // 领券中心
        coupon1.setStatus(0); // 未使用
        coupon1.setCreateTime(new Date());
        coupon1.setUpdateTime(new Date());
        coupon1.setDelFlag(0); // 未删除
        coupon1.setRowNum(1); // Excel 行号
        userCouponList.add(coupon1);

        // 第二个 UserCouponDO 对象
        UserCouponDO coupon2 = new UserCouponDO();
        coupon2.setId(2L);
        coupon2.setUserId(1002L);
        coupon2.setCouponTemplateId(2002L);
        coupon2.setReceiveTime(new Date());
        coupon2.setReceiveCount(1);
        coupon2.setValidStartTime(new Date());
        coupon2.setValidEndTime(new Date(System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000)); // 14天后
        coupon2.setUseTime(null);
        coupon2.setSource(1); // 平台发放
        coupon2.setStatus(1); // 锁定
        coupon2.setCreateTime(new Date());
        coupon2.setUpdateTime(new Date());
        coupon2.setDelFlag(0);
        coupon2.setRowNum(2);
        userCouponList.add(coupon2);

        // 第三个 UserCouponDO 对象
        UserCouponDO coupon3 = new UserCouponDO();
        coupon3.setId(3L);
        coupon3.setUserId(1003L);
        coupon3.setCouponTemplateId(2003L);
        coupon3.setReceiveTime(new Date());
        coupon3.setReceiveCount(2);
        coupon3.setValidStartTime(new Date());
        coupon3.setValidEndTime(new Date(System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000)); // 30天后
        coupon3.setUseTime(new Date()); // 已使用
        coupon3.setSource(2); // 店铺领取
        coupon3.setStatus(2); // 已使用
        coupon3.setCreateTime(new Date());
        coupon3.setUpdateTime(new Date());
        coupon3.setDelFlag(0);
        coupon3.setRowNum(3);
        userCouponList.add(coupon3);

        userCouponMapper.saveUserCouponList(userCouponList);
    }
}
