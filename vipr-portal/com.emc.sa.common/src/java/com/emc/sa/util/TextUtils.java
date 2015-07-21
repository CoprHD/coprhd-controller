/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.apache.commons.lang3.text.StrTokenizer;

public class TextUtils {
    /**
     * Parses some comma-separated text into a list of values.
     * 
     * @param text
     *        the CSV text.
     * @return the list of values.
     */
    public static List<String> parseCSV(String text) {
        if (StringUtils.isNotEmpty(text)) {
            return StrTokenizer.getCSVInstance(text).getTokenList();
        }
        else {
            return new ArrayList<>();
        }
    }

    /**
     * Formats some values into comma-separated text.
     * 
     * @param values
     *        the values to format.
     * @return the CSV text.
     */
    public static String formatCSV(Iterable<String> values) {
        StrBuilder sb = new StrBuilder();
        for (String value : values) {
            sb.appendSeparator(",");
            sb.append("\"");
            if (StringUtils.isNotEmpty(value)) {
                for (int i = 0; i < value.length(); i++) {
                    char ch = value.charAt(i);
                    if (ch == '"') {
                        sb.append('"');
                    }
                    sb.append(ch);
                }
            }
            sb.append("\"");
        }
        return sb.toString();
    }

    /**
     * Formats some values into comma-separated text.
     * 
     * @param values
     *        the values to format.
     * @return the CSV text.
     */
    public static String formatCSV(String... values) {
        return formatCSV(Arrays.asList(values));
    }
}
