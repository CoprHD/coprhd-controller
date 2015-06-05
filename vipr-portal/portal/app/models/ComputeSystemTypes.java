/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models;

import java.util.List;

import util.StringOption;

import com.google.common.collect.Lists;

public class ComputeSystemTypes {
    private static final String OPTION_PREFIX = "ComputeSystemType";
    
    public static final String UCS = "ucs";
    public static final String CSERIES = "cseries";
    public static final String SERVER_UCS = "cisco_ucsm";
    public static final String[] VALUES = { UCS };  //remove CSERIES for 2.2



    public static final StringOption[] OPTIONS = { 

    };


    public static boolean isUcs(String type) {
        return UCS.equals(type);
    }
    public static boolean isCSeries(String type) {
        return CSERIES.equals(type);
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
