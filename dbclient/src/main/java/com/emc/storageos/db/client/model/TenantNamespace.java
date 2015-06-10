/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * Object services namespace information for a tenant (reverse mapping from tenant to
 * namespace).
 */
@Cf("TenantNamespace")
@XmlRootElement(name = "tenant_namespace")
public class TenantNamespace extends DataObject {
    // namespace the tenant is associated to
    private String _namespace;

    private URI _defaultObjectProject;

    private URI _defaultObjectVirtualPool;

    private URI _defaultObjectReplicationGroup;

    public static URI EMPTY_URI = URI.create("");

    @XmlElement
    @Name("namespace")
    public String getNamespace() {
        return _namespace;
    }

    public void setNamespace(String namespace) {
        _namespace = namespace;
        setChanged("namespace");
    }

    @XmlElement
    @Name("defaultObjectProject")
    public URI getDefaultObjectProject() {
        return _defaultObjectProject;
    }

    public void setDefaultObjectProject(URI defaultProject) {
        _defaultObjectProject = defaultProject;
        setChanged("defaultObjectProject");
    }

    @XmlElement
    @Name("defaultObjectVirtualPool")
    public URI getDefaultObjectVirtualPool() {
        return _defaultObjectVirtualPool;
    }

    // TODO: not needed in v2
    public void setDefaultObjectVirtualPool(URI defaultObjectVirtualPool) {
        _defaultObjectVirtualPool = defaultObjectVirtualPool;
        setChanged("defaultObjectVirtualPool");
    }

    @XmlElement
    @Name("defaultObjectReplicationGroup")
    public URI getDefaultObjectReplicationGroup() {
        if (_defaultObjectReplicationGroup == null) {
            return EMPTY_URI;
        }
        return _defaultObjectReplicationGroup;
    }

    public void setDefaultObjectReplicationGroup(URI defaultObjectReplicationGroup) {
        _defaultObjectReplicationGroup = defaultObjectReplicationGroup;
        setChanged("defaultObjectReplicationGroup");
    }
}
