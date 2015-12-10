package com.emc.storageos.model.vasa;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.StringHashMapEntry;

@XmlRootElement(name="vvol_request_param")
public class VVolRequestParam {
    
    private String Name;
    private String protocolEndpoint;
    private String vVolSecondaryId;
    private String description;
    private List<StringHashMapEntry> extensions;
    public String getProtocolEndpoint() {
        return protocolEndpoint;
    }
    public void setProtocolEndpoint(String protocolEndpoint) {
        this.protocolEndpoint = protocolEndpoint;
    }
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
    @XmlElementWrapper(name = "extensions")
    @XmlElement(name="extensions")
    public List<StringHashMapEntry> getExtensions() {
        if(null == extensions){
            extensions = new ArrayList<StringHashMapEntry>();
        }
        return extensions;
    }
    public void setExtensions(List<StringHashMapEntry> extensions) {
        this.extensions = extensions;
    }
    public String getName() {
        return Name;
    }
    public void setName(String name) {
        Name = name;
    }

    
}
