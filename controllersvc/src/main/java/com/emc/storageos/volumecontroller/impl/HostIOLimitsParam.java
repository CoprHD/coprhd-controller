/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

public class HostIOLimitsParam {

    private Integer _hostIOLimitBandwidth;
    private Integer _hostIOLimitIOPs;
    
    public Integer getHostIOLimitBandwidth() {
        return _hostIOLimitBandwidth;
    }
    
    public void setHostIOLimitBandwidth(Integer _hostIOLimitBandwidth) {
        this._hostIOLimitBandwidth = _hostIOLimitBandwidth;
    }

    public Integer getHostIOLimitIOPs() {
        return _hostIOLimitIOPs;
    }

    public void setHostIOLimitIOPs(Integer _hostIOLimitIOPs) {
        this._hostIOLimitIOPs = _hostIOLimitIOPs;
    }

    public boolean isHostIOLimitIOPsSet() {
        return _hostIOLimitIOPs != null && _hostIOLimitIOPs > 0;
    }

    public boolean isHostIOLimitBandwidthSet() {
        return _hostIOLimitBandwidth != null && _hostIOLimitBandwidth > 0;
    }    
    
    /**
     * Per VMAX spec, 0 or null limit indicates unlimited value.  Hence, null and 0 are equals as well
     * @param limit1
     * @param limit2
     * @return
     */
    public static boolean isEqualsLimit(Integer limit1, Integer limit2) {
        if ( limit1 == null) 
            limit1 = 0;
        if (limit2 == null) 
            limit2 = 0;
        
        return limit1.equals(limit2); 
    }

}
