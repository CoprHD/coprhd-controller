/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc;

public class SymConstants {
    private SymConstants() {
        throw new IllegalAccessError("SbdriverConstants class");
    }

    public final static String HOST_FORMAT = "https://%s:%d";
    public final static String BASE_URL = "/univmax/restapi";
    public final static String END_POINT_SLO_PROVISIONING = "/sloprovisioning";
    public final static String END_POINT_SYMMETRIX = "/symmetrix";

    public static class MarkHolder {

        public final static String QUESTION_MARK = "?";
        public final static String EQUAL_MARK = "=";
        public final static String AND_MARK = "&";
    }

}
