/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.platform;

/**
 * Constants used for external configuration files and directories.
 *
 * @author cdail
 */
public class ConfigurationConstants {
    
    /** Product String. */
    public static final String PRODUCT_STRING = "sasvc";
    
    /** Product Version String. */
    public static final String PRODUCT_VERSION = "6.0";

    /** Configuration directory. */
    public static final String CONFIG_DIR = "${config.dir}";
    
    /** Location of the framework Spring configuration files. */
    public static final String FRAMEWORK_CONFIGS = CONFIG_DIR + "/sa-conf.xml";
    
    /** Location of the log4j XML file. */
    public static final String LOG_CONFIGURATION  = CONFIG_DIR + "/sasvc-log4j.properties";
    
    /** Location directory of the log files. */
    public static final String LOG_DIRECTORY = "${platform.home}/logs";
    
    /** Location to redirect stdout to. */
    public static final String STDOUT_LOG  = LOG_DIRECTORY + "/sasvc.out";
    
    /** Location of the old stdout file. */
    public static final String STDOUT_LOG_OLD  = STDOUT_LOG + ".old";
    
    /** Location to redirect stderr to. */
    public static final String STDERR_LOG  = LOG_DIRECTORY + "/sasvc.err";
    
    /** Location of the old stderr file. */
    public static final String STDERR_LOG_OLD  = STDERR_LOG + ".old";

    /** Location for the base of derby. */
    public static final String DATA_DIR_PROP = "data.dir";

    /** Location for the base of derby. */
    public static final String DATA_DIR = "${platform.home}/data";
}
