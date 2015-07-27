/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.platform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.emc.sa.util.SystemProperties;

import static com.iwave.platform.ConfigurationConstants.*;

/**
 * <p>Initializes the iWave Platform Environment. This initializes the
 * logging facility, temp directories, system properties and other resources
 * required before the spring context can be created.
 *
 * <p>This class is ported from the adapter framework class
 * com.iwave.framework.Environment.
 *
 * @author Chris Dail
 */
public class Environment {
    
    private static boolean initialized = false;

    /**
     * Initializes the environment.
     * @throws java.io.IOException
     */
    public static void init() throws IOException {
        if (initialized) {
            return;
        }
        
        // This must always be done first as other things use this variable
        initHome();
        initDataDir();
        
        // Initialize logging
        initLogging();
        initStdOut();
        initStdErr();

        // Show the environment
        initSystemProperties();
        showEnvironment();
        
        initialized = true;
    }

    private static void initHome() throws IOException {
        String homeString = System.getProperty("platform.home");
        if (homeString == null || homeString.equals("")) {
            homeString = System.getProperty("user.dir") + "/..";
        }
        
        // platform.home may not point to an absolute path.
        // Set it again with the absolute path
        File homeDir = new File(homeString).getCanonicalFile();
        System.setProperty("platform.home", homeDir.getAbsolutePath());
    }
    
    private static void initDataDir() {
        if (System.getProperties().containsKey(DATA_DIR_PROP)) {
            File tempDir = new File(System.getProperty(DATA_DIR_PROP));
            System.setProperty("java.io.tmpdir", tempDir.getAbsolutePath());
        }
        else {
            File tempDir = new File(SystemProperties.resolve(
                DATA_DIR));
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            if (tempDir.isDirectory()) {
                System.setProperty("java.io.tmpdir", tempDir.getAbsolutePath());
            }
        }
    }
    
    private static void initLogging() {
        // Create the logging directory if it does not already exist
        new File(SystemProperties.resolve(LOG_DIRECTORY)).mkdirs();
        
        String logPath = SystemProperties.resolve(LOG_CONFIGURATION);
        if (new File(logPath).exists()) {
            LogManager.resetConfiguration();
            PropertyConfigurator.configureAndWatch(logPath);
        } else {
            LogManager.resetConfiguration();
            System.out.println("Unable to initialize logging, "+logPath+" not found, is platform.home set correctly?");
        }

        // Initialize the JUL -> SLF bridge so all log messages end up in Log4j
        java.util.logging.LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
    }
    
    private static void initStdOut() throws IOException {
        File sysoutFile = new File(SystemProperties.resolve(STDOUT_LOG));
        
        // Rename the STDOUT file to the old variation
        if (sysoutFile.isFile()) {
            File oldFile = new File(SystemProperties.resolve(STDOUT_LOG_OLD));
            sysoutFile.renameTo(oldFile);
        }
        
        sysoutFile.createNewFile();
        System.setOut(new PrintStream(new FileOutputStream(sysoutFile)));
    }
    
    private static void initStdErr() throws IOException {
        File syserrFile = new File(SystemProperties.resolve(STDERR_LOG));
        
        // Rename the STDERR file to the old variation
        if (syserrFile.isFile()) {
            File oldFile = new File(SystemProperties.resolve(STDERR_LOG_OLD));
            syserrFile.renameTo(oldFile);
        }
        
        syserrFile.createNewFile();
        System.setErr(new PrintStream(new FileOutputStream(syserrFile)));
    }
    
    private static void initSystemProperties() throws IOException {
        // Create a local logger for showing the environment
        Logger log = Logger.getLogger(Environment.class);

        // Set a hostname environment variable that can be resolved from the
        // properties files
        try {
            System.setProperty("hostname", InetAddress.getLocalHost().getHostName());
        }
        catch (UnknownHostException e) {
            log.warn("Unable to determine the hostname", e);
        }
    }
    
    private static void showEnvironment() {
        // Create a local logger for showing the environment
        Logger log = Logger.getLogger(Environment.class);
        
        if (log.isDebugEnabled()) {
            Properties props = System.getProperties();
            log.debug("Showing all system properties:");
            for (Enumeration<Object> enumer = props.keys(); enumer.hasMoreElements();) {
                Object key = enumer.nextElement();
                Object value = props.get(key);
                log.debug("  " + key + "=" + value);
            }
        }
    }    
}
