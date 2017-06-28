/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax.rest;

import java.util.HashMap;
import java.util.Map;

public class RestEndPoint {
    private String path;
    private Map<RestMethod, Object> actions;

    public RestEndPoint(String path) {
        this.path = path;
        this.actions = new HashMap<>();
    }

    public Map<RestMethod, Object> getActions() {
        return actions;
    }

    public boolean pathIsMatch(String rest_path) {
        // Perfect match.
        if (this.path.equals(rest_path)) {
            return true;
        }

        // Path with Id.
        return false;
    }
}
