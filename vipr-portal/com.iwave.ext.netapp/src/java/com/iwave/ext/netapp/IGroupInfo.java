/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * @author sdorcas
 * 
 */
public class IGroupInfo implements Serializable {

    private static final long serialVersionUID = -3150288215047336783L;

    private String name = "";
    private IGroupType type = null;
    private LunOSType osType = null;
    private List<String> initiators = new ArrayList<String>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IGroupType getType() {
        return type;
    }

    public void setType(IGroupType type) {
        this.type = type;
    }

    public LunOSType getOsType() {
        return osType;
    }

    public void setOsType(LunOSType osType) {
        this.osType = osType;
    }

    public List<String> getInitiators() {
        return initiators;
    }

    public void addInitiator(String initiator) {
        initiators.add(initiator);
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("name", name);
        builder.append("type", type);
        builder.append("osType", osType);
        builder.append("initiators", initiators);
        return builder.toString();
    }

}
