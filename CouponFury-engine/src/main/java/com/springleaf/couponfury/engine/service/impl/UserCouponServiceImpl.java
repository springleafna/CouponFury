package com.springleaf.couponfury.engine.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Singleton;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.springleaf.couponfury.engine.common.constant.EngineRedisConstant;
import com.springleaf.couponfury.engine.common.context.UserContext;
import com.springleaf.couponfury.engine.common.enums.RedisStockDecrementErrorEnum;
import com.springleaf.couponfury.engine.dao.entity.CouponSettlementDO;
import com.springleaf.couponfury.engine.dao.entity.UserCouponDO;
import com.springleaf.couponfury.engine.dao.mapper.CouponSettlementMapper;
import com.springleaf.couponfury.engine.dao.mapper.UserCouponMapper;
import com.springleaf.couponfury.engine.dto.req.*;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import static com.springleaf.couponfury.engine.common.constant.EngineRedisConstant.USER_COUPON_TEMPLATE_LIST_KEY;
import static com.springleaf.couponfury.engine.common.enums.CouponSettlementEnum.*;
import static com.springleaf.couponfury.engine.common.enums.UserCouponStatusEnum.*;

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
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private CouponSettlementMapper couponSettlementMapper;
    @Resource
    private UserCouponMapper userCouponMapper;
    @Resource
    private TransactionTemplate transactionTemplate;

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

    @Override
    public void createPaymentRecord(CouponCreatePaymentReqDTO requestParam) {
        RLock lock = redissonClient.getLock(String.format(EngineRedisConstant.LOCK_COUPON_SETTLEMENT_KEY, requestParam.getCouponId()));
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            throw new ClientException("正在创建优惠券结算单，请稍候再试");
        }

        try {
            // 验证优惠券是否正在使用或者已经被使用
            // TODO:这里的用户id需要修改
            if (couponSettlementMapper.getCouponSettlementByCouponIdAndUserIdInStatus(requestParam.getCouponId(), Long.parseLong(UserContext.getUserId())) != null) {
                throw new ClientException("请检查优惠券是否已使用");
            }
            // TODO:这里的用户id需要修改
            UserCouponDO userCouponDO = userCouponMapper.getUserCouponByCouponIdAndUserId(requestParam.getCouponId(), Long.parseLong(UserContext.getUserId()));
            // 验证用户优惠券状态和有效性
            if (Objects.isNull(userCouponDO)) {
                throw new ClientException("优惠券不存在");
            }
            if (userCouponDO.getValidEndTime().before(new Date())) {
                throw new ClientException("优惠券已过期");
            }
            if (userCouponDO.getStatus() != 0) {
                throw new ClientException("优惠券使用状态异常");
            }

            // 获取优惠券模板和消费规则
            CouponTemplateQueryRespDTO couponTemplate = couponTemplateService.findCouponTemplate(
                    new CouponTemplateQueryReqDTO(requestParam.getShopNumber(), userCouponDO.getCouponTemplateId()));
            JSONObject consumeRule = JSONObject.parseObject(couponTemplate.getConsumeRule());

            // 计算折扣金额
            BigDecimal discountAmount;

            // 商品专属优惠券
            if (couponTemplate.getTarget().equals(0)) {
                // 获取第一个匹配的商品
                // 如果优惠的对象是商品，就从优惠券结算单的商品集合中找到与优惠券里的商品匹配的商品
                Optional<CouponCreatePaymentGoodsReqDTO> matchedGoods = requestParam.getGoodsList().stream()
                        .filter(each -> Objects.equals(couponTemplate.getGoods(), each.getGoodsNumber()))
                        .findFirst();

                if (matchedGoods.isEmpty()) {
                    throw new ClientException("商品信息与优惠券模板不符");
                }

                // 验证折扣金额
                CouponCreatePaymentGoodsReqDTO paymentGoods = matchedGoods.get();
                // “maximumDiscountAmount” 表示优惠券最大折扣金额
                BigDecimal maximumDiscountAmount = consumeRule.getBigDecimal("maximumDiscountAmount");
                // 计算优惠券结算单的商品价格折扣后是否与接收的商品折扣后金额一致
                if (!paymentGoods.getGoodsAmount().subtract(maximumDiscountAmount).equals(paymentGoods.getGoodsPayableAmount())) {
                    throw new ClientException("商品折扣后金额异常");
                }

                discountAmount = maximumDiscountAmount;
            } else { // 店铺专属
                // 检查店铺编号（如果是店铺券）
                if (couponTemplate.getSource() == 0 && !requestParam.getShopNumber().equals(couponTemplate.getShopNumber())) {
                    throw new ClientException("店铺编号不一致");
                }

                // “termsOfUse” 表示订单金额满足多少元可以使用优惠券，这里判断订单金额是否满足优惠券使用条件
                BigDecimal termsOfUse = consumeRule.getBigDecimal("termsOfUse");
                if (requestParam.getOrderAmount().compareTo(termsOfUse) < 0) {
                    throw new ClientException("订单金额未满足使用条件");
                }

                BigDecimal maximumDiscountAmount = consumeRule.getBigDecimal("maximumDiscountAmount");

                switch (couponTemplate.getType()) {
                    case 0 -> // 立减券
                            discountAmount = maximumDiscountAmount;
                    case 1 -> // 满减券
                            discountAmount = maximumDiscountAmount;
                    case 2 -> { // 折扣券
                        // 获取折扣率
                        BigDecimal discountRate = consumeRule.getBigDecimal("discountRate");
                        discountAmount = requestParam.getOrderAmount().multiply(discountRate);
                        if (discountAmount.compareTo(maximumDiscountAmount) >= 0) {
                            discountAmount = maximumDiscountAmount;
                        }
                    }
                    default -> throw new ClientException("无效的优惠券类型");
                }
            }

            // 计算折扣后金额并进行检查
            // 判断经过 扣减计算后的优惠券金额 与 接收的折扣后金额 是否一致
            BigDecimal actualPayableAmount = requestParam.getOrderAmount().subtract(discountAmount);
            if (actualPayableAmount.compareTo(requestParam.getPayableAmount()) != 0) {
                throw new ClientException("折扣后金额不一致");
            }

            // 创建优惠券结算单，并更新优惠券状态
            // 通过编程式事务减小事务范围
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    // 创建优惠券结算单记录
                    CouponSettlementDO couponSettlementDO = CouponSettlementDO.builder()
                            .orderId(requestParam.getOrderId())
                            .couponId(requestParam.getCouponId())
                            // TODO:这里的用户id应该是领券的用户id，而不是商家用户id
                            .userId(Long.parseLong(UserContext.getUserId()))
                            .status(0)
                            .build();
                    couponSettlementMapper.saveCouponSettlement(couponSettlementDO);

                    // 变更用户优惠券状态
                    // TODO:这里的用户id需要修改
                    userCouponMapper.updateUserCouponStatus(requestParam.getCouponId(), Long.parseLong(UserContext.getUserId()), UNUSED.getCode(), LOCKING.getCode());
                } catch (Exception e) {
                    log.error("创建优惠券结算单失败", e);
                    status.setRollbackOnly();
                    throw e;
                }
            });

            // 从用户可用优惠券列表中删除优惠券
            String userCouponItemCacheKey = StrUtil.builder()
                    .append(userCouponDO.getCouponTemplateId())
                    .append("_")
                    .append(userCouponDO.getId())
                    .toString();
            // TODO:这里的用户id需要修改
            stringRedisTemplate.opsForZSet().remove(String.format(USER_COUPON_TEMPLATE_LIST_KEY, UserContext.getUserId()), userCouponItemCacheKey);

        } finally {
            lock.unlock();
        }
    }

    @Override
    public void processPayment(CouponProcessPaymentReqDTO requestParam) {
        RLock lock = redissonClient.getLock(String.format(EngineRedisConstant.LOCK_COUPON_SETTLEMENT_KEY, requestParam.getCouponId()));
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            throw new ClientException("正在核销优惠券结算单，请稍候再试");
        }

        // 通过编程式事务减小事务范围
        transactionTemplate.executeWithoutResult(status -> {
            try {
                // 变更优惠券结算单状态为已支付
                int couponSettlementUpdated = couponSettlementMapper.updateCouponSettlementStatus(requestParam.getOrderId(), requestParam.getCouponId(), Long.parseLong(UserContext.getUserId()), LOCKED.getCode(), PAID.getCode());
                if (couponSettlementUpdated <= 0) {
                    log.error("核销优惠券结算单异常，请求参数：{}", JSON.toJSONString(requestParam));
                    throw new ServiceException("核销优惠券结算单异常");
                }

                // 变更用户优惠券状态
                int userCouponUpdated = userCouponMapper.updateUserCouponStatus(requestParam.getCouponId(), Long.parseLong(UserContext.getUserId()), LOCKING.getCode(), USED.getCode());
                if (userCouponUpdated <= 0) {
                    log.error("修改用户优惠券记录状态已使用异常，请求参数：{}", JSON.toJSONString(requestParam));
                    throw new ServiceException("修改用户优惠券记录状态异常");
                }
            } catch (Exception e) {
                log.error("核销优惠券结算单失败", e);
                status.setRollbackOnly();
                throw e;
            } finally {
                lock.unlock();
            }
        });
    }

    @Override
    public void processRefund(CouponProcessRefundReqDTO requestParam) {
        RLock lock = redissonClient.getLock(String.format(EngineRedisConstant.LOCK_COUPON_SETTLEMENT_KEY, requestParam.getCouponId()));
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            throw new ClientException("正在执行优惠券退款，请稍候再试");
        }

        try {
            // 通过编程式事务减小事务范围
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    // 变更优惠券结算单状态为已退款
                    int couponSettlementUpdated = couponSettlementMapper.updateCouponSettlementStatus(requestParam.getOrderId(), requestParam.getCouponId(), Long.parseLong(UserContext.getUserId()), PAID.getCode(), REFUNDED.getCode());
                    if (couponSettlementUpdated <= 0) {
                        log.error("优惠券结算单退款异常，请求参数：{}", JSON.toJSONString(requestParam));
                        throw new ServiceException("核销优惠券结算单异常");
                    }

                    // 变更用户优惠券状态
                    int userCouponUpdated = userCouponMapper.updateUserCouponStatus(requestParam.getCouponId(), Long.parseLong(UserContext.getUserId()), USED.getCode(), UNUSED.getCode());
                    if (userCouponUpdated <= 0) {
                        log.error("修改用户优惠券记录状态未使用异常，请求参数：{}", JSON.toJSONString(requestParam));
                        throw new ServiceException("修改用户优惠券记录状态异常");
                    }
                } catch (Exception e) {
                    log.error("执行优惠券结算单退款失败", e);
                    status.setRollbackOnly();
                    throw e;
                }
            });

            // 查询出来优惠券再放回缓存
            UserCouponDO userCouponDO = userCouponMapper.getUserCouponByCouponIdAndUserId(requestParam.getCouponId(), Long.parseLong(UserContext.getUserId()));

            String userCouponItemCacheKey = StrUtil.builder()
                    .append(userCouponDO.getCouponTemplateId())
                    .append("_")
                    .append(userCouponDO.getId())
                    .toString();
            stringRedisTemplate.opsForZSet().add(String.format(USER_COUPON_TEMPLATE_LIST_KEY, UserContext.getUserId()), userCouponItemCacheKey, userCouponDO.getReceiveTime().getTime());
        } finally {
            lock.unlock();
        }
    }
}
