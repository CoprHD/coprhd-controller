/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.infra;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Date;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import models.datatable.AuditLogDataTable;
import org.joda.time.DateTime;
import play.Logger;
import play.mvc.Controller;
import play.mvc.With;
import util.AuditLogs;
import util.BourneUtil;
import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_AUDITOR") })
public class AuditLog extends Controller {
    private static String LOG_LANG = "en_US";
    private static JAXBContext CONTEXT;

    private static final String PARAM_DATE = "date";
    private static final String PARAM_START = "startTime";
    private static final String PARAM_END = "ENDTime";
    private static final String PARAM_RESULT = "resultStatus";
    private static final String PARAM_SERVICE = "serviceType";
    private static final String PARAM_USER = "user";
    private static final String PARAM_KEYWORD = "keyword";

    private static synchronized JAXBContext getContext() throws JAXBException {
        if (CONTEXT == null) {
            CONTEXT = JAXBContext.newInstance(AuditLogs.class);
        }
        return CONTEXT;
    }

    private static AuditLogs unmarshall(InputStream in) throws JAXBException {
        return (AuditLogs) getContext().createUnmarshaller().unmarshal(in);
    }

    public static void list() {
        AuditLogDataTable dataTable = new AuditLogDataTable();

        Long startTime = params.get(PARAM_START, Long.class);
        String resultStatus = params.get(PARAM_RESULT);
        String serviceType = params.get(PARAM_SERVICE);
        String user = params.get(PARAM_USER);
        String keyword = params.get(PARAM_KEYWORD);
        DateTime defaultStartTime = new DateTime().minusMinutes(15);
        renderArgs.put(PARAM_START, (startTime == null) ? defaultStartTime.getMillis() : startTime);
        renderArgs.put(PARAM_RESULT, resultStatus);
        renderArgs.put(PARAM_SERVICE, serviceType);
        renderArgs.put(PARAM_USER, user);
        renderArgs.put(PARAM_KEYWORD, keyword);
        Common.copyRenderArgsToAngular();

        render(dataTable);
    }

    public static void logsJson() {
        InputStream in = getLogsStream();
        try {
            AuditLogs logs = unmarshall(in);
            if (logs.getAuditLogs() != null) {
                for (com.emc.storageos.db.client.model.AuditLog log : logs.getAuditLogs()) {
                    if (log.getUserId() == null) {
                        try {
                            log.setUserId(new URI(""));
                        } catch (URISyntaxException shouldNotHappen) {
                            Logger.error(shouldNotHappen, "Error setting UserId to blank");
                        }
                    }
                }

                renderJSON(logs.getAuditLogs());
            }
            else {
                renderJSON(Collections.<String> emptyList());
            }
        } catch (JAXBException e) {
            error(e);
        }
    }

    private static InputStream getLogsStream() {
        Long dateTime = params.get(PARAM_DATE, Long.class);
        Long startTime = params.get(PARAM_START, Long.class);
        Long endTime = params.get(PARAM_END, Long.class);
        String result = params.get(PARAM_RESULT);
        String serviceType = params.get(PARAM_SERVICE);
        String user = params.get(PARAM_USER);
        String keyword = params.get(PARAM_KEYWORD);
        if (isAllNull(dateTime, startTime, endTime, result, serviceType, user, keyword)) {
            dateTime = new Date().getTime();
        }
        if (dateTime != null) {
            return BourneUtil.getViprClient().audit().getLogsForHourAsStream(new Date(dateTime), LOG_LANG);
        }
        return BourneUtil.getViprClient().audit().getAsStream(new Date(startTime), new Date(endTime), serviceType, user, result, keyword,
                LOG_LANG);
    }

    private static boolean isAllNull(Object... objects) {
        for (Object object : objects) {
            if (object != null) {
                return false;
            }

        }
        return true;
    }

    public static void download() {

    }
}