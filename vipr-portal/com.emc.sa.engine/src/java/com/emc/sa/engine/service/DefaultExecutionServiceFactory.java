/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.service;

import java.util.Arrays;
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

import com.emc.sa.catalog.ExtentionClassLoader;
import com.emc.sa.descriptor.ServiceDefinitionLoader;
import com.emc.sa.descriptor.TestExternalInterface;
import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.google.common.collect.Maps;

@Component
public class DefaultExecutionServiceFactory implements ExecutionServiceFactory, ApplicationContextAware {
    private static final Logger LOG = Logger.getLogger(DefaultExecutionServiceFactory.class);

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
    	Class<? extends ExecutionService> serviceClass=null;
    	String serviceName = catalogService.getBaseService();
    	boolean isExtended = false;
        
    	if(serviceName.endsWith("@Extension")){
    		serviceName=serviceName.substring(0, serviceName.length()-"@Extension".length());
    		serviceClass = services.get("GenericPlugin");
    		isExtended=true;
    		return newInstance(serviceClass, serviceName,isExtended);

    	} else if (serviceName.endsWith("Extension")){
    		serviceName=serviceName.substring(0, serviceName.length()-"Extension".length());
    	}

    	serviceClass = services.get(serviceName);
    	        
        
        if (serviceClass == null) {
            throw new ServiceNotFoundException(String.format("Service '%s' not found", serviceName));
        }
        return newInstance(serviceClass, serviceName);
    }

    private ExecutionService newInstance(Class<? extends ExecutionService> serviceClass, String serviceName, boolean isExtended) {
    	ExecutionService executionService=newInstance(serviceClass, "GenericPlugin");
    	if (executionService instanceof ExternalTaskExecutor){
    		ExternalTaskApdapterInterface task=	 ExtentionClassLoader.getProxyObject("com.emc.sa.service.vipr.plugins.tasks."+serviceName);
			((ExternalTaskExecutor) executionService).setGenericExtensionTask(task);
    	}
    	
    	return executionService;
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

    
    public static void main(String[] args) throws ServiceNotFoundException {
    	DefaultExecutionServiceFactory def = new DefaultExecutionServiceFactory();
    	

    	Order order = new Order();
    	
    	CatalogService catalogService = new CatalogService();
    	catalogService.setBaseService("CustomSample@Extension");

    	def.createService(order, catalogService);
    	
    	
	}
 }
