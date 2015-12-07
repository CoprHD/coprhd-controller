package com.emc.storageos.model.vasa;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="bulk_Capability_Profile")
public class CapabilityProfileBulkResponse {

    List<CapabilityProfileCreateResponse> capabilityProfiles;

    @XmlElement(name="capability_profile")
    public List<CapabilityProfileCreateResponse> getCapabilityProfiles() {
        if(capabilityProfiles == null){
            capabilityProfiles = new ArrayList<CapabilityProfileCreateResponse>();
        }
        return capabilityProfiles;
    }

    public void setCapabilityProfiles(List<CapabilityProfileCreateResponse> capabilityProfiles) {
        this.capabilityProfiles = capabilityProfiles;
    }
}
