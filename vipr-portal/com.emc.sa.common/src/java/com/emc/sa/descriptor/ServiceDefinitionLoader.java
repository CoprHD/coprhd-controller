/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

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

        return services;
    }

    private static Resource[] getResources(ClassLoader classLoader) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
        return resolver.getResources(PATTERN);
    }
}