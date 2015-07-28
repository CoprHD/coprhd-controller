/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.util;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;

/**
 * Utility class to validate and formats the WWN.
 */
public class WWNUtility {
    /**
     * Regular Expression to match the WWN.
     */
    private static final String WWN_PATTERN = "(([0-9a-fA-F]){2}:){7}([0-9a-fA-F]){2}";
    private static final String WWN_ALIAS_PATTERN = "([a-zA-Z0-9\\-_\\^\\$]{1,64})";
    private static final String WWN_NO_COLON_PATTERN = "([0-9a-fA-F]){16}";

    /**
     * Validate the WWN format.
     * 
     * @param wwn : WWN.
     * @return true if it is valid else false.
     */
    public static boolean isValidWWN(String wwn) {
        return wwn != null && wwn.matches(WWN_PATTERN);
    }

    /**
     * Validate the WWN format.
     * 
     * @param wwn : WWN.
     * @return true if it is valid else false.
     */
    public static boolean isValidNoColonWWN(String wwn) {
        return wwn != null && wwn.matches(WWN_NO_COLON_PATTERN);
    }

    /**
     * Return the WWN in upper case with out colons.
     * 
     * @param wwnWithColon
     * @return
     */
    public static String getUpperWWNWithNoColons(String wwnWithColon) {
        return wwnWithColon != null ? wwnWithColon.replace(":", "").toUpperCase() : "";
    }

    /**
     * Returns the WWN in upper case with colon format.
     * 
     * @param wwwWithNoColon.
     * @return
     */
    public static String getWWNWithColons(String wwwWithNoColon) {
        StringBuilder builder = new StringBuilder();
        Iterable<String> s = Splitter.fixedLength(2).split(wwwWithNoColon);
        Iterator<String> t = s.iterator();
        while (t.hasNext()) {
            builder.append(t.next());
            if (t.hasNext()) {
                builder.append(":");
            }
        }
        return builder.toString().toUpperCase();
    }

    /**
     * A valid WWN alias would be at most 64 character in length. Valid characters are:
     * a-z (insensitive)
     * 1-9
     * - _ $, or ^
     * 
     * @param address
     * @return
     */
    public static boolean isValidWWNAlias(String address) {
        boolean valid = false;

        if (address != null) {
            String alias = address.trim();
            Pattern aliasPattern = Pattern.compile(WWN_ALIAS_PATTERN);
            Matcher matcher = aliasPattern.matcher(alias);

            valid = matcher.matches() && matcher.group(0).length() == alias.length();
        }

        return valid;
    }
}
