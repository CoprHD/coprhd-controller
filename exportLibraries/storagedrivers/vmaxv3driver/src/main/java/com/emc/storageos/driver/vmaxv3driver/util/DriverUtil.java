/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.util;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;

/**
 * Utility methods.
 *
 * Created by gang on 7/5/16.
 */
public class DriverUtil {

    /**
     * Convert WWN number like "500009735014fc18" to "50:00:09:73:50:14:fc:18".
     *
     * @param wwn
     * @return
     */
    public static String formatWwn(String wwn) {
        if (StringUtils.isBlank(wwn)) {
            return "";
        }
        // Left pad with zeros to make size to 16 and trim excess.
        wwn = StringUtils.substring(StringUtils.leftPad(wwn, 16, '0'), 0, 16);
        StrBuilder sb = new StrBuilder();
        for (int i = 0; i < wwn.length(); i += 2) {
            sb.appendSeparator(':');
            sb.append(StringUtils.substring(wwn, i, i + 2));
        }
        return sb.toString();
    }
}
