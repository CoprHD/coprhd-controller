/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;


import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.emc.sa.catalog.ExtentionClassLoader;

public class ServiceDefinitionLoader {
    public static final String PATTERN = "classpath*:com/**/*Service.json";

    private static final Logger LOG = Logger.getLogger(ServiceDefinitionLoader.class);

    public static List<ServiceDefinition> load(ClassLoader classLoader) throws IOException {
        ServiceDefinitionReader reader = new ServiceDefinitionReader();
        List<ServiceDefinition> services = new ArrayList<>();
        for (Resource resource : getResources(classLoader)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Reading service definition: " + resource.getDescription());
            }
            try (InputStream in = resource.getInputStream()) {
                ServiceDefinition service = reader.readService(in);
                if (service != null) {
                    if (!service.disabled) {
                        services.add(service);
                    }
                    else {
                        LOG.debug("Skipping disabled service");
                    }
                }
                else {
                    LOG.warn("Error reading service definition " + resource.getDescription());
                }
            } catch (IOException | RuntimeException e) {
                LOG.error("Error reading service definition: " + resource.getDescription(), e);
            }
        }
        
        List<ServiceDefinition> extenalServices = loadExternal(null);
        List<ServiceDefinition> modifiedExtenalServices = new ArrayList<ServiceDefinition>();
        
        for (ServiceDefinition extenalService : extenalServices) {
        	
        	if(extenalService.isExtended && extenalService.extendedServiceId !=null && !extenalService.extendedServiceId.equals("GenericPlugin")  ){
        		extenalService=extendServiceDefinition(services,extenalService.extendedServiceId,extenalService);
//        		baseServiceDef.serviceId=extenalService.serviceId;
//        		baseServiceDef.extendedServiceId=extenalService.extendedServiceId;
        	}

//        	if ( (extenalService.serviceId).endsWith(".Extension" )){
//        		String extendService = extenalService.serviceId.substring(0, extenalService.serviceId.length()- ".Extension".length());
//        		extenalService=extendServiceDefinition(services,extendService,extenalService);
//        		extenalService.serviceId=extenalService.serviceId+".Extension";
//        		extenalService.baseKey=extenalService.baseKey+".Extension";
//        		
//        	}
        	modifiedExtenalServices.add(extenalService);
        }
        services.addAll(modifiedExtenalServices);
        return services;
    }
    
	static ServiceDefinition extendServiceDefinition(final List<ServiceDefinition> baseServices, String extendService,ServiceDefinition serviceDefExtension) {
		for (Iterator<ServiceDefinition> internalIterator = baseServices.iterator(); internalIterator.hasNext();) {
			ServiceDefinition baseServiceDef = internalIterator.next();
			if (extendService.equalsIgnoreCase(baseServiceDef.serviceId)) {
				Map<String, ItemDefinition> items = serviceDefExtension.items;
				for (Entry<String, ItemDefinition> entry : items.entrySet()) {
					ItemDefinition itemDef = entry.getValue();
					baseServiceDef.addItem(itemDef);
				}
        		baseServiceDef.serviceId=serviceDefExtension.serviceId;
        		baseServiceDef.extendedServiceId=serviceDefExtension.extendedServiceId;
        		baseServiceDef.baseKey=serviceDefExtension.baseKey;
        		baseServiceDef.isExtended=serviceDefExtension.isExtended;
				return baseServiceDef;
			}
		}
		return serviceDefExtension;

	}
    
	private static List<ServiceDefinition> loadExternal(ClassLoader classLoader)
			throws IOException {

		List<InputStream> extResStreams = getExternalResources();

		ServiceDefinitionReader reader = new ServiceDefinitionReader();
		List<ServiceDefinition> services = new ArrayList<>();
		for (InputStream extResStream : extResStreams) {

			ServiceDefinition service = reader.readService(extResStream);
			System.out.println("MANOJ LOADING EXTERNAL SERVICE DEFS "+service.toString());
			IOUtils.closeQuietly(extResStream);
			if (service != null) {
				if (!service.disabled) {
					services.add(service);
				} else {
					LOG.debug("Skipping disabled service");
				}
			} 
		}
		return services;
	}
        
    private static List<InputStream> getExternalResources() throws IOException {
    	List<String> sysDecriptors =  Arrays.asList("CustomSampleService.json","CreateVolumeServiceExtention.json");
    	List<InputStream> resStreams= new ArrayList<InputStream>();
    	for (String sysDecriptor : sysDecriptors){
    		InputStream is = ExtentionClassLoader.getProxyResourceAsStream(sysDecriptor);
    		resStreams.add(is);
    	}
    	return resStreams;
	}

	private static Resource[] getResources(ClassLoader classLoader) throws IOException {
	   	
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
        return resolver.getResources(PATTERN);
    }
	
    public static void main(String[] args) throws IOException {
    	ExtentionClassLoader.getProxyObject("com.emc.sa.descriptor.TestExternalInterfaceImpl");
    	ServiceDefinitionLoader.loadExternal(null);
		
	}
}