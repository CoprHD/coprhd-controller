/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;

/**
 * Class captures a list of URIs for the Service Profile Templates assigned to the
 * Compute Virtual Pool.
 */
public class ServiceProfileTemplateAssignments {

    private Set<String> templates;

    public ServiceProfileTemplateAssignments() {}
    
    public ServiceProfileTemplateAssignments(Set<String> templates) {
        this.templates = templates;
    }

    // The set of SPT URIs.
    @XmlElement(name = "service_profile_template")
    public Set<String> getServiceProfileTemplates() {
        if (templates == null) {
        	templates = new HashSet<String>();
        }
        return templates;
    }

    public void setServiceProfileTemplates(Set<String> templates) {
        this.templates = templates;
    }    
}
