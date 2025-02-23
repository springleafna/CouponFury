package com.springleaf.couponfury.distribution;

import com.springleaf.couponfury.distribution.dao.mapper.CouponTaskMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DistributionApplicationTests {

    @Resource
    private CouponTaskMapper couponTaskMapper;
    @Test
    void contextLoads() {
        System.out.println(couponTaskMapper.getCouponTaskById(1816672964423188499L));
    }
}
