/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.healthmonitor.beans;

import java.util.List;

/**
 * Metadata class for diagnostic tests
 */
public class DiagTestMetadata {

    private String name;
    private List<String> ok;
    private List<String> warn;
    private List<String> error;
    private List<String> crit;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getOk() {
        return ok;
    }

    public void setOk(List<String> ok) {
        this.ok = ok;
    }

    public List<String> getWarn() {
        return warn;
    }

    public void setWarn(List<String> warn) {
        this.warn = warn;
    }

    public List<String> getError() {
        return error;
    }

    public void setError(List<String> error) {
        this.error = error;
    }

    public List<String> getCrit() {
        return crit;
    }

    public void setCrit(List<String> crit) {
        this.crit = crit;
    }
}
