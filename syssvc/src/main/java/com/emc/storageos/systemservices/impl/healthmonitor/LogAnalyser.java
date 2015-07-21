/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.healthmonitor;

import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.LogSvcPropertiesLoader;
import com.emc.storageos.systemservices.impl.logsvc.merger.LogNetworkStreamMerger;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;
import com.emc.vipr.model.sys.logging.LogRequest;
import com.emc.vipr.model.sys.logging.LogSeverity;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Log analyser is called by DiagnosticsScheduler every 15 min, it anylysises the db/zk error logs
 * If any error log matches the pre-defined error/fatal patterns, it will be writen into SystemEvents
 *
 * There are 2 kinds of patterns right now, error patterns and fatal patterns
 * db and zk's patterns are seperated.
 */
public class LogAnalyser {

    private enum LogAlertLevel {
        WARNNING,
        ERROR,
        FATAL;
    }

    private static final Logger _log = LoggerFactory.getLogger(LogAnalyser.class);

    private static final AlertsLogger _alertsLog = AlertsLogger.getAlertsLogger();

    private ServiceImpl service;
    private LogSvcPropertiesLoader logSvcPropertiesLoader;

    //Pre-defined patterns in sys-conf.xml
    private List<String> warnningPatterns;
    private List<String> errorPatterns;
    private List<String> fatalPatterns;

    private List<String> svcNames;
    private int logLevel = LogSeverity.ERROR.ordinal();
    private long maxCount = 1000;
    private String msgRegex = null;
    private DateTime startTime;
    private DateTime endTime;

    public void setService(ServiceImpl service) {
        this.service = service;
    }

    public void setLogSvcPropertiesLoader(LogSvcPropertiesLoader logSvcPropertiesLoader) {
        this.logSvcPropertiesLoader = logSvcPropertiesLoader;
    }

    public void setWarnningPatterns(List<String> warnningPatterns) {
        this.warnningPatterns = warnningPatterns;
    }

    public void setErrorPatterns(List<String> errorPatterns) {
        this.errorPatterns = errorPatterns;
    }

    public void setFatalPatterns(List<String> fatalPatterns) {
        this.fatalPatterns = fatalPatterns;
    }

    public void setSvcNames(List<String> svcNames) {
        this.svcNames = svcNames;
    }

    public void analysisLogs() {
        //parse db and zk error logs and alert if match pre-defined errors/fatals
        try {
            String serviceNameList = getServiceNameList();
            _log.info("Starting parse error logs for services : {}, and will alert if match pre-defined errors/fatals", serviceNameList);
            LogNetworkStreamMerger logRequestMgr = getNodeErrorLogs();
            LogMessage msg = logRequestMgr.readNextMergedLogMessage();
            int finalCount = 0;
            if (msg != null) {
                do {
                    List<LogMessage> currentLogBatch = new ArrayList<>();
                    LogMessage startLogOfNextBatch = logRequestMgr.readLogBatch(msg, currentLogBatch);

                    if (!LogUtil.permitNextLogBatch(maxCount, finalCount,
                            currentLogBatch.size())) {
                        //discard this batch
                        break;
                    }

                    for (LogMessage logMsg : currentLogBatch) {
                        parseErrorLogAndCompareWithPatterns(logMsg);
                        finalCount++;
                    }

                    msg = startLogOfNextBatch;
                } while (msg != null) ;
            }
            _log.info("Total error/fatal logs number is {}", finalCount);
        } catch (Exception e) {
            _log.error("Get exception when achieve logs with error msg: {}; stack trace is {}",
                    e.getMessage(), e.getStackTrace());
        }

    }

    private String getServiceNameList() {
        StringBuilder strBuilder = new StringBuilder();
        for(String svcName : svcNames) {
            if (strBuilder.length() > 0) {
                strBuilder.append(';');
            }
            strBuilder.append(svcName);
        }
        return strBuilder.toString();
    }

   /*
    * get error logs from the given service
    */
    private LogNetworkStreamMerger getNodeErrorLogs() {
        endTime = new DateTime();
        if(startTime == null) {
            startTime = endTime.minusMinutes(15);
        }

        String nodeId = service.getNodeName();
        List<String> nodeIds = new ArrayList<String>();
        nodeIds.add(nodeId);
        LogRequest logReqInfo = new LogRequest.Builder().nodeIds(nodeIds).baseNames(
                svcNames).logLevel(logLevel).startTime(startTime.toDate())
                .endTime(endTime.toDate()).regex(msgRegex).maxCont(maxCount).build();
        _log.info("Diagnostics scheduler: log request info is {}", logReqInfo.toString());

        LogNetworkStreamMerger logRequestMgr = new LogNetworkStreamMerger
                (logReqInfo, MediaType.TEXT_PLAIN_TYPE, logSvcPropertiesLoader);

        //Then it will be start with next log.
        startTime = endTime.plusSeconds(1);
        return logRequestMgr;
    }

    /*
     * parse error logs with error patterns and fatal patterns
     * rawLogContent is the full content of one log message
     */
    private void parseErrorLogAndCompareWithPatterns(LogMessage msg) {
        if ((msg.getLogContent() != null) && (msg.getLogContent().length != 0)) {
            String rawLogContent = new String(msg.getRawLogContent());
            String prefixStr = new String(msg.getService());
            _log.debug("Current log's raw content is {}", rawLogContent);

            compareErrorLogWithPatterns(rawLogContent, warnningPatterns, prefixStr, LogAlertLevel.WARNNING);
            compareErrorLogWithPatterns(rawLogContent, errorPatterns, prefixStr, LogAlertLevel.ERROR);
            compareErrorLogWithPatterns(rawLogContent, fatalPatterns, prefixStr, LogAlertLevel.FATAL);
        }
    }

    private void compareErrorLogWithPatterns(String rawLogContent, List<String> matchPatterns, String preStr, LogAlertLevel logAlertLevel) {
        for (String pattern : matchPatterns) {
            Pattern patt = Pattern.compile(pattern);
            Matcher matcher = patt.matcher(rawLogContent);
            if (matcher.matches()) {
                switch(logAlertLevel) {
                    case WARNNING:
                        _alertsLog.warn("[" + preStr + "] " + rawLogContent);
                        break;
                    case ERROR:
                        _alertsLog.error("[" + preStr + "] " + rawLogContent);
                        break;
                    case FATAL:
                        _alertsLog.fatal("[" + preStr + "] " + rawLogContent);
                        break;
                    default:
                        break;
                }
                _log.debug("Pattern {} matches with log {} ", pattern, rawLogContent);
                return;
            }
        }
    }
}
