/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.recovery;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="db_offline_status" )
public class DbOfflineStatus {
    private boolean outageTimeExceed;

    public DbOfflineStatus () {}
    
    public DbOfflineStatus (boolean outageTimeExceeded) {
        outageTimeExceed = outageTimeExceeded;
    }

    @XmlElement(name="db_offline_info")
    public boolean getOutageTimeExceeded() {
        return this.outageTimeExceed;
    }

    public void setOutageTimeExceeded(boolean outageTimeExceeded) {
        this.outageTimeExceed = outageTimeExceeded;
    }
}
