/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.southbound;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.services.util.StorageDriverManager;

public class StorageDriverManagerPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(StorageDriverManagerPostProcessor.class);
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!StringUtils.equals(beanName, StorageDriverManager.STORAGE_DRIVER_MANAGER)) {
            return bean;
        }
        StorageDriverManagerProxy proxy = new StorageDriverManagerProxy();
        proxy.setManager((StorageDriverManager) bean);
        DbClient dbClient = (DbClient)((StorageDriverManager) bean).getApplicationContext().getBean("dbclient");
        proxy.setDbClient(dbClient);
        log.info("StorageDriverManager instance has been substituted in apisvc");
        return proxy;
    }
}
