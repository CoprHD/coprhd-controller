/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.impl.TypeMap;

import java.net.URI;
import java.util.List;


/**
 * @class DisposedObject
 * Many elements of the system run discovery task trying to identify external resources automatically.
 * User might want to exclude some of this resources and place them outside the Array Management System.
 * We need to
 * There is a requirements for the system to remove

 */
@Cf("DecommissionedResource")
public class DecommissionedResource extends DataObject {


    protected String _user;
    protected String _type;
    protected String _nativeGuid;
    protected URI _decommissionedId;

    @Name("user")
    public String getUser() {
        return _user;
    }
    public void setUser(String user) {
        _user = user;
        setChanged("user");
    }

    @Name("type")
    public String getType() {
        return _type;
    }
    public void setType(String type) {
        _type = type;
        setChanged("type");
    }

    @Name("nativeGuid")
    @AlternateId("AltIdIndex")
    public String getNativeGuid() {
        return _nativeGuid;
    }
    public void setNativeGuid(String nativeGuid) {
        _nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }

    @Name("decommissionedId")
    @AlternateId("AltIdIndex")
    public URI getDecommissionedId() {
        return _decommissionedId;
    }
    public void setDecommissionedId(URI disposedId) {
        _decommissionedId = disposedId;
        setChanged("decommissionedId");
    }


    public static boolean checkDecommissioned(DbClient dbClient, URI id){
        List<URI> oldResources = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getDecommissionedResourceIDConstraint(id.toString()));
        if(oldResources != null )
        {
            List<DecommissionedResource> objects = dbClient.queryObject(DecommissionedResource.class, oldResources);
            for(DecommissionedResource decomObj : objects ) {
                if(!decomObj.getInactive())  {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean checkDecommissioned(DbClient dbClient, String nativeGuid, Class<? extends DataObject> type){
        List<URI> oldResources = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getDecommissionedResourceNativeGuidConstraint(nativeGuid));
        if(oldResources != null )
        {
            List<DecommissionedResource> objects = dbClient.queryObject(DecommissionedResource.class, oldResources);
            for( DecommissionedResource decomObj : objects ) {
                if(decomObj.getType().equalsIgnoreCase(TypeMap.getCFName(type))  &&
                        !decomObj.getInactive())  {
                    return true;
                }
            }
        }
        return false;
    }

    public static int removeDecommissionedFlag(DbClient dbClient, String nativeGuid, Class<? extends DataObject> type){
        List<URI> oldResources = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getDecommissionedResourceNativeGuidConstraint(nativeGuid) );
        int cleared = 0;
        if(oldResources != null )
        {
            List<DecommissionedResource> objects = dbClient.queryObject(DecommissionedResource.class, oldResources);
            for( DecommissionedResource decomObj : objects)  {
                if(decomObj.getType().equalsIgnoreCase(TypeMap.getCFName(type))  &&
                        !decomObj.getInactive())  {
                    decomObj.setInactive(true);
                    cleared++;
                }
            }
            dbClient.persistObject(objects);
        }
        return cleared;
    }

}
