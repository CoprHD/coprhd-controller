package com.emc.storageos.model.vasa;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;

@XmlRootElement(name="bulk_Capability_Profile")
public class CapabilityProfileBulkResponse {

    List<BlockVirtualPoolRestRep> capabilityProfiles;

    @XmlElement(name="block_vpool")
    public List<BlockVirtualPoolRestRep> getCapabilityProfiles() {
        if(null == capabilityProfiles){
            capabilityProfiles = new ArrayList<BlockVirtualPoolRestRep>();
        }
        return capabilityProfiles;
    }

    public void setCapabilityProfiles(List<BlockVirtualPoolRestRep> capabilityProfiles) {
        this.capabilityProfiles = capabilityProfiles;
    }

    
}
