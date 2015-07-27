/*
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

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class Health {
    private int value;
    private List<String> descriptionIds;
    private List<String> descriptions;
    private List<String> resolutionIds;
    private List<String> resolutions;
    public int getValue() {
        return value;
    }
    public void setValue(int value) {
        this.value = value;
    }
    public List<String> getDescriptionIds() {
        return descriptionIds;
    }
    public void setDescriptionIds(List<String> descriptionIds) {
        this.descriptionIds = descriptionIds;
    }
    public List<String> getDescriptions() {
        return descriptions;
    }
    public void setDescriptions(List<String> descriptions) {
        this.descriptions = descriptions;
    }
    public List<String> getResolutionIds() {
        return resolutionIds;
    }
    public void setResolutionIds(List<String> resolutionIds) {
        this.resolutionIds = resolutionIds;
    }
    public List<String> getResolutions() {
        return resolutions;
    }
    public void setResolutions(List<String> resolutions) {
        this.resolutions = resolutions;
    }
    
    public static enum HealthEnum {
        UNKNOWN(0),
        OK(5),
        OK_BUT(7),
        DEGRADED(10),
        MINOR(15),
        MAJOR(20),
        CRITICAL(25),
        NON_RECOVERABLE(30);
        
        private int value;

        private HealthEnum(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
        
    }

}
