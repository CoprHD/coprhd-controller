/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.hds.model;

public class FreeLun {
    
    private String lun;
    
    public FreeLun() {
        
    }

    /**
     * @return the lun
     */
    public String getLun() {
        return lun;
    }

    /**
     * @param lun the lun to set
     */
    public void setLun(String lun) {
        this.lun = lun;
    }
}
