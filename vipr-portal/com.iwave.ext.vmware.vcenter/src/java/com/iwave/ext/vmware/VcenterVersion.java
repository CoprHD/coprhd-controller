/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.vmware;

public class VcenterVersion {

    private String version;
    
    public VcenterVersion() {
        super();
    }
    
    public VcenterVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
    
    
    @Override
    public String toString() {
        return this.version;
    }
}
