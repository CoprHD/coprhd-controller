/**
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
package com.emc.vipr.model.sys.healthmonitor;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents test details - name, value and its status (if any) as returned by diagtool.
 */
@XmlRootElement(name = "param")
public class TestParam {

    public TestParam() {
    }

    public TestParam(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public TestParam(String key, String value, String status) {
        this.key = key;
        this.value = value;
        this.status = status;
    }

    private String key;
    private String value;
    private String status;

    @XmlElement(name = "key")
    public String getKey() {
        return key;
    }

    @XmlElement(name = "value")
    public String getValue() {
        return value;
    }

    @XmlElement(name = "status")
    public String getStatus() {
        return status;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return key + "=" + value + (status != null ? " [" + status + "]" : "");
    }
}
