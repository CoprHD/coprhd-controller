/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.model;

public class AixVersion {
    
    private String version;
    
    public AixVersion(String version) {
       setVersion(version);
    }
    
    @Override
    public String toString() {
        return String.format("%s", version );
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
