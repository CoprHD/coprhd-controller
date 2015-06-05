/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.geomodel.request;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * request body for intervdc get tokens when key ids are being requested
 */
@XmlRootElement(name="token_keys_request")
public class TokenKeysRequest {
    
    private String firstKeyId;
    private String secondKeyId;
    private String requestingVDC;
    
    @XmlElement(name = "requesting_vdc")
    public String getRequestingVDC() {
        return this.requestingVDC;
    }
    
    public void setRequestingVDC(String requestingVDC) {
        this.requestingVDC = requestingVDC;
    }
    
    @XmlElement(name = "first_key_id")
    public String getFirstKeyId() {
        return this.firstKeyId;
    }
    
    public void setFirstKeyId(String fstKeyId) {
        this.firstKeyId = fstKeyId;
    }
    
    @XmlElement(name="second_key_id")
    public String getSecondKeyId() {
        return this.secondKeyId;
    }
    
    public void setSecondKeyId(String scndKey) {
        this.secondKeyId = scndKey;
    }
 
}
