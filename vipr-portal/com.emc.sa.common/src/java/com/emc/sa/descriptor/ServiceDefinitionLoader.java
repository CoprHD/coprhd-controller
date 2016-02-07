/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.xeustechnologies.jcl.ClasspathResources;
import org.xeustechnologies.jcl.JarClassLoader;
import org.xeustechnologies.jcl.JclObjectFactory;
import org.xeustechnologies.jcl.context.DefaultContextLoader;
import org.xeustechnologies.jcl.context.JclContext;
import org.xeustechnologies.jcl.proxy.CglibProxyProvider;
import org.xeustechnologies.jcl.proxy.ProxyProviderFactory;

import com.emc.sa.catalog.ExtentionClassLoader;

public class ServiceDefinitionLoader {
    public static final String PATTERN = "classpath*:com/**/*Service.json";

    private static final Logger LOG = Logger.getLogger(ServiceDefinitionLoader.class);

	
	//addJCLContextLoader(String pathToExternalResources);

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
        services.addAll(extenalServices);
        return services;
    }
    
    
    
	private static List<ServiceDefinition> loadExternal(ClassLoader classLoader)
			throws IOException {

		List<InputStream> extResStreams = getExternalResources();

		ServiceDefinitionReader reader = new ServiceDefinitionReader();
		List<ServiceDefinition> services = new ArrayList<>();
		for (InputStream extResStream : extResStreams) {

			ServiceDefinition service = reader.readService(extResStream);
			System.out.println(service.toString());
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
    	List<String> sysDecriptors =  Arrays.asList("CustomSampleService.json","ManojSampleService.json");
    	
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
    	ExtentionClassLoader.getProxy("C:/HDRIVE/ECLIPSE-WS/CoPRHD-WS/JCL-master/Test");
    	ServiceDefinitionLoader.loadExternal(null);
		
	}
}