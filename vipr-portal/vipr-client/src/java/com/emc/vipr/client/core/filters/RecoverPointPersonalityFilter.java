/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.VolumeRestRep;

/**
 * Filter for listing volumes with a particular RecoverPoint personality (source/target/metadata).
 */
public class RecoverPointPersonalityFilter extends DefaultResourceFilter<VolumeRestRep> {
    public static RecoverPointPersonalityFilter SOURCE = new RecoverPointPersonalityFilter("SOURCE");
    public static RecoverPointPersonalityFilter TARGET = new RecoverPointPersonalityFilter("TARGET");
    public static RecoverPointPersonalityFilter METADATA = new RecoverPointPersonalityFilter("METADATA");

    private final String personality;

    public RecoverPointPersonalityFilter(String personality) {
        this.personality = personality;
    }

    @Override
    public boolean accept(VolumeRestRep item) {
        if ((item.getProtection() != null) && (item.getProtection().getRpRep() != null)) {
            return isPersonalityMatch(item.getProtection().getRpRep().getPersonality());
        }
        else {
            return false;
        }
    }

    protected boolean isPersonalityMatch(String personality) {
        return this.personality.equals(personality);
    }
}
