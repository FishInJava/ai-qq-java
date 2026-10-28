package com.hobozoo.aiqq.common.util;

import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * @author hbz
 */
public class AnnotationUtil {

    /**
     * 场景1：SpringIOC初始化后，Bean中的含有非spring容器中的Bean
     * 假设初始化后已经通过某种方式注入成功，我见过一种方式，IOCBean初始化
     * 完成后，遍历IOC中所有Bean，找到有XXX注解的类，获取注解上的值，通过反射，
     * 把非IOCBean注入。
     *
     * 想要获取这类Bean，只能通过反射。
     * --Bean上有注解（例如NotSpringIOCBean）
     */
    void getBean(String name) throws Exception{
        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields) {
            NotSpringIOCBean annotation;
            if ((annotation = field.getAnnotation(NotSpringIOCBean.class)) != null && name.equals(annotation.name())) {
                ReflectionUtils.makeAccessible(field);
                field.get(this);
            }
        }
    }
}
