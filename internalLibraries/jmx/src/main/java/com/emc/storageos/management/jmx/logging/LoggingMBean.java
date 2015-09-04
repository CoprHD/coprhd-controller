/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.management.jmx.logging;

import java.io.IOException;
import java.net.URL;
import java.util.Calendar;
import java.util.Scanner;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

import com.emc.vipr.model.sys.logging.LogScopeEnum;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;

@ManagedResource(objectName = LoggingMBean.MBEAN_NAME, description = "Logging MBean")
public class LoggingMBean {
    private static final org.slf4j.Logger _log = LoggerFactory.getLogger(
            LoggingMBean.class);
    public static final String MBEAN_NAME =
            "com.emc.storageos.management.jmx.logging:name=LoggingMBean";
    public static final String ATTRIBUTE_NAME = "LoggerLevel";
    public static final String OPERATION_RESET = "resetLoggerLevel";
    public static final String OPERATION_SET = "setLoggerLevelWithExpir";

    private static final String LOG_LEVEL_DELIMITER = "/";
    private static final String LOG_LEVEL_CONFIG = "log_level";
    private static final int _DEFAULT_LOG_INIT_DELAY_SECONDS = 5;
    private static final int _DEFAULT_LOG_LEVEL_CHECK_MINUTES = 120; // 2 hours
    private static final int _DEFAULT_LOG_LEVEL_RETRY_SECONDS = 2;
    private static final String FULL_MESSAGES_CLASS_NAME = "com.emc.storageos.svcs.errorhandling.utils.Messages";

    private CoordinatorClient _coordinator;
    private String _logName;
    private String _hostId;
    private int _initDelayInSeconds = _DEFAULT_LOG_INIT_DELAY_SECONDS;
    private int _logLevelResetCheckMinutes = _DEFAULT_LOG_LEVEL_CHECK_MINUTES;
    private int _logLevelResetRetrySeconds = _DEFAULT_LOG_LEVEL_RETRY_SECONDS;
    private boolean _isInitDelayed; // default = false;
    private LogLevelResetter _resetterRunnable = new LogLevelResetter();

    private static class LogLevelConfig {
        public String level;
        public String scope;
        public Calendar expiration = Calendar.getInstance();
    }

    /**
     * Setter for the initialization delay time in milliseconds.
     *
     * @param initDelay initialization delay time in milliseconds.
     */
    public void setInitDelayInSeconds(int initDelay) {
        if (initDelay == 0)
            return;
        if (initDelay < 0)
            throw new IllegalArgumentException("negative initDelay provided");
        _initDelayInSeconds = initDelay;
        _isInitDelayed = true;
    }

    /**
     * Setter for the coordinator client reference.
     *
     * @param coordinator A reference to the coordinator client.
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    /**
     * Setter for the service for getting Bourne cluster information.
     *
     * @param service A reference to the service for getting Bourne cluster
     *            information.
     */
    public void setService(Service service) {
        _logName = service.getName();
        _hostId = service.getId();
    }

    @ManagedAttribute(description = "get the root logger level")
    public String getLoggerLevel() {
        return LogManager.getRootLogger().getEffectiveLevel().toString();
    }

    @ManagedOperation(description = "update the root logger level with a sepcific " +
            "expiration time")
    public void setLoggerLevelWithExpir(String level, int expir, String scope) {
        LogScopeEnum scopeEnum = getLogScope(scope);
        setLoggerLevelByScope(level, scopeEnum);
        persistLogLevel(level, expir, scopeEnum.toString());
        _resetterRunnable.snooze(expir * 60 * 1000);
    }

    @ManagedOperation(description = "reset the root logger level")
    public void resetLoggerLevel() {
        loadMessagesClass();
        ClassLoader cl = getClass().getClassLoader();
        LogManager.resetConfiguration();
        URL log4jprops = cl.getResource(System.getProperty("log4j.configuration"));
        if (log4jprops != null) {
            PropertyConfigurator.configure(log4jprops);
        }
    }

    /**
     * There is bug in log4j which will cause deadlock when calling LogManager.resetConfiguration()
     * and Application is performing log.error(msg,e), log4j will call e.toString() method which
     * eventually create new Messages instance, LogFactory.getLogger() will be called when loading
     * Messages first time and try to get lock on ht which probably hold by another thread
     * (resetConfiguration); before calling LogManager.resetConfiguration() we will load Messages
     * class first to avoid race condition.
     * */
    private void loadMessagesClass() {
        try {
            Class.forName(FULL_MESSAGES_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            _log.warn("can't find Messages class", e);
        }
    }

    /**
     * If there has been a dynamic log level change, pick up and reuse the log level from
     * coordinator service.
     */
    public void init() {
        _log.trace("Entered init()");
        if (_isInitDelayed) {
            _log.info("Scheduling the initialization in {} seconds.", _initDelayInSeconds);
            Executors.newSingleThreadScheduledExecutor().schedule(
                    new Runnable() {
                        @Override
                        public void run() {
                            _log.info("Starting coordinator client");
                            try {
                                _coordinator.start();
                            } catch (IOException e) {
                                _log.error("failed to start coordinator client:", e);
                                return;
                            }
                            init();
                        }
                    }, _initDelayInSeconds, TimeUnit.SECONDS);

            _isInitDelayed = false;
            return;
        }

        _log.debug("Starting the log level resetter thread");
        Thread thread = new Thread(_resetterRunnable);
        thread.setName("LogLevelResetter");
        thread.start();

        _log.debug("Initializing logger for {}", _logName);
        Configuration config = getLogLevelConfig();
        if (config == null) {
            _log.info("No previous dynamic log change for {} found in ZK", _logName);
            return;
        }

        LogLevelConfig logLevelConfig = parseLogLevelConfig(config);
        if (logLevelConfig.expiration.before(Calendar.getInstance())) {
            _log.info("config should have expired at {}, now is {}",
                    logLevelConfig.expiration.getTime(),
                    Calendar.getInstance().getTime());
        } else {
            _log.info("Setting log level for {} to level {} and scope {} according to previous change",
                    new Object[] { _logName, logLevelConfig.level, logLevelConfig.scope });
            setLoggerLevelByScope(logLevelConfig.level, getLogScope(logLevelConfig.scope));

            // Schedule the polling thread which reverts expired dynamic log level changes.
            long nextRun = logLevelConfig.expiration.getTimeInMillis() -
                    System.currentTimeMillis();
            _log.info("Try to reset the log level in {} milliseconds", nextRun);
            _resetterRunnable.snooze(nextRun);
        }
    }

    public void shutdown() {
        _resetterRunnable.shutdown();
    }

    private LogScopeEnum getLogScope(String scope) {
        if (scope == null) {
            return LogScopeEnum.SCOPE_DEFAULT;
        }
        try {
            return LogScopeEnum.valueOf(scope);
        } catch (IllegalArgumentException e) {
            _log.error("Exception getting LogScopeEnum, e=", e);
            return LogScopeEnum.SCOPE_DEFAULT;
        }
    }

    private void setLoggerLevelByScope(String level, LogScopeEnum scopeEnum) {
        switch (scopeEnum) {
            case SCOPE_DEPENDENCY:
                setCurrentLogger(level);
            case SCOPE_DEFAULT:
                LogManager.getRootLogger().setLevel(Level.toLevel(level));
                break;
            default:
                _log.error("The log scope({}) is not supported.", scopeEnum);
        }
    }

    private void setCurrentLogger(String level) {
        Enumeration cats = LogManager.getCurrentLoggers();
        while (cats.hasMoreElements()) {
            Logger c = (Logger) cats.nextElement();
            if (c.getLevel() != null) {
                c.setLevel(Level.toLevel(level));
                _log.info("set logger({}) level to {}", c.getName(), level);
            }
        }
    }

    private void persistLogLevel(String level, int expirInMin, String scope) {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(_logName);
        config.setId(_hostId);

        long expiration = System.currentTimeMillis() + expirInMin * 60 * 1000;
        String configStr = level + LOG_LEVEL_DELIMITER + String.valueOf(expiration)
                + LOG_LEVEL_DELIMITER + scope;
        config.setConfig(LOG_LEVEL_CONFIG, configStr);

        try {
            _log.info("Persisting log level configuration");
            _coordinator.persistServiceConfiguration(config);
            _log.info("Persist log level configuration succeed");
        } catch (CoordinatorException e) {
            _log.error("Exception persisting log level config {}:", configStr, e);
            return;
        }
    }

    // Get the persisted log level configuration from ZK. return null if none
    private Configuration getLogLevelConfig() {
        Configuration config = null;
        try {
            config = _coordinator.queryConfiguration(_logName, _hostId);
        } catch (CoordinatorException e) {
            _log.error("Exception getting log level configuration:", e);
            return null;
        }

        if (config == null || config.getConfig(LOG_LEVEL_CONFIG) == null)
            return null;

        return config;
    }

    private static LogLevelConfig parseLogLevelConfig(Configuration config) {
        String configStr = config.getConfig(LOG_LEVEL_CONFIG);
        _log.debug("parsing config :{}", configStr);

        Scanner scanner = null;
        try {
            scanner = new Scanner(configStr).useDelimiter(LOG_LEVEL_DELIMITER);
            LogLevelConfig logLevelConfig = new LogLevelConfig();
            logLevelConfig.level = scanner.next();
            logLevelConfig.expiration.setTimeInMillis(scanner.nextLong());
            // the scope field was added in ViPR V1.1. When parsing a Configration
            // from V1, just leave this field as null.
            if (scanner.hasNext())
                logLevelConfig.scope = scanner.next().trim();
            else
                logLevelConfig.scope = null;

            return logLevelConfig;
        } finally {
            if (scanner != null)
                scanner.close();
        }
    }

    /**
     * Class for resetting dynamic log level changes.
     * Borrowed from CustomAuthenticationManager.LogLevelResetter
     */
    private class LogLevelResetter implements Runnable {
        private final org.slf4j.Logger _log = LoggerFactory.getLogger(
                LogLevelResetter.class);
        private boolean _doRun = true;
        private Long _lastResetTime = 0L;

        private class Waiter {
            private long _t = 0;

            public synchronized void sleep(long milliSeconds) {
                _t = System.currentTimeMillis() + milliSeconds;
                while (true) {
                    final long dt = _t - System.currentTimeMillis();
                    _log.debug("dt = {}", dt);
                    if (dt <= 0) {
                        return;
                    } else {
                        try {
                            wait(dt);
                        } catch (InterruptedException e) {
                            _log.info("LogLevelResetter: waiter interrupted", e);
                        }
                    }
                }
            }

            public synchronized void snooze(final long ms) {
                _t = System.currentTimeMillis() + ms;
                notifyAll();
            }
        }

        private final Waiter _waiter = new Waiter();

        private void sleep(final long ms) {
            _waiter.sleep(ms);
        }

        public void snooze(final long ms) {
            _lastResetTime = System.currentTimeMillis() + ms;
            _log.info("received request to reset at {}", _lastResetTime);
            // you can snooze for another ms milliseconds bofore waking up
            _waiter.snooze(ms);
        }

        public void shutdown() {
            _doRun = false;
        }

        @Override
        public void run() {
            while (_doRun) {
                _log.info("Starting log level config reset, lastResetTime = {}",
                        _lastResetTime);
                try {
                    long timeNow = System.currentTimeMillis();
                    Calendar now = Calendar.getInstance();
                    long nextRunMillis = _logLevelResetCheckMinutes * 60 * 1000;
                    Configuration config = getLogLevelConfig();
                    if (config == null) {
                        _log.debug("No previous dynamic log level changes found");
                    } else {
                        LogLevelConfig logLevelConfig = parseLogLevelConfig(config);

                        // check to see if the config expires
                        if (logLevelConfig.expiration.after(now)) {
                            _log.debug("Log level configuration not yet expired, " +
                                    "skipping reset log level");
                            // Reschedule the task
                            nextRunMillis =
                                    logLevelConfig.expiration.getTimeInMillis() -
                                            timeNow;
                        } else {
                            _log.info("resetting log level");
                            resetLoggerLevel();
                            _log.info("deleting log level configuration");
                            _coordinator.removeServiceConfiguration(config);
                            _log.info("Done log level reset");
                        }
                    }
                    // sleep and check for updates
                    _log.debug("Next check will run in {} min", nextRunMillis / 60 / 1000);
                    sleep(nextRunMillis);
                    // An update notification came in ... run again after a short nap
                } catch (Exception e) {
                    _log.error("Exception loading log level configuration from zk"
                            + ", will retry in {} secs", _logLevelResetRetrySeconds, e);
                    // schedule a retry
                    try {
                        Thread.sleep(_logLevelResetRetrySeconds * 1000);
                    } catch (Exception ignore) {
                        _log.error("Got Exception in thread.sleep()", e);
                    }
                }
            }
        }
    }
}
