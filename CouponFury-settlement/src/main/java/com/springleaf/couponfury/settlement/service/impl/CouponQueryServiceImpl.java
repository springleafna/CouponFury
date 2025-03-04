package com.springleaf.couponfury.settlement.service.impl;

import com.springleaf.couponfury.settlement.dto.req.QueryCouponsReqDTO;
import com.springleaf.couponfury.settlement.dto.resp.QueryCouponsRespDTO;
import com.springleaf.couponfury.settlement.service.CouponQueryService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CouponQueryServiceImpl implements CouponQueryService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public QueryCouponsRespDTO listQueryUserCoupons(QueryCouponsReqDTO requestParam) {
        return null;
    }

    @Override
    public QueryCouponsRespDTO listQueryUserCouponsBySync(QueryCouponsReqDTO requestParam) {
        return null;
    }
}
