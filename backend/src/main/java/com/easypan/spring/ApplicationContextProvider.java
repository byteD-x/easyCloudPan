package com.easypan.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 应用上下文提供者，用于获取 Spring 容器中的 Bean.
 */
@Component("applicationContextProvider")
@SuppressWarnings({"all", "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD"})
public class ApplicationContextProvider implements ApplicationContextAware {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationContextProvider.class);

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ApplicationContextProvider.applicationContext = applicationContext;
    }

    /**
     * 获取应用上下文.
     *
     * @return 应用上下文
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 通过名称获取 Bean.
     *
     * @param name Bean 名称
     * @return Bean 对象
     */
    public static Object getBean(String name) {
        try {
            return getApplicationContext().getBean(name);
        } catch (NoSuchBeanDefinitionException e) {
            logger.error("获取bean异常", e);
            return null;
        }

    }

    /**
     * 通过类型获取 Bean.
     *
     * @param clazz Bean 类型
     * @param <T> Bean 类型
     * @return Bean 对象
     */
    @SuppressWarnings("null")
    public static <T> T getBean(Class<T> clazz) {
        return getApplicationContext().getBean(clazz);
    }

    /**
     * 通过名称和类型获取 Bean.
     *
     * @param name Bean 名称
     * @param clazz Bean 类型
     * @param <T> Bean 类型
     * @return Bean 对象
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return getApplicationContext().getBean(name, clazz);
    }
}