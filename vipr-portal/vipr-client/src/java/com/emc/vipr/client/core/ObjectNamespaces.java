/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.object.ObjectNamespaceList;
import com.emc.storageos.model.object.ObjectNamespaceRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * ObjectNamespaces resources.
 * <p>
 * Base URL: <tt>/vdc/objectnamespaces</tt>
 */
public class ObjectNamespaces extends AbstractCoreBulkResources<ObjectNamespaceRestRep> implements
        TopLevelResources<ObjectNamespaceRestRep> {

    public ObjectNamespaces(ViPRCoreClient parent, RestClient client) {
        super(parent, client, ObjectNamespaceRestRep.class, PathConstants.OBJECT_NAMESPACE_URL);
    }

   /**
    * Gets list of all object storage namespaces
    * 
    * @return ObjectnamespaceList
    */
    public ObjectNamespaceList getObjectNamespaces() {
        ObjectNamespaceList response = client.get(ObjectNamespaceList.class, baseUrl);
        return response;
    }
    
    /**
     * gets the details of a particular object namespace
     * 
     * @param Id details required for
     * @return ObjectNamespaceRestRep
     */
    public ObjectNamespaceRestRep getObjectNamespace(URI Id) {
        ObjectNamespaceRestRep response = client.get(ObjectNamespaceRestRep.class, baseUrl+"/"+Id);
        return response;
    }
    
    @Override
    public List<NamedRelatedResourceRep> list() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<ObjectNamespaceRestRep> getAll() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<ObjectNamespaceRestRep> getAll(ResourceFilter<ObjectNamespaceRestRep> filter) {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    protected List<ObjectNamespaceRestRep> getBulkResources(BulkIdParam input) {
        // TODO Auto-generated method stub
        return null;
    }
}
