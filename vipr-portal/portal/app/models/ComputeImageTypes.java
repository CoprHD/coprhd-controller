/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import util.StringOption;

import com.google.common.collect.Lists;

public class ComputeImageTypes {
    private static final String OPTION_PREFIX = "ComputeImageType";
    
    public static final String ESX = "esx";
    public static final String LINUX = "linux";
    public static final String[] VALUES = { ESX, LINUX };



    public static final StringOption[] OPTIONS = { 

    };


    public static boolean isUcs(String type) {
        return ESX.equals(type);
    }
    public static boolean isCSeries(String type) {
        return LINUX.equals(type);
    }
 

    public static StringOption option(String type) {
        return new StringOption(type, getDisplayValue(type));
    }

    public static List<StringOption> options(String... types) {
        List<StringOption> options = Lists.newArrayList();
        for (String type : types) {
            options.add(option(type));
        }
        return options;
    }

    public static String getDisplayValue(String type) {
        return StringOption.getDisplayValue(type, OPTION_PREFIX);
    }
}
