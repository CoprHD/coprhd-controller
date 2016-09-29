/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.commons.lang.StringUtils;

public class SystemLogUtils {
    public static final String SYSTEM_EVENTS_LOG = "systemevents";
    public static final String MESSAGES_LOG = "messages";
    public static final String NGINX_ACCESS_LOG = "nginx_access";
    public static final String NGINX_ERROR_LOG = "nginx_error";
    public static final String BKUTILS_LOG = "bkutils";
    public static final String[] NON_SERVICE_LOGS = {
            SYSTEM_EVENTS_LOG, MESSAGES_LOG, NGINX_ACCESS_LOG, NGINX_ERROR_LOG, BKUTILS_LOG
    };

    public static String buildLogsUrl(String format, String nodeId, String logName, Integer severity,
            String searchMessage, String startTime, String endTime) {
        StringBuffer sb = new StringBuffer("logs");
        if (StringUtils.isNotBlank(format)) {
            sb.append('.');
            sb.append(format);
        }
        sb.append("?end=");
        sb.append(urlEncode(endTime));
        if (StringUtils.isNotBlank(nodeId)) {
            sb.append("&node_id=");
            sb.append(urlEncode(nodeId));
        }
        if (StringUtils.isNotBlank(logName)) {
            sb.append("&log_name=");
            sb.append(urlEncode(logName));
        }
        if (severity != null) {
            sb.append("&severity=");
            sb.append(urlEncode(severity));
        }
        if (StringUtils.isNotBlank(searchMessage)) {
            sb.append("&msg_regex=");
            sb.append(urlEncode(searchMessage));
        }
        sb.append("&start=");
        sb.append(urlEncode(startTime));
        return sb.toString();
    }

    private static String urlEncode(Object o) {
        String value = null;
        if (o instanceof String) {
            value = (String) o;
        }
        else if (o != null) {
            value = o.toString();
        }
        return urlEncode(value);
    }

    private static String urlEncode(String s) {
        try {
            return s != null ? URLEncoder.encode(s, "UTF-8") : "";
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
