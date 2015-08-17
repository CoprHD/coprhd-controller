/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import play.mvc.Http;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrowserUtil {
    public static final String USER_AGENT_HEADER = "user-agent";
    public static final Pattern MSIE_PATTERN = Pattern.compile("MSIE ([0-9]{1,}[\\.0-9]{0,})");

    public static boolean supportsCSSRotate() {
        double ieVersion = getIEVersion();
        // Since this is only IE version comparison, double and float different will not be a factor here
        return ieVersion == -1.0 || ieVersion >= 10.0; // NOSONAR ("Suppressing Floating point numbers should not be tested for equality")
    }

    public static double getIEVersion() {
        Http.Request request = Http.Request.current();
        if (request == null) {
            return -1.0;
        }

        Http.Header header = request.headers.get(USER_AGENT_HEADER);
        if (header == null) {
            return -1.0;
        }

        String userAgent = header.value();
        Matcher matcher = MSIE_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return -1.0;
    }
}
