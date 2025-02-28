package com.springleaf.couponfury.engine.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Singleton;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.springleaf.couponfury.engine.common.constant.EngineRedisConstant;
import com.springleaf.couponfury.engine.common.context.UserContext;
import com.springleaf.couponfury.engine.common.enums.RedisStockDecrementErrorEnum;
import com.springleaf.couponfury.engine.dto.req.CouponTemplateQueryReqDTO;
import com.springleaf.couponfury.engine.dto.req.CouponTemplateRedeemReqDTO;
import com.springleaf.couponfury.engine.dto.resp.CouponTemplateQueryRespDTO;
import com.springleaf.couponfury.engine.mq.event.UserCouponRedeemEvent;
import com.springleaf.couponfury.engine.mq.producer.EventPublisher;
import com.springleaf.couponfury.engine.service.CouponTemplateService;
import com.springleaf.couponfury.engine.service.UserCouponService;
import com.springleaf.couponfury.engine.toolkit.StockDecrementReturnCombinedUtil;
import com.springleaf.couponfury.framework.exception.ClientException;
import com.springleaf.couponfury.framework.exception.ServiceException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class UserCouponServiceImpl implements UserCouponService {

    @Resource
    private CouponTemplateService couponTemplateService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserCouponRedeemEvent userCouponRedeemEvent;
    @Resource
    private EventPublisher eventPublisher;

    // lua 脚本路径
    private final static String STOCK_DECREMENT_AND_SAVE_USER_RECEIVE_LUA_PATH = "lua/stock_decrement_and_save_user_receive.lua";


    @Override
    public void redeemUserCoupon(CouponTemplateRedeemReqDTO requestParam) {
        // 验证缓存是否存在，保障数据存在并且缓存中存在
        CouponTemplateQueryRespDTO couponTemplate = couponTemplateService.findCouponTemplate(BeanUtil.toBean(requestParam, CouponTemplateQueryReqDTO.class));

        // 验证领取的优惠券是否在活动有效时间
        boolean isInTime = DateUtil.isIn(new Date(), couponTemplate.getValidStartTime(), couponTemplate.getValidEndTime());
        if (!isInTime) {
            // 一般来说优惠券领取时间不到的时候，前端不会放开调用请求，可以理解这是用户调用接口在“攻击”
            throw new ClientException("不满足优惠券领取时间");
        }

        // 获取 LUA 脚本，并保存到 Hutool 的单例管理容器，下次直接获取不需要加载
        DefaultRedisScript<Long> buildLuaScript = Singleton.get(STOCK_DECREMENT_AND_SAVE_USER_RECEIVE_LUA_PATH, () -> {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(STOCK_DECREMENT_AND_SAVE_USER_RECEIVE_LUA_PATH)));
            redisScript.setResultType(Long.class);
            return redisScript;
        });

        // 验证用户是否符合优惠券领取条件
        JSONObject receiveRule = JSON.parseObject(couponTemplate.getReceiveRule());
        String limitPerPerson = receiveRule.getString("limitPerPerson");

        // 执行 LUA 脚本进行扣减库存以及增加 Redis 用户领券记录次数
        String couponTemplateCacheKey = String.format(EngineRedisConstant.COUPON_TEMPLATE_KEY, requestParam.getCouponTemplateId());
        String userCouponTemplateLimitCacheKey = String.format(EngineRedisConstant.USER_COUPON_TEMPLATE_LIMIT_KEY, UserContext.getUserId(), requestParam.getCouponTemplateId());
        Long stockDecrementLuaResult = stringRedisTemplate.execute(
                buildLuaScript,
                ListUtil.of(couponTemplateCacheKey, userCouponTemplateLimitCacheKey),
                String.valueOf(couponTemplate.getValidEndTime().getTime()), limitPerPerson
        );

        // 判断 LUA 脚本执行返回类，如果失败根据类型返回报错提示
        long firstField = StockDecrementReturnCombinedUtil.extractFirstField(stockDecrementLuaResult);
        if (RedisStockDecrementErrorEnum.isFailedCode(firstField)) {
            throw new ServiceException(RedisStockDecrementErrorEnum.getMessageByCode(firstField));
        }

        UserCouponRedeemEvent.UserCouponRedeemMessage userCouponRedeemMessage = UserCouponRedeemEvent.UserCouponRedeemMessage.builder()
                .requestParam(requestParam)
                .receiveCount((int) StockDecrementReturnCombinedUtil.extractSecondField(stockDecrementLuaResult))
                // TODO:这里的用户id应该是领券的用户id，而不是商家用户id
                .couponTemplate(couponTemplate)
                .userId(UserContext.getUserId())
                .build();
        // TODO:这里其实可以给rabbitmq的发送添加返回值，通过返回值判断send是否成功，如果失败，则抛出异常
        eventPublisher.publish(userCouponRedeemEvent.topic(), userCouponRedeemEvent.buildEventMessage(userCouponRedeemMessage));

    }
}
