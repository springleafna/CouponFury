package com.springleaf.couponfury.framework.config;

import com.springleaf.couponfury.framework.web.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;

/**
 * Web 组件自动装配
 */
public class WebAutoConfiguration {

    /**
     * 构建全局异常拦截器组件 Bean
     */
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
