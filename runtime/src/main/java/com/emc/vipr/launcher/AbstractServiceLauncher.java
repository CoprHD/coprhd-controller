/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.launcher;

import java.io.File;
import java.lang.reflect.Method;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public abstract class AbstractServiceLauncher implements Runnable {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected String serviceName;
    protected String confName;

    public AbstractServiceLauncher(String name) {
        this(name, StringUtils.removeEnd(name, "svc"));
    }

    public AbstractServiceLauncher(String serviceName, String confName) {
        this.serviceName = serviceName;
        this.confName = confName;
    }

    public void launch() {
        new Thread(this, serviceName).start();
    }

    @Override
    public void run() {
        try {
            log.info("Starting service: {}", serviceName);
            runService();
        }
        catch (Exception e) {
            log.error("Could not start service: {}", serviceName, e);
            System.exit(-1);
        }
    }

    protected abstract void runService() throws Exception;

    /**
     * Starts a bean, by invoking the 'start' method.
     * 
     * @param beanName
     *        the name of the bean in the context.
     * 
     * @throws Exception
     *         if an exception occurs.
     */
    protected void startBean(String beanName) throws Exception {
        Object bean = getBean(beanName);
        Method start = bean.getClass().getMethod("start");
        start.invoke(bean);
    }

    protected File getHomeDir() {
        String homeDir = System.getProperty("product.home");
        if (StringUtils.isBlank(homeDir)) {
            throw new IllegalStateException("product.home is not set");
        }
        return new File(homeDir).getAbsoluteFile();
    }

    protected File getConfDir() {
        return new File(getHomeDir(), "conf");
    }

    protected FileSystemXmlApplicationContext createContext() {
        File confDir = getConfDir();
        File confFile = new File(confDir, String.format("%s-conf.xml", confName));
        return new FileSystemXmlApplicationContext(confFile.toURI().toString());
    }

    @SuppressWarnings("unchecked")
    protected <T> T getBean(String name) {
        FileSystemXmlApplicationContext context = createContext();
        return (T) context.getBean(name);
    }

    protected <T> T getServiceBean() {
        return getBean(serviceName);
    }
}
