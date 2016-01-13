/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import util.StringOption;

public class FileProtectionSystemTypes {

    public static final String NONE = "NONE";
    public static final String LOCAL = "LOCAL";
    public static final String REMOTE = "REMOTE";

    public static final String[] PROTECTION_SYSTEM = { NONE, LOCAL, REMOTE };

    public static final StringOption[] PROTECTION_SYSTEM_OPTIONS = StringOption.options(PROTECTION_SYSTEM, "FileProtectionSystemTypes",
            false);

    public static boolean isTypeLocal(String type) {
        return LOCAL.equals(type);
    }

    public static boolean isTypeRemote(String type) {
        return REMOTE.equals(type);
    }
}
