/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Feature extends VNXeBase {
    private int state;

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }
    
    public static enum FeatureStateEnum {
        FeatureStateDisabled(1),
        FeatureStateEnabled(2),
        FeatureStateHidden(3);

        private int value;

        private FeatureStateEnum(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }

    }

}
