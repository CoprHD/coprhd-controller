/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.io.Serializable;
import java.net.URI;

@Cf("Event")
public class ActionableEvent extends DataObject implements TenantResource {

    private String description;
    private NamedURI resource;
    private String eventStatus;
    private URI tenant;
    private byte[] method;

    public enum Status {
        pending, approved, declined
    }

    @Name("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        setChanged("description");
    }

    @Name("method")
    public byte[] getMethod() {
        return method;
    }

    public void setMethod(byte[] method) {
        this.method = method;
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
        return tenant;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.model.TenantResource#setTenant(java.net.URI)
     */
    @Override
    public void setTenant(URI tenant) {
        this.tenant = tenant;
        setChanged("tenant");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.model.TenantResource#auditParameters()
     */
    @Override
    public Object[] auditParameters() {
        return new Object[] { getDescription(), getTenant(), getId() };
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

    @Name("eventStatus")
    @AggregatedIndex(cf = "AggregatedIndex", groupBy = "tenant")
    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
        setChanged("eventStatus");
    }

    public static class Method implements Serializable {

        protected String method;
        protected Object[] args;

        public Method(String orchestrationMethod, Object[] args) {
            this.method = orchestrationMethod;
            this.args = args;
        }

        public String getMethod() {
            return this.method;
        }

        public Object[] getArgs() {
            return this.args;
        }

        public void setMethod(String name) {
            this.method = name;
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

}