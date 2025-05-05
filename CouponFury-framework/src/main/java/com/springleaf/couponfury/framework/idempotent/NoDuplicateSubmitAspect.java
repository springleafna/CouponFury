package com.springleaf.couponfury.framework.idempotent;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.springleaf.couponfury.framework.exception.ClientException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Aspect
public class NoDuplicateSubmitAspect {

    private final RedissonClient redissonClient;

    public NoDuplicateSubmitAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 1. @Around 用于环绕增强方法，执行前后逻辑。
     * 2. @annotation指定匹配被NoDuplicateSubmit注解标记的方法。
     * 3. 当方法被该注解标记时，触发noDuplicateSubmit方法，执行防重复提交的逻辑，如分布式锁控制。
     */
    @Around("@annotation(com.springleaf.couponfury.framework.idempotent.NoDuplicateSubmit)")
    public Object noDuplicateSubmit(ProceedingJoinPoint joinPoint) throws Throwable {
        NoDuplicateSubmit noDuplicateSubmit = getNoDuplicateSubmitAnnotation(joinPoint);
        // 获取分布式锁标识
        String lockKey = String.format("no-duplicate-submit:path:%s:currentUserId:%s:md5:%s", getServletPath(), getCurrentUserId(), calcArgsMD5(joinPoint));
        RLock lock = redissonClient.getLock(lockKey);
        // 尝试获取锁，获取锁失败就意味着已经重复提交，直接抛出异常
        if (!lock.tryLock()) {
            throw new ClientException(noDuplicateSubmit.message());
        }
        Object result;
        try {
            // 执行标记了防重复提交注解的方法原逻辑
            result = joinPoint.proceed();
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * @return 返回自定义防重复提交注解
     * 该方法通过AOP连接点获取目标方法的NoDuplicateSubmit注解。
     * 步骤：1.解析连接点获取方法签名
     * 2.通过反射定位目标方法
     * 3.提取方法上的防重提交注解
     *
     * 当一个方法被标记为 @Around 通知时，Spring AOP会在方法执行时创建一个 ProceedingJoinPoint 对象，并将其作为参数传递给通知方法。
     * 它提供了 proceed() 方法，用于执行目标方法。在环绕通知中，proceed() 方法可以被调用零次、一次或多次。
     * 通过 ProceedingJoinPoint，你可以在环绕通知中执行被增强的方法，并且可以获取方法的签名、参数、目标对象等信息。
     */
    public static NoDuplicateSubmit getNoDuplicateSubmitAnnotation(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method targetMethod = joinPoint.getTarget().getClass().getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());
        return targetMethod.getAnnotation(NoDuplicateSubmit.class);
    }

    /**
     * @return 获取当前线程上下文 ServletPath
     */
    private String getServletPath() {
        ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (sra != null) {
            return sra.getRequest().getServletPath();
        }
        return null;
    }

    /**
     * @return 当前操作用户 ID
     */
    private String getCurrentUserId() {
        // 用户属于非核心功能，这里先通过模拟的形式代替。后续如果需要后管展示，会重构该代码
        return "1810518709471555585";
    }

    /**
     * @return joinPoint md5
     * 代码中是将传入的参数(方法的参数数组)转成byte数组后进行MD5加密 生成了一个哈希值
     * 作为唯一标识 这样如果再次提交相同的参数 生成的哈希值也必然一样
     * 若访问路径一样 操作人也还一样的话 拿到的lockKey自然也就一样 上锁就失败了
     */
    private String calcArgsMD5(ProceedingJoinPoint joinPoint) {
        return DigestUtil.md5Hex(JSON.toJSONBytes(joinPoint.getArgs()));
    }
}
