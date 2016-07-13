/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.io.Serializable;
import java.net.URI;

@Cf("Event")
public class ActionableEvent extends DataObject implements TenantResource {

    public static class Method implements Serializable {

        private static final long serialVersionUID = 1L;
        protected String orchestrationMethod;
        protected Object[] args;

        public Method(String orchestrationMethod, Object[] args) {
            this.orchestrationMethod = orchestrationMethod;
            this.args = args;
        }

        public String getOrchestrationMethod() {
            return this.orchestrationMethod;
        }

        public Object[] getArgs() {
            return this.args;
        }

        public void setOrchestrationMethod(String name) {
            this.orchestrationMethod = name;
        }

        public void setArgs(Object... args) {
            this.args = args;
        }

        public byte[] serialize() {
            return com.emc.storageos.coordinator.client.service.impl.GenericSerializer.serialize(this);
        }

        public static Method deserialize(byte[] array) {
            return (Method) com.emc.storageos.coordinator.client.service.impl.GenericSerializer.deserialize(array);
        }

    }

    private String _message;
    private String _controllerClass;
    private NamedURI resource;
    private String status;
    private URI _tenant;
    private byte[] _method;

    public enum Status {
        pending, approved, declined
    }

    @Name("message")
    public String getMessage() {
        return _message;
    }

    public void setMessage(String message) {
        this._message = message;
        setChanged("message");
    }

    @Name("method")
    public byte[] getMethod() {
        return _method;
    }

    public void setMethod(byte[] method) {
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

    @Name("resource")
    public NamedURI getResource() {
        return resource;
    }

    public void setResource(NamedURI resource) {
        this.resource = resource;
        setChanged("resource");
    }

    @Name("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        setChanged("status");
    }

}