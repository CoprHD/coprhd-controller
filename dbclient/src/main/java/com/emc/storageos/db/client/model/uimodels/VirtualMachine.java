/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.*;

@Deprecated
@Cf("VirtualMachine")
public class VirtualMachine extends ModelObject {

    public static final String TEMPLATE = "template";
    public static final String RUNNING = "running";
    public static final String DATACENTER_ID = "datacenterId";

    private Boolean template = Boolean.FALSE;

    private Boolean running = Boolean.FALSE;
    
    private NamedURI datacenterId;

    public VirtualMachine() {
    }        
    
    public VirtualMachine(String label) {
        this.setLabel(label);
    }    

    @Name(TEMPLATE)
    public Boolean getTemplate() {
        return template;
    }

    public void setTemplate(Boolean template) {
        this.template = template;
        setChanged(TEMPLATE);
    }

    @Name(RUNNING)
    public Boolean getRunning() {
        return running;
    }

    public void setRunning(Boolean running) {
        this.running = running;
        setChanged(RUNNING);
    }

    @NamedRelationIndex(cf = "NamedRelationIndex", type = VcenterDataCenter.class)
    @Name(DATACENTER_ID)
    public NamedURI getDatacenterId() {
        return datacenterId;
    }

    public void setDatacenterId(NamedURI datacenterId) {
        this.datacenterId = datacenterId;
        setChanged(DATACENTER_ID);
    }

    public String toString() {
        return getLabel();
    }    
        
}
