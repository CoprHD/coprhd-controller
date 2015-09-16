/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.services.util;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Logger instance that logs messages to "/var/log/alerts" file with crit(fatal in
 * logf4j (syssvc.log)), error, warn priorities with facility set to LOCAL7.
 */
public class AlertsLogger {
    private static Logger _log = Logger.getLogger(AlertsLogger.class);
    private static volatile AlertsLogger _instance = null;
    private static final String ALERTS_FACILITY = "LOCAL7";

    private AlertsLogger() {
    }

    /**
     * Custom appender that fixes level mapping to syslog.
     */
    private static class SyslogAppender extends org.apache.log4j.net.SyslogAppender {
        @Override
        public void append(LoggingEvent event) {
            Level newLevel = event.getLevel();
            if (event.getLevel().equals(Level.FATAL)) {
                newLevel = SyslogLevel.FATAL;
            }
            super.append(new LoggingEvent(
                    event.getFQNOfLoggerClass(), event.getLogger(), event.getTimeStamp(),
                    newLevel, event.getMessage(), event.getThreadName(),
                    event.getThrowableInformation(),
                    event.getNDC(), event.getLocationInformation(),
                    event.getProperties()));
        }
    }

    /**
     * Fix for level mapping - log4j "fatal" is mapped to syslog "critical".
     */
    private static class SyslogLevel extends Level {
        private static final Level FATAL = new SyslogLevel(FATAL_INT, "FATAL", 2);

        protected SyslogLevel(int level, String levelStr, int syslogEquivalent) {
            super(level, levelStr, syslogEquivalent);
        }
    }

    public static AlertsLogger getAlertsLogger() {
        if (_instance == null) {
            SyslogAppender syslogAppender = new SyslogAppender();
            syslogAppender.setFacility(ALERTS_FACILITY);
            syslogAppender.setSyslogHost("localhost");
            syslogAppender.setHeader(true);
            syslogAppender.activateOptions();
            _instance = new AlertsLogger();
            _log.addAppender(syslogAppender);
        }
        return _instance;
    }

    public void fatal(Object message) {
        _log.fatal(message);
    }

    public void error(Object message) {
        _log.error(message);
    }

    public void warn(Object message) {
        _log.warn(message);
    }
}
