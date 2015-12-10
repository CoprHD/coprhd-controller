package com.emc.storageos.model.vasa;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.StringHashMapEntry;

@XmlRootElement(name="vvol_response_param")
public class VVolResponseParam extends VasaCommonRestResponse {
    
    private String name;
    private RelatedResourceRep protocolEndpoint;
    private String vVolSecondaryId;
    private String description;
    private List<StringHashMapEntry> extensions;
    

    public String getvVolSecondaryId() {
        return vVolSecondaryId;
    }
    public void setvVolSecondaryId(String vVolSecondaryId) {
        this.vVolSecondaryId = vVolSecondaryId;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public List<StringHashMapEntry> getExtensions() {
        return extensions;
    }
    public void setExtensions(List<StringHashMapEntry> extensions) {
        this.extensions = extensions;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public RelatedResourceRep getProtocolEndpoint() {
        return protocolEndpoint;
    }
    public void setProtocolEndpoint(RelatedResourceRep protocolEndpoint) {
        this.protocolEndpoint = protocolEndpoint;
    }
    
    

}
