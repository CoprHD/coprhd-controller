/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.utils;

import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;

public class SanUtils {
    private static Pattern LOOSE_WWN_PATTERN = Pattern.compile("[0-9a-f:]+");
    
    public static String formatWWN(String wwn) {
        if (StringUtils.isBlank(wwn)) {
            return null;
        }
        // Left pad with zeros to make 16 chars, trim any excess
        wwn = StringUtils.substring(StringUtils.leftPad(wwn, 16, '0'), 0, 16);

        StrBuilder sb = new StrBuilder();
        for (int i = 0; i < wwn.length(); i += 2) {
            sb.appendSeparator(':');
            sb.append(StringUtils.substring(wwn, i, i + 2));
        }
        return sb.toString();
    }
    
    public static String cleanWWN(String wwn) {
        if (StringUtils.isBlank(wwn)) {
            return null;
        }
        wwn = StringUtils.lowerCase(StringUtils.trim(wwn));
        if (!LOOSE_WWN_PATTERN.matcher(wwn).matches()) {
            return null;
        }
        wwn = StringUtils.replace(wwn, ":", "");
        return wwn;
    }
}
