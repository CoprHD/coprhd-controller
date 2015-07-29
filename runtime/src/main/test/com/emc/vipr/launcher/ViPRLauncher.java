/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.launcher;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.UnhandledException;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.google.common.collect.Maps;

public class ViPRLauncher {
    private static void initLogging(File homeDir) {
        // Create the logs directory if it does not already exist
        new File(homeDir, "logs").mkdirs();

        File confDir = new File(homeDir, "conf");
        File logConf = new File(confDir, "log4j-runtime.properties");

        LogManager.resetConfiguration();
        PropertyConfigurator.configureAndWatch(logConf.getAbsolutePath());

        // Initialize the JUL -> SLF bridge so all log messages end up in Log4j
        java.util.logging.LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
    }

    private static Properties readConfig(File configFile) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(configFile))) {
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IllegalArgumentException | IOException ex) {
            throw new UnhandledException(ex);
        }
    }

    private static Map<String, AbstractServiceLauncher> loadLaunchers(Properties config) {
        Map<String, AbstractServiceLauncher> launchers = Maps.newHashMap();
        for (Map.Entry<Object, Object> entry : config.entrySet()) {
            String propertyName = entry.getKey().toString();
            String propertyValue = entry.getValue().toString();

            if (StringUtils.endsWith(propertyName, ".service")) {
                String launcherName = StringUtils.removeEnd(propertyName, ".service");
                AbstractServiceLauncher launcher = createLauncher(propertyValue);
                launchers.put(launcherName, launcher);
            }
        }
        return launchers;
    }

    private static AbstractServiceLauncher createLauncher(String className) {
        try {
            Class<?> launcherClass = Class.forName(className);
            if (!AbstractServiceLauncher.class.isAssignableFrom(launcherClass)) {
                throw new IllegalArgumentException(String.format(
                        "Class %s is not a subclass of AbstractServiceLauncher", className));
            }
            AbstractServiceLauncher launcher = (AbstractServiceLauncher) launcherClass.newInstance();
            return launcher;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new UnhandledException(e);
        }
    }

    public static void launchServices(Properties config) {
        Map<String, AbstractServiceLauncher> launchers = loadLaunchers(config);

        String launch = config.getProperty("launch");
        if (StringUtils.isBlank(launch)) {
            throw new IllegalArgumentException("No launch property provided");
        }
        for (String name : StringUtils.split(launch, ",")) {
            AbstractServiceLauncher launcher = launchers.get(name);
            if (launcher == null) {
                throw new IllegalArgumentException("No launcher named: " + name);
            }
            launcher.launch();
            // Allows configuring the delay after launching the service
            String delay = StringUtils.defaultString(config.getProperty(name + ".delay"), "5000");
            try {
                Thread.sleep(NumberUtils.toLong(delay, 5000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UnhandledException(e);
            }
        }
    }

    /**
     * System properties needed to launch this: -Dproduct.home=<path to working dir> (in eclipse:
     * ${workspace_loc:runtime}/working) -Djava.net.preferIPv4Stack=true
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("No configuration file specified");
        }
        String configPath = args[0];
        File configFile = new File(configPath);
        if (System.getProperty("log.name") == null) {
            String logName = StringUtils.substringBefore(configFile.getName(), ".");
            System.setProperty("log.name", logName);
        }

        String productHome = System.getProperty("product.home");
        if (StringUtils.isBlank(productHome)) {
            throw new IllegalStateException("product.home is not set");
        }
        File homeDir = new File(productHome);
        initLogging(homeDir);

        if (!configFile.isFile()) {
            if (configFile.isAbsolute()) {
                throw new IllegalArgumentException("Configuration file does not exist: " + configFile);
            }
            // Try relative to the home directory
            configFile = new File(homeDir, args[0]);
            if (!configFile.isFile()) {
                throw new IllegalArgumentException("Configuration file does not exist: " + configFile);
            }
        }

        Properties config = readConfig(configFile);
        launchServices(config);
    }
}
