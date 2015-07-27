/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Provides system property resolution.  System properties are specified
 * as ${<i>property.name</i>}.  If a property is not set, it is ignored.
 * 
 * <p>This utility originates in the adapters codebase and has been ported to
 * orchestrator.
 */
public class SystemProperties {
    /** Pattern for matching property names. */
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{(.*?)\\}");
    
    /**
     * <p>Resolves any system properties within the string.  Properties are 
     *    denoted by <tt>${<i>property.name</i>}</tt>.  If no system property
     *    is found, they are left alone.
     * 
     * @param  str
     *         the string to resolve.
     * @return the resolved string.
     */
    public static String resolve(String str) {
        if ((str == null) || str.equals("")) {
            return str;
        }
        
        Matcher m = PROPERTY_PATTERN.matcher(str);
        
        StringBuffer sb = new StringBuffer();
        int start = 0;
        
        while (m.find()) {
            String propertyName  = m.group(1);
            String propertyValue = System.getProperty(propertyName, null);
            
            if (propertyValue == null) {
                propertyValue = m.group();
            }
            sb.append(str.substring(start, m.start()));
            sb.append(propertyValue);
            
            start = m.end();
        }
        
        if (start < str.length()) {
            sb.append(str.substring(start));
        }
        
        return sb.toString();
    }
}
