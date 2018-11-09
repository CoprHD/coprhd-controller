/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

import java.io.Serializable;
import java.net.URI;

public class VolumeURIHLU extends StorageGroupPolicyLimitsParam implements Serializable {
    private URI _volumeURI = null;
    private String _hlu = null;
    private String _label = null;

    public VolumeURIHLU(URI volURI, String hlu, String autoTierPolicyName, String label) {
        setAutoTierPolicyName(autoTierPolicyName);
        _volumeURI = volURI;
        _hlu = hlu;
        _label = label;
    }

    public VolumeURIHLU(URI volURI, String hlu, String autoTierPolicyName, String label,
            Integer hostIOLimitBandwidth, Integer hostIOLimitIOPs) {
        this(volURI, hlu, autoTierPolicyName, label);
        setHostIOLimitBandwidth(hostIOLimitBandwidth);
        setHostIOLimitIOPs(hostIOLimitIOPs);
    }

    public URI getVolumeURI() {
        return _volumeURI;
    }

    public String getHLU() {
        return _hlu;
    }

    public void setHLU(String hlu) {
        this._hlu = hlu;
    }

    public String toString() {
        String label = (_label != null) ? _label : "No Label";
        String hlu = (_hlu != null) ? _hlu : "Unassigned";
        String policyName = super.toString();
        return String.format("%s -> HLU:%s (Policy:%s)", label, hlu, policyName);
    }
}
