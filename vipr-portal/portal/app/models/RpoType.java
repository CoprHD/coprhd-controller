/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models;

import util.StringOption;

public class RpoType {
    public static final String SECONDS = "SECONDS";
    public static final String MINUTES = "MINUTES";
    public static final String HOURS = "HOURS";
    public static final String WRITES = "WRITES";
    public static final String BYTES = "BYTES";
    public static final String KB = "KB";
    public static final String MB = "MB";
    public static final String GB = "GB";
    public static final String TB = "TB";

    public static final String[] VALUES = {
        SECONDS, MINUTES, HOURS, WRITES, BYTES, KB, MB, GB, TB
    };

    public static final StringOption[] OPTIONS = StringOption.options(VALUES, "RpoType", false);
}
