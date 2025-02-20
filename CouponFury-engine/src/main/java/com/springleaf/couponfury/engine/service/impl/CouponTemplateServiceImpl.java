package com.springleaf.couponfury.engine.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.map.MapUtil;
import com.springleaf.couponfury.engine.common.constant.EngineRedisConstant;
import com.springleaf.couponfury.engine.common.context.UserContext;
import com.springleaf.couponfury.engine.dao.entity.CouponTemplateDO;
import com.springleaf.couponfury.engine.dao.mapper.CouponTemplateMapper;
import com.springleaf.couponfury.engine.dto.req.CouponTemplateQueryReqDTO;
import com.springleaf.couponfury.engine.dto.resp.CouponTemplateQueryRespDTO;
import com.springleaf.couponfury.engine.service.CouponTemplateService;
import com.springleaf.couponfury.framework.exception.ClientException;
import jakarta.annotation.Resource;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 优惠券模板业务逻辑实现层
 */
@Service
public class CouponTemplateServiceImpl implements CouponTemplateService {

    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RBloomFilter<String> couponTemplateQueryBloomFilter;

    @Override
    public CouponTemplateQueryRespDTO findCouponTemplate(CouponTemplateQueryReqDTO requestParam) {
        // 查询 Redis 缓存中是否存在优惠券模板信息
        String couponTemplateCacheKey = String.format(EngineRedisConstant.COUPON_TEMPLATE_KEY, requestParam.getCouponTemplateId());
        Map<Object, Object> couponTemplateCacheMap = stringRedisTemplate.opsForHash().entries(couponTemplateCacheKey);

        // 如果存在直接返回，不存在需要通过布隆过滤器、缓存空值以及双重判定锁的形式读取数据库中的记录
        if (MapUtil.isEmpty(couponTemplateCacheMap)) {
            // 判断布隆过滤器是否存在指定模板ID，不存在直接返回错误
            if (!couponTemplateQueryBloomFilter.contains(String.valueOf(requestParam.getCouponTemplateId()))) {
                throw new ClientException("优惠券模板不存在或已过期");
            }

            // 查询 Redis 缓存中是否存在优惠券模板空值信息，如果有代表模板不存在，直接返回
            String couponTemplateIsNullCacheKey = String.format(EngineRedisConstant.COUPON_TEMPLATE_IS_NULL_KEY, requestParam.getCouponTemplateId());
            Boolean hasKeyFlag = stringRedisTemplate.hasKey(couponTemplateIsNullCacheKey);
            if (Boolean.TRUE.equals(hasKeyFlag)) {
                throw new ClientException("优惠券模板不存在或已过期");
            }

            // 获取优惠券模板分布式锁
            RLock lock = redissonClient.getLock(String.format(EngineRedisConstant.LOCK_COUPON_TEMPLATE_KEY, requestParam.getCouponTemplateId()));
            lock.lock();

            try {
                // 双重判定空值缓存是否存在，存在则继续抛异常
                hasKeyFlag = stringRedisTemplate.hasKey(couponTemplateIsNullCacheKey);
                if (Boolean.TRUE.equals(hasKeyFlag)) {
                    throw new ClientException("优惠券模板不存在或已过期");
                }

                // 通过双重判定锁优化大量请求无意义查询数据库
                couponTemplateCacheMap = stringRedisTemplate.opsForHash().entries(couponTemplateCacheKey);
                if (MapUtil.isEmpty(couponTemplateCacheMap)) {
                    CouponTemplateDO couponTemplateDO = couponTemplateMapper.getCouponTemplateByShopNumberAndId(UserContext.getShopNumber(), requestParam.getCouponTemplateId());

                    // 优惠券模板不存在或者已过期加入空值缓存，并且抛出异常
                    if (couponTemplateDO == null) {
                        stringRedisTemplate.opsForValue().set(couponTemplateIsNullCacheKey, "", 30, TimeUnit.MINUTES);
                        throw new ClientException("优惠券模板不存在或已过期");
                    }

                    // 通过将数据库的记录序列化成 JSON 字符串放入 Redis 缓存
                    CouponTemplateQueryRespDTO actualRespDTO = BeanUtil.toBean(couponTemplateDO, CouponTemplateQueryRespDTO.class);
                    // 将actualRespDTO转换为Map
                    Map<String, Object> cacheTargetMap = BeanUtil.beanToMap(actualRespDTO, false, true);
                    // 将Map中的null值转为""
                    Map<String, String> actualCacheTargetMap = cacheTargetMap.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> entry.getValue() != null ? entry.getValue().toString() : ""
                            ));

                    // 通过 LUA 脚本执行设置 Hash 数据以及设置过期时间
                    String luaScript = "redis.call('HMSET', KEYS[1], unpack(ARGV, 1, #ARGV - 1)) " +
                            "redis.call('EXPIREAT', KEYS[1], ARGV[#ARGV])";

                    List<String> keys = Collections.singletonList(couponTemplateCacheKey);
                    List<String> args = new ArrayList<>(actualCacheTargetMap.size() * 2 + 1);
                    actualCacheTargetMap.forEach((key, value) -> {
                        args.add(key);
                        args.add(value);
                    });

                    // 优惠券活动过期时间转换为秒级别的 Unix 时间戳
                    args.add(String.valueOf(couponTemplateDO.getValidEndTime().getTime() / 1000));

                    // 执行 LUA 脚本
                    stringRedisTemplate.execute(
                            new DefaultRedisScript<>(luaScript, Long.class),
                            keys,
                            args.toArray()
                    );
                    couponTemplateCacheMap = cacheTargetMap.entrySet()
                            .stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                }
            } finally {
                lock.unlock();
            }
        }
        return BeanUtil.mapToBean(couponTemplateCacheMap, CouponTemplateQueryRespDTO.class, false, CopyOptions.create());
    }

    /**
     * 缓存击穿解决方案
     */
    public CouponTemplateQueryRespDTO findCouponTemplateV1(CouponTemplateQueryReqDTO requestParam) {
        // 查询 Redis 缓存中是否存在优惠券模板信息
        String couponTemplateCacheKey = String.format(EngineRedisConstant.COUPON_TEMPLATE_KEY, requestParam.getCouponTemplateId());
        Map<Object, Object> couponTemplateCacheMap = stringRedisTemplate.opsForHash().entries(couponTemplateCacheKey);

        // 如果存在直接返回，不存在需要通过双重判定锁的形式读取数据库中的记录
        if (MapUtil.isEmpty(couponTemplateCacheMap)) {
            // 获取优惠券模板分布式锁
            RLock lock = redissonClient.getLock(String.format(EngineRedisConstant.LOCK_COUPON_TEMPLATE_KEY, requestParam.getCouponTemplateId()));
            lock.lock();

            try {
                // 通过双重判定锁优化大量请求无意义查询数据库
                couponTemplateCacheMap = stringRedisTemplate.opsForHash().entries(couponTemplateCacheKey);
                if (MapUtil.isEmpty(couponTemplateCacheMap)) {
                    CouponTemplateDO couponTemplateDO = couponTemplateMapper.getCouponTemplateByShopNumberAndId(UserContext.getShopNumber(), requestParam.getCouponTemplateId());

                    // 优惠券模板不存在或者已过期直接抛出异常
                    if (couponTemplateDO == null) {
                        throw new ClientException("优惠券模板不存在或已过期");
                    }

                    // 通过将数据库的记录序列化成 JSON 字符串放入 Redis 缓存
                    CouponTemplateQueryRespDTO actualRespDTO = BeanUtil.toBean(couponTemplateDO, CouponTemplateQueryRespDTO.class);
                    // 将actualRespDTO转换为Map
                    Map<String, Object> cacheTargetMap = BeanUtil.beanToMap(actualRespDTO, false, true);
                    // 将Map中的null值转为""
                    Map<String, String> actualCacheTargetMap = cacheTargetMap.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> entry.getValue() != null ? entry.getValue().toString() : ""
                            ));

                    // 通过 LUA 脚本执行设置 Hash 数据以及设置过期时间
                    String luaScript = "redis.call('HMSET', KEYS[1], unpack(ARGV, 1, #ARGV - 1)) " +
                            "redis.call('EXPIREAT', KEYS[1], ARGV[#ARGV])";

                    List<String> keys = Collections.singletonList(couponTemplateCacheKey);
                    List<String> args = new ArrayList<>(actualCacheTargetMap.size() * 2 + 1);
                    actualCacheTargetMap.forEach((key, value) -> {
                        args.add(key);
                        args.add(value);
                    });

                    // 优惠券活动过期时间转换为秒级别的 Unix 时间戳
                    args.add(String.valueOf(couponTemplateDO.getValidEndTime().getTime() / 1000));

                    // 执行 LUA 脚本
                    stringRedisTemplate.execute(
                            new DefaultRedisScript<>(luaScript, Long.class),
                            keys,
                            args.toArray()
                    );
                    couponTemplateCacheMap = cacheTargetMap.entrySet()
                            .stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                }
            } finally {
                lock.unlock();
            }
        }
        return BeanUtil.mapToBean(couponTemplateCacheMap, CouponTemplateQueryRespDTO.class, false, CopyOptions.create());
    }

    /**
     * 缓存穿透解决方案之布隆过滤器
     */
    public CouponTemplateQueryRespDTO findCouponTemplateV2(CouponTemplateQueryReqDTO requestParam) {
        // 判断布隆过滤器是否存在指定模板 ID，不存在直接返回错误
        if (!couponTemplateQueryBloomFilter.contains(String.valueOf(requestParam.getCouponTemplateId()))) {
            throw new ClientException("优惠券模板不存在");
        }

        CouponTemplateDO couponTemplateDO = couponTemplateMapper.getCouponTemplateByShopNumberAndId(UserContext.getShopNumber(), requestParam.getCouponTemplateId());

        return BeanUtil.toBean(couponTemplateDO, CouponTemplateQueryRespDTO.class);
    }
}
