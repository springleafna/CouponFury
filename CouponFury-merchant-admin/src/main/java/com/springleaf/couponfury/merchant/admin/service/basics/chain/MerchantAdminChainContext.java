package com.springleaf.couponfury.merchant.admin.service.basics.chain;

import com.springleaf.couponfury.merchant.admin.service.handle.filter.CouponTemplateCreateParamBaseVerifyChainFilter;
import com.springleaf.couponfury.merchant.admin.service.handle.filter.CouponTemplateCreateParamNotNullChainFilter;
import com.springleaf.couponfury.merchant.admin.service.handle.filter.CouponTemplateCreateParamVerifyChainFilter;
import org.springframework.beans.BeansException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 商家后管责任链上下文容器
 * ApplicationContextAware 接口获取应用上下文，并复制局部变量方便后续使用；CommandLineRunner 项目启动后执行责任链容器的填充工作
 */
@Component
public class MerchantAdminChainContext<T> implements ApplicationContextAware, CommandLineRunner {

    /**
     * 应用上下文，我们这里通过 Spring IOC 获取 Bean 实例
     */
    private ApplicationContext applicationContext;

    /**
     * 保存商家后管责任链实现类
     * <p>
     * Key：{@link MerchantAdminAbstractChainHandler#mark()}
     * Val：{@link MerchantAdminAbstractChainHandler} 一组责任链实现 Spring Bean 集合
     * <p>
     * 比如有一个优惠券模板创建责任链，实例如下：
     * Key：MERCHANT_ADMIN_CREATE_COUPON_TEMPLATE_KEY
     * Val：
     * - 验证优惠券信息基本参数是否必填 —— 执行器 {@link CouponTemplateCreateParamNotNullChainFilter}
     * - 验证优惠券信息基本参数是否按照格式传递 —— 执行器 {@link CouponTemplateCreateParamBaseVerifyChainFilter}
     * - 验证优惠券信息基本参数是否正确，比如商品数据是否存在等 —— 执行器 {@link CouponTemplateCreateParamVerifyChainFilter}
     * - ......
     */
    private final Map<String, List<MerchantAdminAbstractChainHandler>> abstractChainHandlerContainer = new HashMap<>();


    /**
     * 责任链组件执行
     *
     * @param mark         责任链组件标识
     * @param requestParam 请求参数
     */
    public void handler(String mark, T requestParam) {
        // 根据 mark 标识从责任链容器中获取一组责任链实现 Bean 集合
        List<MerchantAdminAbstractChainHandler> abstractChainHandlers = abstractChainHandlerContainer.get(mark);
        if (CollectionUtils.isEmpty(abstractChainHandlers)) {
            throw new RuntimeException(String.format("[%s] Chain of Responsibility ID is undefined.", mark));
        }
        abstractChainHandlers.forEach(each -> each.handler(requestParam));
    }

    /**
     * 这个方法实现了 CommandLineRunner 接口的 run 方法，它会在Spring应用上下文加载完成后执行。
     * CommandLineRunner 是Spring Boot提供的一个接口，用于在Spring应用启动后执行一些初始化任务。
     * 该接口包含一个 run 方法，该方法会在Spring应用启动完成后自动调用。
     *
     * 执行步骤：
     * 1.加载应用上下文：Spring Boot应用启动时，首先加载并初始化所有配置的Bean。
     * 2.调用 run 方法：在应用上下文完全加载后，Spring会调用 MerchantAdminChainContext 类的 run 方法。
     * 3.组织责任链处理器：
     * -从Spring的IOC容器中获取所有 MerchantAdminAbstractChainHandler 类型的Bean。
     * -根据每个处理器的 mark 值，将它们组织成责任链。
     * -对每个标识符（mark）对应的责任链处理器列表进行排序。
     */
    @Override
    public void run(String... args) throws Exception {
        // 从 Spring IOC 容器中获取指定接口 Spring Bean 集合
        Map<String, MerchantAdminAbstractChainHandler> chainFilterMap = applicationContext.getBeansOfType(MerchantAdminAbstractChainHandler.class);
        chainFilterMap.forEach((beanName, bean) -> {
            // 判断 Mark 是否已经存在抽象责任链容器中，如果已经存在直接向集合新增；如果不存在，创建 Mark 和对应的集合
            List<MerchantAdminAbstractChainHandler> abstractChainHandlers = abstractChainHandlerContainer.getOrDefault(bean.mark(), new ArrayList<>());
            abstractChainHandlers.add(bean);
            abstractChainHandlerContainer.put(bean.mark(), abstractChainHandlers);
        });
        abstractChainHandlerContainer.forEach((mark, unsortedChainHandlers) -> {
            // 对每个 Mark 对应的责任链实现类集合进行排序，优先级小的在前
            unsortedChainHandlers.sort(Comparator.comparing(Ordered::getOrder));
        });
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
