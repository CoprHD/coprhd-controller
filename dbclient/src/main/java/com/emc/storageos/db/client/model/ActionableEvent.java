/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("Event")
public class ActionableEvent extends DataObject implements TenantResource {

    private String _message;
    private String _controllerClass;
    private String _orchestrationMethod;
    private URI _tenant;

    @Name("message")
    public String getMessage() {
        return _message;
    }

    public void setMessage(String message) {
        this._message = message;
        setChanged("message");
    }

    @Name("controllerClass")
    public String getControllerClass() {
        return _controllerClass;
    }

    public void setControllerClass(String controllerClass) {
        this._controllerClass = controllerClass;
        setChanged("controllerClass");
    }

    @Name("orchestrationMethod")
    public String getOrchestrationMethod() {
        return _orchestrationMethod;
    }

    public void setOrchestrationMethod(String orchestrationMethod) {
        this._orchestrationMethod = orchestrationMethod;
        setChanged("orchestrationMethod");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.model.TenantResource#getTenant()
     */
    @Override
    @RelationIndex(cf = "RelationIndex", type = TenantOrg.class)
    @Name("tenant")
    public URI getTenant() {
        return _tenant;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.model.TenantResource#setTenant(java.net.URI)
     */
    @Override
    public void setTenant(URI tenant) {
        _tenant = tenant;
        setChanged("tenant");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.model.TenantResource#auditParameters()
     */
    @Override
    public Object[] auditParameters() {
        return new Object[] { getMessage(), getTenant(), getId() };
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.model.TenantResource#getDataObject()
     */
    @Override
    public DataObject findDataObject() {
        return this;
    }
}