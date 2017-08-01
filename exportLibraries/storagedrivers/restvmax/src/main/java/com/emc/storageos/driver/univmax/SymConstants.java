/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax;

public class SymConstants {
    public final static String DRIVER_NAME = "unimax";

    private SymConstants() {
        throw new IllegalAccessError("SymConstants class");
    }

    public static class StatusCode {
        public final static int EXCEPTION = -1;// Has exception and no response got from vmax3
        public final static int OK = 200;// OK
        public final static int CREATED = 201;// Created
        public final static int NO_CONTENT = 204;
    }

    public static class MarkHolder {

        public final static String QUESTION_MARK = "?";
        public final static String EQUAL_MARK = "=";
        public final static String AND_MARK = "&";
        public final static String NEW_LINE_MARK = "\n";
    }

}
