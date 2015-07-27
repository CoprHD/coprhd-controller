/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ViPRServiceTest {
    // Valid conversion patterns
    private static final String CONVERSIONS = "bBhHsScCdoxXeEfgGaAtT%";
    private static Pattern MESSAGE_FORMAT = Pattern.compile("\\%[^" + CONVERSIONS + "]*[" + CONVERSIONS + "]");
    private static volatile ResourceBundle MESSAGES;

    @BeforeClass
    public static void loadMessages() {
        MESSAGES = ResourceBundle.getBundle(ViPRServiceTest.class.getPackage().getName() + ".ViPRService");
    }

    @Test
    public void validMessagePatterns() {
        Set<String> invalid = new HashSet<>();
        for (String key : MESSAGES.keySet()) {
            String value = MESSAGES.getString(key);
            if (!isValidMessage(value)) {
                invalid.add(key);
            }
        }
        Assert.assertTrue("Invalid message formats: " + invalid, invalid.isEmpty());
    }

    private boolean isValidMessage(String value) {
        Matcher matcher = MESSAGE_FORMAT.matcher(value);
        List<Object> args = new ArrayList<>();
        while (matcher.find()) {
            String specifier = matcher.group();
            char conversion = specifier.toLowerCase().charAt(specifier.length() - 1);
            switch (conversion) {
            case 'c':
                args.add('c');
                break;
            case 'd':
            case 'o':
            case 'x':
                args.add(0);
                break;
            case 'e':
            case 'f':
            case 'g':
            case 'a':
                args.add(0.0);
                break;
            case 't':
                args.add(new Date());
                break;
            case '%':
                // skip
                break;
            default:
                args.add("");
                break;
            }
        }

        try {
            String.format(value, args.toArray());
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }
}
