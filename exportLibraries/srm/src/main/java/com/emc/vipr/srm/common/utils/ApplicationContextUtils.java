package com.emc.vipr.srm.common.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Utility class to return a reference to the Spring Application Context from within non-Spring enabled beans. This class has
 * to be initialized on application startup.
 *
 * 
 */
public class ApplicationContextUtils implements ApplicationContextAware {

    private static ApplicationContext appContext;

    /**
     * This method is called from within the ApplicationContext once it is done initializing.
     *
     * @param context
     *            a reference to the ApplicationContext.
     */
    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        appContext = context;
    }

    /**
     * Static method to get access to the bean by name. Invokers of this method must cast to the appropriate class else a
     * Runtime error will be thrown
     *
     * @param beanName
     *            - name of the bean to get.
     * @return an Object reference to the named bean.
     */
    public static Object getBean(final String beanName) {
        return appContext.getBean(beanName);
    }

}

