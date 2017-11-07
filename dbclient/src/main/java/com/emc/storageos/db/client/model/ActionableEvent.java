/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.io.Serializable;
import java.net.URI;
import java.util.Calendar;

@Cf("ActionableEvent")
public class ActionableEvent extends DataObject implements TenantResource {

    private String description;
    private NamedURI resource;
    private String eventStatus;
    private URI tenant;
    private byte[] approveMethod;
    private byte[] declineMethod;
    private String eventCode;
    private String warning;
    private StringSet taskIds;
    private StringSet approveDetails;
    private StringSet declineDetails;
    private StringSet affectedResources;
    private Calendar eventExecutionTime;

    public enum Status {
        pending, approved, declined, failed, system_declined
    }

    @Name("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        setChanged("description");
    }

    @Name("taskIds")
    public StringSet getTaskIds() {
        return this.taskIds;
    }

    public void setTaskIds(StringSet taskIds) {
        this.taskIds = taskIds;
        setChanged("taskIds");
    }

    @Name("warning")
    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
        setChanged("warning");
    }

    @Name("approveMethod")
    public byte[] getApproveMethod() {
        return approveMethod;
    }

    public void setApproveMethod(byte[] approveMethod) {
        this.approveMethod = approveMethod;
        setChanged("approveMethod");
    }

    @Name("declineMethod")
    public byte[] getDeclineMethod() {
        return declineMethod;
    }

    public void setDeclineMethod(byte[] declineMethod) {
        this.declineMethod = declineMethod;
        setChanged("declineMethod");
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

    @NamedRelationIndex(cf = "NamedRelation", types = { Host.class, Initiator.class, Cluster.class, Vcenter.class,
            VcenterDataCenter.class })
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

    @Name("eventCode")
    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
        setChanged("eventCode");
    }

    @Name("approveDetails")
    public StringSet getApproveDetails() {
        return this.approveDetails;
    }

    public void setApproveDetails(StringSet approveDetails) {
        this.approveDetails = approveDetails;
        setChanged("approveDetails");
    }

    @Name("declineDetails")
    public StringSet getDeclineDetails() {
        return this.declineDetails;
    }

    public void setDeclineDetails(StringSet declineDetails) {
        this.declineDetails = declineDetails;
        setChanged("declineDetails");
    }

    @Name("eventExecutionTime")
    public Calendar getEventExecutionTime() {
        return this.eventExecutionTime;
    }

    public void setEventExecutionTime(Calendar eventExecutionTime) {
        this.eventExecutionTime = eventExecutionTime;
        setChanged("eventExecutionTime");
    }

    @Name("affectedResources")
    @AlternateId("AffectedResources")
    public StringSet getAffectedResources() {
        if (affectedResources == null) {
            affectedResources = new StringSet();
        }
        return affectedResources;
    }

    public void setAffectedResources(StringSet affectedResources) {
        this.affectedResources = affectedResources;
        setChanged("affectedResources");
    }

    public static class Method implements Serializable {

        private String methodName;
        private Object[] args;

        public Method(String methodName, Object[] args) {
            this.methodName = methodName;
            this.args = args;
        }

        public String getMethodName() {
            return this.methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public Object[] getArgs() {
            return this.args;
        }

        public void setArgs(Object... args) {
            this.args = args;
        }

        public byte[] serialize() {
            return com.emc.storageos.coordinator.client.service.impl.GenericSerializer.serialize(this, methodName, true);
        }

        public static Method deserialize(byte[] array) {
            return (Method) com.emc.storageos.coordinator.client.service.impl.GenericSerializer.deserialize(array);
        }

    }

}