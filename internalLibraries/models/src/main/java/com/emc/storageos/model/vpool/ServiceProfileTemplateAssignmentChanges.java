/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;

/**
 * Class captures lists of URIs for Service Profile Templates to be assigned/unassigned
 * to/from the Compute Virtual Pool.
 */
public class ServiceProfileTemplateAssignmentChanges {

    private ServiceProfileTemplateAssignments add;
    private ServiceProfileTemplateAssignments remove;

    public ServiceProfileTemplateAssignmentChanges() {
    }

    public ServiceProfileTemplateAssignmentChanges(ServiceProfileTemplateAssignments add,
            ServiceProfileTemplateAssignments remove) {
        this.add = add;
        this.remove = remove;
    }

    // SPTs to be assigned.
    @XmlElement(name = "add")
    public ServiceProfileTemplateAssignments getAdd() {
        return add;
    }

    public void setAdd(ServiceProfileTemplateAssignments add) {
        this.add = add;
    }

    // SPTs to be unassigned.
    @XmlElement(name = "remove")
    public ServiceProfileTemplateAssignments getRemove() {
        return remove;
    }

    public void setRemove(ServiceProfileTemplateAssignments remove) {
        this.remove = remove;
    }

    public boolean hasRemoved() {
        return (remove != null && !remove.getServiceProfileTemplates().isEmpty());
    }

    public boolean hasAdded() {
        return (add != null && !add.getServiceProfileTemplates().isEmpty());
    }

}
