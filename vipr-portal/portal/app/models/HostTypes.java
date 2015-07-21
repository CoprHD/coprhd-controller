/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import util.StringOption;

public class HostTypes {
    public static final String OPTION_PREFIX = "HostType";

    public static final String LINUX = "Linux";
    public static final String WINDOWS = "Windows";
    public static final String HPUX = "HPUX";
    public static final String AIX = "AIX";
    public static final String AIXVIO = "AIXVIO";
    public static final String SUNVCS = "SUNVCS";
    public static final String OTHER = "Other";
    public static final String ESX = "Esx";
    public static final String No_OS = "No_OS";
    public static final String[] STANDARD_CREATION_TYPES = { WINDOWS, LINUX, AIX, AIXVIO, ESX, HPUX, SUNVCS, OTHER };
    public static final String[] STANDARD_VIEW_TYPES = { WINDOWS, LINUX, AIX, AIXVIO, HPUX, SUNVCS, ESX, OTHER, No_OS };

    public static boolean isAIX(String type) {
        return AIX.equals(type);
    }

    public static boolean isAIXVIO(String type) {
        return AIXVIO.equals(type);
    }

    public static boolean isLinux(String type) {
        return LINUX.equals(type);
    }

    public static boolean isWindows(String type) {
        return WINDOWS.equals(type);
    }
    
    public static boolean isHPUX(String type) {
    	return HPUX.equals(type);
    }
    
    public static boolean isSUNVCS(String type) {
        return SUNVCS.equals(type);
    }

    public static boolean isOther(String type) {
        return OTHER.equals(type);
    }

    public static boolean isEsx(String type) {
        return ESX.equals(type);
    }

    public static boolean isNoOS(String type) {
        return No_OS.equals(type);
    }

    public static StringOption option(String type) {
        return new StringOption(type, getDisplayValue(type));
    }

    public static String getDisplayValue(String type) {
        return StringOption.getDisplayValue(type, OPTION_PREFIX);
    }
}
