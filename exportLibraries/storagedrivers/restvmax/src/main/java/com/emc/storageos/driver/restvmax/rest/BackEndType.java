/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax.rest;

import java.util.Base64;

public enum BackendType {
    VMAX {
        public Integer getPort() {
            return 8443;
        }
        public String getAuthField() {
            return "Authorization";
        }
        public String getAuthFieldValue(String username, String password) {
            return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        }
        public String getPathVendorPrefix() {
            return "/univmax/restapi";
        }
    };
    public abstract Integer getPort();
    public abstract String getAuthField();
    public abstract String getAuthFieldValue(String username, String password);
    public abstract String getPathVendorPrefix();
}