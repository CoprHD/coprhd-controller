/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import util.StringOption;

public class FileRpoType {

    public static final String MINUTES = "MINUTES";
    public static final String HOURS = "HOURS";
    public static final String DAYS = "DAYS";

    public static final String[] RPO_VALUES = { HOURS, MINUTES, DAYS };

    public static final StringOption[] RPO_OPTIONS = StringOption.options(RPO_VALUES, "FileRpoType", false);

}
