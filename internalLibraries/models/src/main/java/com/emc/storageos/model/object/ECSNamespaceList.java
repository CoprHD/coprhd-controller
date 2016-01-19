/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

/**
 * Class represents a return type that returns the id and self link for a
 * list of ECS Namespaces.
 * Generic interface for all object storage systems exposing namespaces
 */ 
@XmlRootElement(name = "object_namespaces")
public class ECSNamespaceList {
    private List<NamedRelatedResourceRep> namespaces;

    public ECSNamespaceList() {
    }

    public ECSNamespaceList(List<NamedRelatedResourceRep> namespaces) {
        this.namespaces = namespaces;
    }
    /**
     * List of individual ECS namespaces
     * 
     * @valid none
     */
    @XmlElement(name = "object_namespace")
    public List<NamedRelatedResourceRep> getNamespaces() {
        if (namespaces == null) {
            namespaces = new ArrayList<NamedRelatedResourceRep>();
        }
        return namespaces;
    }

    public void setNamespaces(List<NamedRelatedResourceRep> namespaces) {
        this.namespaces = namespaces;
    }
}
