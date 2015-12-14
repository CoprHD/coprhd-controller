package com.emc.storageos.spring.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class BeanProvider implements ApplicationContextAware{

    private static ApplicationContext springContext;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        springContext = applicationContext;
    }
    
    public static <T> T getBean(Class<T> clazz) {
        return springContext.getBean(clazz);
    }

}
