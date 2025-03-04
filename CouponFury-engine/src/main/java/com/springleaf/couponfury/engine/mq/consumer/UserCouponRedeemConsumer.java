package com.springleaf.couponfury.engine.mq.consumer;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.springleaf.couponfury.engine.common.constant.EngineRedisConstant;
import com.springleaf.couponfury.engine.common.context.UserContext;
import com.springleaf.couponfury.engine.common.enums.UserCouponStatusEnum;
import com.springleaf.couponfury.engine.dao.entity.UserCouponDO;
import com.springleaf.couponfury.engine.dao.mapper.CouponTemplateMapper;
import com.springleaf.couponfury.engine.dao.mapper.UserCouponMapper;
import com.springleaf.couponfury.engine.dto.req.CouponTemplateRedeemReqDTO;
import com.springleaf.couponfury.engine.dto.resp.CouponTemplateQueryRespDTO;
import com.springleaf.couponfury.engine.mq.event.BaseEvent;
import com.springleaf.couponfury.engine.mq.event.UserCouponRedeemEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 用户兑换优惠券消息消费者
 */
@Slf4j(topic = "UserCouponRedeemConsumer")
@Component
public class UserCouponRedeemConsumer {
    @Value("${spring.rabbitmq.topic.user-coupon-redeem}")
    private String topic;

    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private UserCouponMapper userCouponMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queuesToDeclare = @Queue(value = "user.coupon.redeem"))
    public void listener(String message) {
        try {
            log.info("[消费者] 用户兑换优惠券 - 执行消费逻辑，topic: {}, message: {}", topic, message);
            // 转换对象
            BaseEvent.EventMessage<UserCouponRedeemEvent.UserCouponRedeemMessage> eventMessage = JSON.parseObject(message,
                    new TypeReference<BaseEvent.EventMessage<UserCouponRedeemEvent.UserCouponRedeemMessage>>() {
                    }.getType());

            UserCouponRedeemEvent.UserCouponRedeemMessage messageData = eventMessage.getData();
            CouponTemplateRedeemReqDTO requestParam = messageData.getRequestParam();
            CouponTemplateQueryRespDTO couponTemplate = messageData.getCouponTemplate();
            String userId = messageData.getUserId();
            Integer receiveCount = messageData.getReceiveCount();

            // 进行优惠券模板的扣减库存操作
            int decremented = couponTemplateMapper.decrementCouponTemplateStock(requestParam.getShopNumber(), requestParam.getCouponTemplateId(), 1L);
            if (decremented <= 0) {
                log.warn("[消费者] 用户兑换优惠券 - 执行消费逻辑，扣减优惠券数据库库存失败，topic: {}, message: {}", topic, message);
                return;
            }

            // 添加 Redis 用户领取的优惠券记录列表
            Date now = new Date();
            DateTime validEndTime = DateUtil.offsetHour(now, JSON.parseObject(couponTemplate.getConsumeRule()).getInteger("validityPeriod"));
            UserCouponDO userCouponDO = UserCouponDO.builder()
                    .couponTemplateId(requestParam.getCouponTemplateId())
                    .userId(Long.parseLong(userId))
                    .source(requestParam.getSource())
                    .receiveCount(receiveCount)
                    .status(UserCouponStatusEnum.UNUSED.getCode())
                    .receiveTime(now)
                    .validStartTime(now)
                    .validEndTime(validEndTime)
                    .build();
            userCouponMapper.saveUserCoupon(userCouponDO);

            // 添加用户领取优惠券模板缓存记录
            String userCouponListCacheKey = String.format(EngineRedisConstant.USER_COUPON_TEMPLATE_LIST_KEY, userId);
            String userCouponItemCacheKey = StrUtil.builder()
                    .append(requestParam.getCouponTemplateId())
                    .append("_")
                    .append(userCouponDO.getId())
                    .toString();
            stringRedisTemplate.opsForZSet().add(userCouponListCacheKey, userCouponItemCacheKey, now.getTime());

            // 由于 Redis 在持久化或主从复制的极端情况下可能会出现数据丢失，而我们对指令丢失几乎无法容忍，因此我们采用经典的写后查询策略来应对这一问题
            Double scored;
            try {
                scored = stringRedisTemplate.opsForZSet().score(userCouponListCacheKey, userCouponItemCacheKey);
                // scored 为空意味着可能 Redis Cluster 主从同步丢失了数据，比如 Redis 主节点还没有同步到从节点就宕机了，解决方案就是再新增一次
                if (scored == null) {
                    // 如果这里也新增失败了怎么办？我们大概率做不到绝对的万无一失，只能尽可能增加成功率
                    stringRedisTemplate.opsForZSet().add(userCouponListCacheKey, userCouponItemCacheKey, now.getTime());
                }
            } catch (Throwable e) {
                log.warn("[消费者] 用户兑换优惠券 - 执行消费逻辑，查询Redis用户优惠券记录为空或抛异常，可能Redis宕机或主从复制数据丢失，基础错误信息：{}", e.getMessage());
                // 如果直接抛异常大概率 Redis 宕机了，所以应该写个延时队列向 Redis 重试放入值。为了避免代码复杂性，这里直接写新增，大家知道最优解决方案即可
                stringRedisTemplate.opsForZSet().add(userCouponListCacheKey, userCouponItemCacheKey, now.getTime());
            }
        } catch (Exception e) {
            log.error("监听[消费者] 用户兑换优惠券 - 消费失败 topic: {} message: {}", topic, message);
            throw e;
        }
    }
}
