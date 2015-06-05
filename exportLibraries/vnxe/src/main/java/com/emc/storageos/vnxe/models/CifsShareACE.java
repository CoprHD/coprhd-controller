/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.annotate.JsonDeserialize;
import org.codehaus.jackson.map.annotate.JsonSerialize;
@JsonIgnoreProperties(ignoreUnknown=true)
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class CifsShareACE {
    //Domain user or group Security Identifier (SID).
    private String sid;
    private int accessType;
    private int accessLevel;
    
    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public int getAccessType() {
        return accessType;
    }

    public void setAccessType(int accessType) {
        this.accessType = accessType;
    }

    public int getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(int accessLevel) {
        this.accessLevel = accessLevel;
    }

    @JsonDeserialize(using = ACEAccessLevelEnumDeserializer.class)
    public static enum ACEAccessLevelEnum {
        READ(1),
        WRITE(2),
        FULL(4);
        
        private int value;

        private ACEAccessLevelEnum(int value) {
            this.value = value;
        }
        @org.codehaus.jackson.annotate.JsonValue
        public int getValue() {
            return this.value;
        }
    }
    
    public static enum ACEAccessTypeEnum {
        DENY,
        GRANT,
        NONE;
    }
}
