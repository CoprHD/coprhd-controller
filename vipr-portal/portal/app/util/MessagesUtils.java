/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.StringEscapeUtils;
import play.i18n.Messages;

public class MessagesUtils {
    public static String escapeHtml(String value) {
        return StringEscapeUtils.escapeHtml(value);
    }

    public static String escape(String value) {
        return StringUtils.replace(value, "%", "%%");
    }

    public static String get(String key, Object... vars) {
        if (vars != null && vars.length > 0) {
            Object[] newVars = new Object[vars.length];
            for (int i = 0; i < vars.length; i++) {
                Object value = vars[i];
                if (value instanceof String) {
                    value = escape((String) value);
                }
                newVars[i] = value;
            }
            return Messages.get(key, newVars);
        }
        return Messages.get(key);
    }
}
