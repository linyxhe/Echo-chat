package com.echo.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring上下文工具类，用于获取Spring容器中的Bean
 */
@Component
public class SpringContextUtil implements ApplicationContextAware {
    
    private static ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextUtil.applicationContext = applicationContext;
    }
    
    /**
     * 获取Spring容器中的Bean
     * 
     * @param beanName Bean名称
     * @return Bean实例
     */
    public static Object getBean(String beanName) {
        return applicationContext.getBean(beanName);
    }
    
    /**
     * 获取Spring容器中的Bean
     * 
     * @param clazz Bean类型
     * @param <T> 泛型类型
     * @return Bean实例
     */
    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }
    
    /**
     * 获取Spring容器中的Bean
     * 
     * @param beanName Bean名称
     * @param clazz Bean类型
     * @param <T> 泛型类型
     * @return Bean实例
     */
    public static <T> T getBean(String beanName, Class<T> clazz) {
        return applicationContext.getBean(beanName, clazz);
    }
}