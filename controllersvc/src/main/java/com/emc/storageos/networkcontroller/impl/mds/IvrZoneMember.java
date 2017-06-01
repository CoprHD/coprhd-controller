/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IvrZoneMember implements Serializable {
    private static final Logger _log = LoggerFactory.getLogger(IvrZoneMember.class);

    private String pwwn = "";
    private Integer vsanId = 0;

    public IvrZoneMember(String pwwn, Integer vsanId) {
        setPwwn(pwwn);
        setVsanId(vsanId);
    }

    public String getPwwn() {
        return pwwn;
    }

    public void setPwwn(String pwwn) {
        this.pwwn = pwwn;
    }

    public Integer getVsanId() {
        return vsanId;
    }

    public void setVsanId(Integer vsanId) {
        this.vsanId = vsanId;
    }

    public void print() {
        _log.info("IvrZoneMember: " + getPwwn() + "; vsan:" + getVsanId());
    }

    @Override
    public int hashCode() {
        return getPwwn().hashCode() + getVsanId().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IvrZoneMember) {
            IvrZoneMember zoneMember = (IvrZoneMember) obj;
            return zoneMember != null && zoneMember.getPwwn() != null && getPwwn() != null && 
                    getPwwn().equalsIgnoreCase(zoneMember.getPwwn()); 
        }
        return false;
    }
    
    public boolean contains(Object obj) {
    	if (obj instanceof IvrZoneMember) {
    		IvrZoneMember zoneMember = (IvrZoneMember)obj;
    		 return zoneMember != null && getPwwn() != null && 
                     getPwwn().equalsIgnoreCase(zoneMember.getPwwn());
    	}
    	return false;
    }

}
