/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "compute_vpool")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ComputeVirtualPoolRestRep extends DataObjectRestRep {

    private String description;
    private String systemType;
    private List<RelatedResourceRep> matchedComputeElements;
    private List<RelatedResourceRep> availableMatchedComputeElements;
    private Boolean inUse;

    private Integer minProcessors;
    private Integer maxProcessors;
    private Integer minTotalCores;
    private Integer maxTotalCores;
    private Integer minTotalThreads;
    private Integer maxTotalThreads;
    private Integer minCpuSpeed;
    private Integer maxCpuSpeed;
    private Integer minMemory;
    private Integer maxMemory;
    private Integer minNics;
    private Integer maxNics;
    private Integer minHbas;
    private Integer maxHbas;

    private List<RelatedResourceRep> varrays;
    private Boolean useMatchedElements;
    private List<NamedRelatedResourceRep> serviceProfileTemplates;

    public ComputeVirtualPoolRestRep() {
    }

    /**
     * 
     * User defined description for this virtual pool.
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
     * The supported system type for the virtual pool.
     * 
     * @valid Cisco_UCSM
     * @valid Cisco_CSeries
     * @valid Generic
     * 
     * @return The system type.
     */
    @XmlElement(name = "system_type")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    @XmlElementWrapper(name = "matched_compute_elements")
    /**
     * Set of compute elements which have attributes that match the criteria for 
     * selecting the auto-generated list of compute elements.
     * 
     * @valid none
     */
    @XmlElement(name = "compute_element")
    public List<RelatedResourceRep> getMatchedComputeElements() {
        if (matchedComputeElements == null) {
            matchedComputeElements = new ArrayList<RelatedResourceRep>();
        }
        return matchedComputeElements;
    }

    public void setMatchedComputeElements(List<RelatedResourceRep> matchedComputeElements) {
        this.matchedComputeElements = matchedComputeElements;
    }

    @XmlElementWrapper(name = "available_matched_compute_elements")
    /**
     * Set of compute elements which have attributes that match the criteria for 
     * selecting the auto-generated list of compute elements, and are available
     * 
     * @valid none
     */
    @XmlElement(name = "compute_element")
    public List<RelatedResourceRep> getAvailableMatchedComputeElements() {
        if (availableMatchedComputeElements == null) {
            availableMatchedComputeElements = new ArrayList<RelatedResourceRep>();
        }
        return availableMatchedComputeElements;
    }

    public void setAvailableMatchedComputeElements(List<RelatedResourceRep> availableMatchedComputeElements) {
        this.availableMatchedComputeElements = availableMatchedComputeElements;
    }

    /**
     * Minimum Number of processors supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "min_processors")
    public Integer getMinProcessors() {
        return minProcessors;
    }

    public void setMinProcessors(Integer minProcessors) {
        this.minProcessors = minProcessors;
    }

    /**
     * Maximum Number of processors supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "max_processors")
    public Integer getMaxProcessors() {
        return maxProcessors;
    }

    public void setMaxProcessors(Integer maxProcessors) {
        this.maxProcessors = maxProcessors;
    }

    /**
     * Minimum Number of cores supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "min_total_cores")
    public Integer getMinTotalCores() {
        return minTotalCores;
    }

    public void setMinTotalCores(Integer minTotalCores) {
        this.minTotalCores = minTotalCores;
    }

    /**
     * Maximum Number of cores supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "max_total_cores")
    public Integer getMaxTotalCores() {
        return maxTotalCores;
    }

    public void setMaxTotalCores(Integer maxTotalCores) {
        this.maxTotalCores = maxTotalCores;
    }

    /**
     * Minimum Number of threads supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "min_total_threads")
    public Integer getMinTotalThreads() {
        return minTotalThreads;
    }

    public void setMinTotalThreads(Integer minTotalThreads) {
        this.minTotalThreads = minTotalThreads;
    }

    /**
     * Maximum Number of threads supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "max_total_threads")
    public Integer getMaxTotalThreads() {
        return maxTotalThreads;
    }

    public void setMaxTotalThreads(Integer maxTotalThreads) {
        this.maxTotalThreads = maxTotalThreads;
    }

    /**
     * Minimum CPU speed supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "min_cpu_speed")
    public Integer getMinCpuSpeed() {
        return minCpuSpeed;
    }

    public void setMinCpuSpeed(Integer minCpuSpeed) {
        this.minCpuSpeed = minCpuSpeed;
    }

    /**
     * Maximum CPU speed supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "max_cpu_speed")
    public Integer getMaxCpuSpeed() {
        return maxCpuSpeed;
    }

    public void setMaxCpuSpeed(Integer maxCpuSpeed) {
        this.maxCpuSpeed = maxCpuSpeed;
    }

    /**
     * Minimum memory supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "min_memory")
    public Integer getMinMemory() {
        return minMemory;
    }

    public void setMinMemory(Integer minMemory) {
        this.minMemory = minMemory;
    }

    /**
     * Maximum memory supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "max_memory")
    public Integer getMaxMemory() {
        return maxMemory;
    }

    public void setMaxMemory(Integer maxMemory) {
        this.maxMemory = maxMemory;
    }

    /**
     * Minimum number of NICs supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "min_nics")
    public Integer getMinNics() {
        return minNics;
    }

    public void setMinNics(Integer minNics) {
        this.minNics = minNics;
    }

    /**
     * Maximum number of NICs supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "max_nics")
    public Integer getMaxNics() {
        return maxNics;
    }

    public void setMaxNics(Integer maxNics) {
        this.maxNics = maxNics;
    }

    /**
     * Minimum number of HBAs supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "min_hbas")
    public Integer getMinHbas() {
        return minHbas;
    }

    public void setMinHbas(Integer minHbas) {
        this.minHbas = minHbas;
    }

    /**
     * Maximum number of HBAs supported by this virtual pool.
     * 
     * @valid 1-65535
     */
    @XmlElement(name = "max_hbas")
    public Integer getMaxHbas() {
        return maxHbas;
    }

    public void setMaxHbas(Integer maxHbas) {
        this.maxHbas = maxHbas;
    }

    @XmlElementWrapper(name = "varrays")
    /**
     * The virtual arrays assigned to this virtual pool.
     * 
     * @valid none
     */
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

    /**
     * Determines if matched or valid assigned compute elements are returned from
     * command to retrieve the list of compute elements.
     * 
     * @valid false
     * @valid true
     */
    @XmlElement(name = "use_matched_elements")
    public Boolean getUseMatchedElements() {
        return useMatchedElements;
    }

    public void setUseMatchedElements(Boolean useMatchedElements) {
        this.useMatchedElements = useMatchedElements;
    }

    /**
     * The service profile templates assigned to this virtual pool.
     * 
     * @valid none
     */
    @XmlElementWrapper(name = "service_profile_templates")
    @XmlElement(name = "service_profile_template")
    public List<NamedRelatedResourceRep> getServiceProfileTemplates() {
        if (serviceProfileTemplates == null) {
            return serviceProfileTemplates = new ArrayList<NamedRelatedResourceRep>();
        }
        return serviceProfileTemplates;
    }

    public void setServiceProfileTemplates(List<NamedRelatedResourceRep> serviceProfileTemplates) {
        this.serviceProfileTemplates = serviceProfileTemplates;
    }

    /**
     * Is the compute virtual pool in use
     * 
     * @valid false
     * @valid true
     */
    @XmlElement(name = "in_use")
    public Boolean getInUse() {
        return inUse;
    }

    public void setInUse(Boolean inUse) {
        this.inUse = inUse;
    }

}
