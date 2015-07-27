/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.math.IntRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

public class IvrVsanConfiguration implements Serializable {
    private static final Logger _log = LoggerFactory.getLogger(IvrVsanConfiguration.class);


    private String switchWwn;
    private Set<Integer> vsans = Sets.newHashSet();
    private List<IntRange> vsansRanges;

    private boolean localSwitch;    
    
    public String getSwitchWwn() {
        return switchWwn;
    }


    public void setSwitchWwn(String switchWwn) {
        this.switchWwn = switchWwn;
    }


    public Set<Integer> getVsans() {
        return vsans;
    }


    public void setVsans(Set<Integer> vsans) {
        this.vsans = vsans;
    }


    public boolean isLocalSwitch() {
        return localSwitch;
    }


    public void setLocalSwitch(boolean localSwitch) {
        this.localSwitch = localSwitch;
    }
    
    
    public List<IntRange> getVsansRanges() {
        if ( vsansRanges == null) {
            vsansRanges = new ArrayList<IntRange>();
        }
        return vsansRanges;
    }


    public void setVsansRanges(List<IntRange> vsansRanges) {
        this.vsansRanges = vsansRanges;
    }    

    /**
     * Determine if given vsan is an ivr vsan
     * @param vsanId
     * @return
     */
    public boolean isIvrVsan(int vsanId) {
        boolean inRange=vsans.contains(vsanId);
        if ( !inRange ) {
            for (IntRange range : getVsansRanges()) {
                inRange = range.containsInteger(vsanId);
                if (inRange) {
                    break;
                }
            }
        }
        return inRange;
    }
	
	public void print() {
        _log.info(toString());
    }
	
	public String toString() {
        StringBuffer vsans = new StringBuffer();
        for (Integer vsan : getVsans()) {
            vsans.append(vsan).append(",");
        }
        
        List<IntRange> ranges = getVsansRanges();
        if ( ranges != null) {
            for (IntRange range : ranges) {
                vsans.append(range.getMinimumInteger()).append("-").append(range.getMaximumInteger()).append("\n");
            }
        }
        
        return "Ivr switch: " + getSwitchWwn() + " local: " + isLocalSwitch() + " vsans: " + vsans;
	}

}
