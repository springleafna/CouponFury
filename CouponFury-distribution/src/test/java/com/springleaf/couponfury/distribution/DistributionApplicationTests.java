package com.springleaf.couponfury.distribution;

import cn.hutool.core.map.MapUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.springleaf.couponfury.distribution.dao.entity.CouponTaskFailDO;
import com.springleaf.couponfury.distribution.dao.mapper.CouponTaskFailMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

@SpringBootTest
class DistributionApplicationTests {

    @Resource
    private CouponTaskFailMapper couponTaskFailMapper;
    @Test
    void contextLoads() {
        Map<Object, Object> objectMap = MapUtil.builder()
                .put("rowNum", 1)
                .put("cause", "优惠券模板无库存")
                .build();
        CouponTaskFailDO couponTaskFailDO = CouponTaskFailDO.builder()
                .batchId(123L)
                .jsonObject(JSON.toJSONString(objectMap, JSONWriter.Feature.WriteNulls))
                .build();
        couponTaskFailMapper.saveCouponTaskFail(couponTaskFailDO);
    }
}
