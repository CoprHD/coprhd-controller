/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
// Copyright 2012 by EMC Corporation ("EMC").
//
// UNPUBLISHED  CONFIDENTIAL  AND  PROPRIETARY  PROPERTY OF EMC. The copyright
// notice above does not evidence any actual  or  intended publication of this
// software. Disclosure and dissemination are pursuant to separate agreements.
// Unauthorized use, distribution or dissemination are strictly prohibited.

package com.emc.storageos.cimadapter.connections.celerra;

import com.emc.storageos.cimadapter.connections.cim.CimConstants;

/**
 * Spring bean for a Celerra message specification\.
 */
public class CelerraMessageSpec {

    // The message identifier.
    private String _id = "";

    // The message component.
    private String _component = CimConstants.UNKNOWN_KEY;

    // The message facility.
    private String _facility = CimConstants.UNKNOWN_KEY;

    // The event identifier.
    private String _eventID = CimConstants.UNKNOWN_KEY;

    // The severity.
    private String _severity = CimConstants.UNKNOWN_KEY;

    // The message pattern.
    private String _pattern = "";

    /**
     * Getter for the message identifier.
     * 
     * @return The message identifier.
     */
    public String getID() {
        return _id;
    }

    /**
     * Setter for the message identifier.
     * 
     * @param value The message identifier.
     */
    public void setID(String value) {
        _id = value;
    }

    /**
     * Getter for the message component.
     * 
     * @return The message component.
     */
    public String getComponent() {
        return _component;
    }

    /**
     * Setter for the message component.
     * 
     * @param value The message component.
     */
    public void setComponent(String value) {
        _component = value;
    }

    /**
     * Getter for the message facility.
     * 
     * @return The message facility.
     */
    public String getFacility() {
        return _facility;
    }

    /**
     * Setter for the message facility.
     * 
     * @param value The message facility.
     */
    public void setFacility(String value) {
        _facility = value;
    }

    /**
     * Getter for the message event identifier.
     * 
     * @return The message event identifier.
     */
    public String getEventID() {
        return _eventID;
    }

    /**
     * Setter for the message event identifier.
     * 
     * @param value The message event identifier.
     */
    public void setEventID(String value) {
        _eventID = value;
    }

    /**
     * Getter for the message severity.
     * 
     * @return The message severity.
     */
    public String getSeverity() {
        return _severity;
    }

    /**
     * Setter for the message severity.
     * 
     * @param value The message severity.
     */
    public void setSeverity(String value) {
        _severity = value;
    }

    /**
     * Getter for the message pattern.
     * 
     * @return The message pattern.
     */
    public String getPattern() {
        return _pattern;
    }

    /**
     * Setter for the message pattern.
     * 
     * @param value The message pattern.
     */
    public void setPattern(String value) {
        _pattern = value;
    }
}