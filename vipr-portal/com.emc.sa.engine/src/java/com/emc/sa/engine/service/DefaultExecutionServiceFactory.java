/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.service;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.UnhandledException;
import org.apache.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import com.emc.sa.catalog.CustomServicesWorkflowManager;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.google.common.collect.Maps;

@Component
public class DefaultExecutionServiceFactory implements ExecutionServiceFactory, ApplicationContextAware {
    private static final Logger LOG = Logger.getLogger(DefaultExecutionServiceFactory.class);

    @Autowired
    private CustomServicesWorkflowManager customServicesWorkflowManager;

    private Map<String, Class<? extends ExecutionService>> services = Maps.newHashMap();
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Autowired
    public void setServices(List<ExecutionService> values) {
        LOG.info("Loading Services");
        for (ExecutionService service : values) {
            Class<? extends ExecutionService> serviceClass = service.getClass();
            Service serviceDef = serviceClass.getAnnotation(Service.class);
            if (serviceDef != null) {
                String serviceName = serviceDef.value();
                services.put(serviceName, serviceClass);
                LOG.debug(String.format("Added Service '%s' => %s", serviceName, serviceClass.getName()));
            }
            else {
                LOG.warn(String.format("Service '%s' is missing a %s annotation", serviceClass.getName(),
                        Service.class.getSimpleName()));
            }
        }
    }

    @Override
    public ExecutionService createService(Order order, CatalogService catalogService) throws ServiceNotFoundException {
        String serviceName;
        if (null == catalogService) {
            // This is case of "Test Orders".
            // Getting service name from workflow (as there is no catalog service)
            final CustomServicesWorkflow customServicesWorkflow = customServicesWorkflowManager.getById(order.getCatalogServiceId());
            serviceName = customServicesWorkflow.getLabel();
        }
        else {
            serviceName = catalogService.getBaseService();
        }
        Class<? extends ExecutionService> serviceClass = services.get(serviceName);
        if (serviceClass == null) {
            // Check if service is created from workflow base service.
            // For these services there is only one executor - CustomServicesService
            if (isWorkflowService(serviceName)) {
                serviceClass = services.get("CustomServicesService");
            }
            else {
                throw new ServiceNotFoundException(String.format("Service '%s' not found", serviceName));
            }
        }
        return newInstance(serviceClass, serviceName);
    }

    private boolean isWorkflowService(String serviceName) {
        List<CustomServicesWorkflow> results = customServicesWorkflowManager.getByName(serviceName);
        if (null != results && !results.isEmpty()) {
            return true;
        }
        return false;
    }

    protected ExecutionService newInstance(Class<? extends ExecutionService> serviceClass, String serviceName) {
        try {
            ExecutionService newService = serviceClass.newInstance();
            AutowireCapableBeanFactory factory = applicationContext.getAutowireCapableBeanFactory();
            factory.autowireBean(newService);
            factory.initializeBean(newService, serviceName);
            return newService;
        } catch (InstantiationException e) {
            throw new UnhandledException(e);
        } catch (IllegalAccessException e) {
            throw new UnhandledException(e);
        }
    }
}
