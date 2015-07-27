/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;

import com.google.common.collect.Lists;

public class SanUtils {

    private static Pattern LOOSE_WWN_PATTERN = Pattern.compile("[0-9a-f:]+");

    public static String normalizeWWN(long wwn) {
        return normalizeWWN(Long.toHexString(wwn));
    }

    public static String getPortName(String wwn) {
        String nodeName = StringUtils.substring(cleanWWN(wwn), 16);
        return formatWWN(nodeName);
    }

    public static String getNodeName(String wwn) {
        String portName = StringUtils.substring(cleanWWN(wwn), 0, 16);
        return formatWWN(portName);
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

    private static String formatWWN(String wwn) {
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

    public static String normalizeWWN(String wwn) {
        return formatWWN(cleanWWN(wwn));
    }

    public static List<String> normalizeWWNs(List<String> wwns) {
        List<String> normalized = Lists.newArrayList();
        for (String wwn : wwns) {
            normalized.add(normalizeWWN(wwn));
        }
        return normalized;
    }
}
