/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.model.valid.EnumType;

/**
 * An interface on a {@link Host} to which volumes and file system can be
 * exported. The interface can an IP, iSCSI or FC interface..
 * 
 * @author elalih
 * 
 */
public abstract class HostInterface extends DataObject {
    private String _protocol;
    private URI _host;
    private String _registrationStatus = RegistrationStatus.REGISTERED.toString();

    // to do - This is temporary until initiator IpInterface service are remove
    private Boolean _isManualCreation;

    public HostInterface() {
    }

    /**
     * Constructor that initializes all the required data.
     * 
     * @param host the host URI
     * @param protocol the interface protocol
     * 
     */
    public HostInterface(URI host, String protocol) {
        setHost(host);
        setProtocol(protocol);
    }

    /**
     * The communication protocol of the interface which is {@link Protocol#IPV4} or {@link Protocol#IPV6}.
     * 
     * @return communication protocol of the interface
     */
    @Name("protocol")
    public String getProtocol() {
        return _protocol;
    }

    /**
     * Sets the communication protocol of the interface
     * 
     * @param protocol the interface protocol
     */
    public void setProtocol(String protocol) {
        _protocol = protocol;
        setChanged("protocol");
    }

    /**
     * The host of the interface.
     * 
     * @return the parent host URI for the interface
     */
    @RelationIndex(cf = "RelationIndex", type = Host.class)
    @Name("host")
    public URI getHost() {
        return _host;
    }

    /**
     * Sets the parent host of the interface
     * 
     * @param host the parent host URI
     */
    public void setHost(URI host) {
        _host = host;
        setChanged("host");
    }

    @EnumType(RegistrationStatus.class)
    @Name("registrationStatus")
    public String getRegistrationStatus() {
        return _registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        _registrationStatus = registrationStatus;
        setChanged("registrationStatus");
    }

    /**
     * Getter for manual creation flag.
     * to do - This is temporary until initiator and IpInterface service are remove
     * 
     * @return true for manual creation, false otherwise.
     */
    @Name("isManualCreation")
    public Boolean getIsManualCreation() {
        return _isManualCreation;
    }

    /**
     * Setter for manual creation flag.
     * to do - This is temporary until initiator and IpInterface service are remove
     * 
     * @param isManualCreation true for manual creation, false otherwise.
     */
    public void setIsManualCreation(Boolean isManualCreation) {
        _isManualCreation = isManualCreation;
        setChanged("isManualCreation");
    }

    /**
     * The supported protocols for exporting volumes and file systems to a host
     * 
     * @author elalih
     * 
     */
    public enum Protocol {
        FC,
        iSCSI,
        IPV4,
        IPV6,
        ScaleIO,
        RBD,
    }

    /**
     * Returns the list of parameters used in audit logs for this interface.
     * 
     * @return the list of parameters used in audit logs for this interface.
     */
    public abstract Object[] auditParameters();

}
