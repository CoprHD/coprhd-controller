/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.api;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class will contain a REGEX string and a name. It is used for finding output line matches
 * for processing by the AbstractScaleIOQueryCommand implementation.
 */
public class ParsePattern {
    String patternString;
    Pattern pattern;
    String propertyName;

    public ParsePattern(String pattern, String propertyName) {
        this.patternString = pattern;
        this.pattern = Pattern.compile(pattern);
        this.propertyName = propertyName;
    }

    public List<String> isMatch(String string) {
        List<String> list = null;
        Matcher matcher;
        if ((matcher = pattern.matcher(string)).matches()) {
            list = new ArrayList<String>();
            int count = matcher.groupCount();
            for (int index = 1; index <= count; index++) {
                list.add(matcher.group(index));
            }
        }
        return list;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
