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
package com.emc.vipr.model.sys.healthmonitor;

import com.emc.vipr.model.sys.healthmonitor.HealthMonitorConstants;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents test name, status and its details that are returned by diagtool.
 */
@XmlRootElement(name = "test")
public class DiagTest {

    private String name;
    private String status;
    private List<TestParam> testParams;
    
    public DiagTest() {
        this.name = HealthMonitorConstants.DIAG_UNKNOWN;
    }

    public DiagTest(String name, String status) {
        this.name = name;
        this.status = status;
    }

    public DiagTest(String name, String status, List<TestParam> testParams) {
        this.name = name;
        this.status = status;
        this.testParams = testParams;
    }

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    @XmlElement(name = "status")
    public String getStatus() {
        return status;
    }

    @XmlElementWrapper(name = "params")
    @XmlElement(name = "param")
    public List<TestParam> getTestParams() {
        if (testParams == null) {
            testParams = new ArrayList<TestParam>();
        }
        return testParams;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setTestParams(List<TestParam> testParams) {
        this.testParams = testParams;
    }

    @Override
    public String toString() {
        if (name.equals("EMC upgrade repository") && status.equals("CONFIGURED,UNREACHABLE") && testParams != null && testParams.size() > 0) {
            return name + ": [" + status + "]." + " Details: Unable to reach the update repository, please verify ViPR's Internet connection and check if the repository URL " + testParams.get(0).getValue() + " is correct." ;
        }
        StringBuffer sb = new StringBuffer();
        if (testParams != null && testParams.size() > 0) {
            for (TestParam param : testParams) {
                sb.append(param + ", ");
            }
        }
        return name + ": [" + status + "]." + (sb.length() > 0 ? "Details: "+sb.toString()
                .substring(0, sb.length() - 2) : "");
    }
}
