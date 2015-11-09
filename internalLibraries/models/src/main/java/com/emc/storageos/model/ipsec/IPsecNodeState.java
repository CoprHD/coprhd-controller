/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.ipsec;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "node_state")
public class IPsecNodeState {

    private String version;
    private String runTimeState;

    @XmlElement(name = "node_version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @XmlElement(name = "runtime_state")
    private String getRunTimeState() {
        return this.runTimeState;
    }

    public void setRunTimeState(String runTimeState) {
        this.runTimeState = runTimeState;
    }
}
