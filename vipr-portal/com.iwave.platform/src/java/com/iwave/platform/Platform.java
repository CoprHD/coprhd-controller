/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.platform;

import static com.iwave.platform.ConfigurationConstants.FRAMEWORK_CONFIGS;
import static com.iwave.platform.ConfigurationConstants.PRODUCT_STRING;
import static com.iwave.platform.ConfigurationConstants.PRODUCT_VERSION;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.access.BeanFactoryLocator;
import org.springframework.beans.factory.access.BeanFactoryReference;
import org.springframework.context.ApplicationContext;
import org.springframework.context.access.ContextSingletonBeanFactoryLocator;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.emc.sa.util.SystemProperties;
import com.emc.storageos.api.service.ProvisioningService;

/**
 * Class that encapsulates the starting and management of the Platform. This
 * supports a parent ApplicationContext to allow loading from a WebApp.
 *
 * @author cdail
 */
public class Platform {
    
    private static final String SERVICE_BEAN = "saservice";
    
    /** Logger Instance */
    private Logger log = Logger.getLogger(getClass());
    
    /** The deployer of the actual services. */
    private FileSystemXmlApplicationContext applicationContext;

    public FileSystemXmlApplicationContext getApplicationContext() {
        return applicationContext;
    }
    
    @PostConstruct
    public synchronized FileSystemXmlApplicationContext start() throws Exception {
        try {
            Environment.init();
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
            throw e;
        }
        return createContext();
    }

    public FileSystemXmlApplicationContext createContext() throws Exception {
        // Display the adapters version
        if (log.isInfoEnabled()) {
            Package p = PlatformService.class.getPackage();
            String version = p.getImplementationVersion();
            if (version == null || version.equals("")) {
                version = PRODUCT_VERSION;
            }
            log.info("Starting " + PRODUCT_STRING + ", Version: " + version);
            log.info("Using home directory: " + System.getProperty("platform.home"));
            log.info("Using config directory: " + System.getProperty("config.dir"));
        }
        
        // Create the application context
        try {
            // Spring has an issue with loading absolute file paths on unix
            // The workaround is to use '//' instead of a '/'
            // See: http://forum.springframework.org/archive/index.php/t-22382.html
            FileSystemXmlApplicationContext ctx = new FileSystemXmlApplicationContext(new String[] {
                    SystemProperties.resolve(FRAMEWORK_CONFIGS).replaceAll("/", "//")
                });
            StorageAutomatorService service = (StorageAutomatorService)ctx.getBean(SERVICE_BEAN);
            service.start();            
        }
        catch (Exception e) {
            log.error("Error starting the Application Services", e);
            throw e;
        }

        if (log.isInfoEnabled()) {
            log.info(PRODUCT_STRING + " Started");
        }
        
        return applicationContext;
    }
    
    @PreDestroy
    public synchronized void stop() {
        
        // Shutdown the application context
        if (applicationContext != null) {
            try {
                applicationContext.close();
            }
            finally {
                applicationContext = null;
            }
        }
        
        if (log.isInfoEnabled()) {
            log.info("Stopped " + PRODUCT_STRING);
        }
    }
}
