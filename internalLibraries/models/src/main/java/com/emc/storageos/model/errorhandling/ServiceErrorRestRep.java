/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model.errorhandling;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.annotate.JsonProperty;

@XmlRootElement(name = "error")
public class ServiceErrorRestRep {

    private int code;
    private String codeDescription;
    private String detailedMessage;
    private boolean retryable;

    /**
     * The numerical code associated with the error encountered when processing a service request
     * @valid none
     */
    @XmlElement(required = true, name = "code")
    public int getCode() {
        return code;
    }

    public void setCode(final int code) {
        this.code = code;
    }

    /**
     * The description of the error
     * @valid none
     */
    @XmlElement(required = true, name = "description")
    @JsonProperty("description")
    public String getCodeDescription() {
        return codeDescription;
    }

    public void setCodeDescription(final String codeDescription) {
        this.codeDescription = codeDescription;
    }

    /**
     * Detailed information concerning the error
     * @valid none
     */
    @XmlElement(required = true, name = "details")
    @JsonProperty("details")
    public String getDetailedMessage() {
        return detailedMessage;
    }

    public void setDetailedMessage(final String detailedMessage) {
        this.detailedMessage = detailedMessage;
    }

    /**
     * Indicates whether the error is retryable which 
     * means service is temporarily unavailable and 
     * the client could retry after a while.
     * @valid true = it is retryable.
     * @valid false = it is not retryable.
     */
    @XmlElement(required = true, name = "retryable")
    public boolean isRetryable() {
        return retryable;
    }

    public void setRetryable(final boolean retryable) {
        this.retryable = retryable;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("Service Code: ");
        buffer.append(this.code);
        buffer.append(", Description: ");
        buffer.append(this.codeDescription);
        buffer.append(", Details: ");
        buffer.append(this.detailedMessage);
        return buffer.toString();
    }
}
