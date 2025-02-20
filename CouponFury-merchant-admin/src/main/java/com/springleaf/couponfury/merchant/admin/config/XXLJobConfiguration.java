package com.springleaf.couponfury.merchant.admin.config;

import cn.hutool.core.util.StrUtil;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * XXL-Job 配置类
 */
@Configuration
// @ConditionalOnProperty主要用于在特定条件下启用或禁用 Spring Bean 或配置。
// 通过该注解，可以实现基于配置属性的条件化 Bean 装配
/*
prefix：表示要检查的属性的前缀为 xxl-job。
name：指定要检查的属性名称是 enabled，结合前缀，完整的属性名称为 xxl-job.enabled。
havingValue：指定属性 xxl-job.enabled 的值应该为 "true"，当且仅当属性值匹配 "true" 时，相关的 Bean 或配置才会被加载。
matchIfMissing：如果未找到属性 xxl-job.enabled，将默认视为匹配。*/
@ConditionalOnProperty(prefix = "xxl-job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class XXLJobConfiguration {

    @Value("${xxl-job.admin.addresses:}")
    private String adminAddresses;

    @Value("${xxl-job.access-token:}")
    private String accessToken;

    @Value("${xxl-job.executor.application-name}")
    private String applicationName;

    @Value("${xxl-job.executor.ip}")
    private String ip;

    @Value("${xxl-job.executor.port}")
    private int port;

    @Value("${xxl-job.executor.log-path:}")
    private String logPath;

    @Value("${xxl-job.executor.log-retention-days}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(applicationName);
        xxlJobSpringExecutor.setIp(ip);
        xxlJobSpringExecutor.setPort(port);
        xxlJobSpringExecutor.setAccessToken(StrUtil.isNotEmpty(accessToken) ? accessToken : null);
        xxlJobSpringExecutor.setLogPath(StrUtil.isNotEmpty(logPath) ? logPath : Paths.get("").toAbsolutePath().getParent() + "/tmp");
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);
        return xxlJobSpringExecutor;
    }
}
