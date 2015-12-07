package com.emc.storageos.model.vasa;

import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Length;

public class VasaCommonRestRequest {

    private String name;
    private String description;
    private Set<String> protocols;
    private String protocolType;
    private String provisionType;
    private Set<String> varrays;

    
    /**
     * The name for the Storage Container.
     * 
     * @valid none
     */
    @XmlElement(required = false)
    @Length(min = 2, max = 128)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The description for the Storage Container.
     * 
     * @valid none
     */
    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElementWrapper(name = "protocols")
    @XmlElement(name = "protocol", required = true)
    public Set<String> getProtocols() {
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }

    @XmlElementWrapper(name = "varrays")
    /**
     * The virtual arrays for the Storage Container
     * 
     * @valid none
     */
    @XmlElement(name = "varray")
    @JsonProperty("varray")
    public Set<String> getVarrays() {
        return varrays;
    }

    public void setVarrays(Set<String> varrays) {
        this.varrays = varrays;
    }

    /**
     * The provisioning type for the Storage Container
     * 
     * @valid NONE
     * @valid Thin
     * @valid Thick
     */
    @XmlElement(name = "provisioning_type", required = true)
    public String getProvisionType() {
        return provisionType;
    }

    public void setProvisionType(String provisionType) {
        this.provisionType = provisionType;
    }
    
    public String getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
    }

    
}
