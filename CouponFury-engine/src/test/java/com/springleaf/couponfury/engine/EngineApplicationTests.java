package com.springleaf.couponfury.engine;

import com.springleaf.couponfury.engine.dao.entity.CouponTemplateDO;
import com.springleaf.couponfury.engine.dao.mapper.CouponTemplateMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EngineApplicationTests {

    @Resource
    private CouponTemplateMapper couponTemplateMapper;

    @Test
    void contextLoads() {
        CouponTemplateDO couponTemplateDO = couponTemplateMapper.getCouponTemplateByShopNumberAndId(1810714735922956666L, 1811614173755472986L);
        System.out.println("-----------");
        System.out.println(couponTemplateDO);
        System.out.println("-----------");
    }
}
