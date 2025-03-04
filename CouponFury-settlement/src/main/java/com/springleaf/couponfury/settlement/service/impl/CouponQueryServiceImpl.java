package com.springleaf.couponfury.settlement.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.springleaf.couponfury.framework.exception.ClientException;
import com.springleaf.couponfury.settlement.common.context.UserContext;
import com.springleaf.couponfury.settlement.dto.req.QueryCouponGoodsReqDTO;
import com.springleaf.couponfury.settlement.dto.req.QueryCouponsReqDTO;
import com.springleaf.couponfury.settlement.dto.resp.CouponTemplateQueryRespDTO;
import com.springleaf.couponfury.settlement.dto.resp.QueryCouponsDetailRespDTO;
import com.springleaf.couponfury.settlement.dto.resp.QueryCouponsRespDTO;
import com.springleaf.couponfury.settlement.service.CouponQueryService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.springleaf.couponfury.settlement.common.constant.EngineRedisConstant.COUPON_TEMPLATE_KEY;
import static com.springleaf.couponfury.settlement.common.constant.EngineRedisConstant.USER_COUPON_TEMPLATE_LIST_KEY;

@Service
public class CouponQueryServiceImpl implements CouponQueryService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public QueryCouponsRespDTO listQueryUserCoupons(QueryCouponsReqDTO requestParam) {
        return null;
    }

    /*单线程版本*/
    @Override
    public QueryCouponsRespDTO listQueryUserCouponsBySync(QueryCouponsReqDTO requestParam) {
        // 获取用户已领取的优惠券列表
        Set<String> rangeUserCoupons = stringRedisTemplate.opsForZSet().range(String.format(USER_COUPON_TEMPLATE_LIST_KEY, UserContext.getUserId()), 0, -1);

        // 转换成 coupon-fury_engine:template:coupon_template_id 集合
        List<String> couponTemplateIds = rangeUserCoupons != null ? rangeUserCoupons.stream()
                .map(each -> StrUtil.split(each, "_").get(0))
                .map(each -> String.format(COUPON_TEMPLATE_KEY, each))
                .toList() : null;
        // 利用executePipelined方法对多个Redis命令进行管道化执行，从而提高查询效率。
        List<Object> couponTemplateList = stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
            if (couponTemplateIds != null) {
                couponTemplateIds.forEach(each -> connection.hashCommands().hGetAll(each.getBytes()));
            }
            return null;
        });

        // 按 goods 字段分区：
        // 使用 Collectors.partitioningBy 方法，将优惠券模板列表分为两部分：
        // true：goods 字段为空的优惠券模板，表示该优惠券是平台券或店铺券（没有特定商品限制）。
        // false：goods 字段不为空的优惠券模板，表示该优惠券是商品专属券（只能用于特定商品）。
        List<CouponTemplateQueryRespDTO> couponTemplateDTOList = JSON.parseArray(JSON.toJSONString(couponTemplateList), CouponTemplateQueryRespDTO.class);
        Map<Boolean, List<CouponTemplateQueryRespDTO>> partitioned = couponTemplateDTOList.stream()
                .collect(Collectors.partitioningBy(coupon -> StrUtil.isEmpty(coupon.getGoods())));

        // 拆分后的两个列表
        List<CouponTemplateQueryRespDTO> goodsEmptyList = partitioned.get(true); // goods 为空的列表
        List<CouponTemplateQueryRespDTO> goodsNotEmptyList = partitioned.get(false); // goods 不为空的列表

        // 针对当前订单可用/不可用的优惠券列表
        List<QueryCouponsDetailRespDTO> availableCouponList = new ArrayList<>();
        List<QueryCouponsDetailRespDTO> notAvailableCouponList = new ArrayList<>();

        // 平台券or店铺券
        goodsEmptyList.forEach(each -> {
            JSONObject jsonObject = JSON.parseObject(each.getConsumeRule());
            QueryCouponsDetailRespDTO resultQueryCouponDetail = BeanUtil.toBean(each, QueryCouponsDetailRespDTO.class);
            BigDecimal maximumDiscountAmount = jsonObject.getBigDecimal("maximumDiscountAmount");
            switch (each.getType()) {
                case 0 -> { // 立减券
                    resultQueryCouponDetail.setCouponAmount(maximumDiscountAmount);
                    availableCouponList.add(resultQueryCouponDetail);
                }
                case 1 -> { // 满减券
                    // orderAmount 大于或等于 termsOfUse
                    if (requestParam.getOrderAmount().compareTo(jsonObject.getBigDecimal("termsOfUse")) >= 0) {
                        resultQueryCouponDetail.setCouponAmount(maximumDiscountAmount);
                        availableCouponList.add(resultQueryCouponDetail);
                    } else {
                        notAvailableCouponList.add(resultQueryCouponDetail);
                    }
                }
                case 2 -> { // 折扣券
                    // orderAmount 大于或等于 termsOfUse
                    if (requestParam.getOrderAmount().compareTo(jsonObject.getBigDecimal("termsOfUse")) >= 0) {
                        BigDecimal multiply = requestParam.getOrderAmount().multiply(jsonObject.getBigDecimal("discountRate"));
                        if (multiply.compareTo(maximumDiscountAmount) >= 0) {
                            resultQueryCouponDetail.setCouponAmount(maximumDiscountAmount);
                        } else {
                            resultQueryCouponDetail.setCouponAmount(multiply);
                        }
                        availableCouponList.add(resultQueryCouponDetail);
                    } else {
                        notAvailableCouponList.add(resultQueryCouponDetail);
                    }
                }
                default -> throw new ClientException("无效的优惠券类型");
            }
        });

        Map<String, QueryCouponGoodsReqDTO> goodsRequestMap = requestParam.getGoodsList().stream()
                .collect(Collectors.toMap(QueryCouponGoodsReqDTO::getGoodsNumber, Function.identity(), (existing, replacement) -> existing));

        // 商品专属券
        goodsNotEmptyList.forEach(each -> {
            QueryCouponGoodsReqDTO couponGoods = goodsRequestMap.get(each.getGoods());
            if (couponGoods == null) {
                notAvailableCouponList.add(BeanUtil.toBean(each, QueryCouponsDetailRespDTO.class));
            }
            JSONObject jsonObject = JSON.parseObject(each.getConsumeRule());
            QueryCouponsDetailRespDTO resultQueryCouponDetail = BeanUtil.toBean(each, QueryCouponsDetailRespDTO.class);
            switch (each.getType()) {
                case 0 -> { // 立减券
                    resultQueryCouponDetail.setCouponAmount(jsonObject.getBigDecimal("maximumDiscountAmount"));
                    availableCouponList.add(resultQueryCouponDetail);
                }
                case 1 -> { // 满减券
                    // goodsAmount 大于或等于 termsOfUse
                    if (couponGoods != null && couponGoods.getGoodsAmount().compareTo(jsonObject.getBigDecimal("termsOfUse")) >= 0) {
                        resultQueryCouponDetail.setCouponAmount(jsonObject.getBigDecimal("maximumDiscountAmount"));
                        availableCouponList.add(resultQueryCouponDetail);
                    }
                }
                case 2 -> { // 折扣券
                    // goodsAmount 大于或等于 termsOfUse
                    if (couponGoods != null && couponGoods.getGoodsAmount().compareTo(jsonObject.getBigDecimal("termsOfUse")) >= 0) {
                        BigDecimal discountRate = jsonObject.getBigDecimal("discountRate");
                        resultQueryCouponDetail.setCouponAmount(couponGoods.getGoodsAmount().multiply(discountRate));
                        availableCouponList.add(resultQueryCouponDetail);
                    }
                }
                default -> throw new ClientException("无效的优惠券类型");
            }
        });

        // 与业内标准一致，按最终优惠力度从大到小排序
        availableCouponList.sort((c1, c2) -> c2.getCouponAmount().compareTo(c1.getCouponAmount()));

        return QueryCouponsRespDTO.builder()
                .availableCouponList(availableCouponList)
                .notAvailableCouponList(notAvailableCouponList)
                .build();
    }
}
