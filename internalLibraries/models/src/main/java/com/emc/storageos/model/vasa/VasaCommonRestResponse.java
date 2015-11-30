package com.emc.storageos.model.vasa;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

public class VasaCommonRestResponse extends DataObjectRestRep{
     
    private String type;
    private String description;
    private Set<String> protocols;
    private String provisioningType;
    private Integer numResources;
    private String systemType;
    private List<RelatedResourceRep> varrays;
//    private List<RelatedResourceRep> assignedStoragePools;
//    private List<RelatedResourceRep> matchedStoragePools;


    @XmlElement
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * 
     * User defined description
     * 
     * @valid none
     */
    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Storage type provisioned
     * 
     * @valid NONE
     * @valid Thick
     * @valid Thin
     */
    @XmlElement(name = "provisioning_type")
    public String getProvisioningType() {
        return provisioningType;
    }

    public void setProvisioningType(String provisioningType) {
        this.provisioningType = provisioningType;
    }

    @XmlElementWrapper(name = "protocols")
    /**
     * The set of supported protocols
     * 
     * @valid FC = Fibre Channel (block)
     * @valid ISCSI =  Internet Small Computer System Interface (block)
     * @valid FCoE = Fibre Channel over Ethernet (block)
     * @valid NFS = Network File System (file)
     * @valid NFSv4 = Network File System Version 4 (file)
     * @valid CIFS = Common Internet File System (file)
     */
    @XmlElement(name = "protocol")
    public Set<String> getProtocols() {
        if (protocols == null) {
            protocols = new LinkedHashSet<String>();
        }
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }

    /**
     * The supported system type
     * 
     * @valid NONE
     * @valid vnxblock (Block)
     * @valid vmax (Block)
     * @valid vnxfile (File)
     * @valid isilon (File)
     * @valid netapp (File)
     */
    @XmlElement(name = "system_type")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    /**
     * Number of resources provisioned to this ViPR using this
     * virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "num_resources")
    public Integer getNumResources() {
        return numResources;
    }

    public void setNumResources(Integer numResources) {
        this.numResources = numResources;
    }

    @XmlElementWrapper(name = "varrays")
    @XmlElement(name = "varray")
    @JsonProperty("varrays")
    public List<RelatedResourceRep> getVirtualArrays() {
        if (varrays == null) {
            return varrays = new ArrayList<RelatedResourceRep>();
        }
        return varrays;
    }

    public void setVirtualArrays(List<RelatedResourceRep> varrays) {
        this.varrays = varrays;
    }

}
