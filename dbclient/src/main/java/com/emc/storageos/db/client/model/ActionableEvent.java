/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.io.Serializable;
import java.net.URI;

@Cf("Event")
public class ActionableEvent extends DataObject implements TenantResource {

    public class Method implements Serializable {
        public String _orchestrationMethod;
        public Object[] _args;

        public Method(String orchestrationMethod, Object... args) {
            this._orchestrationMethod = orchestrationMethod;
            this._args = args;
        }
    }

    private String _message;
    private String _controllerClass;
    private URI _tenant;
    private Method _method;

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

    @Name("method")
    public Method getMethod() {
        return _method;
    }

    public void setMethod(Method method) {
        this._method = method;
        setChanged("method");
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