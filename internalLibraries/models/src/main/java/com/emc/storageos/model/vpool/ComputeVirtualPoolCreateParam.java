/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;
import com.emc.storageos.model.valid.Range;

@XmlRootElement(name = "compute_vpool_create")
public class ComputeVirtualPoolCreateParam {

    private String name;
    private String description;
    private String systemType;
    private Set<String> varrays;
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
    private Boolean useMatchedElements = true;
    private Set<String> serviceProfileTemplates;

    public ComputeVirtualPoolCreateParam() {
    }

    /**
     * The name for the virtual pool.
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
     * The description for the virtual pool.
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

    /**
     * Supported System Types
     * 
     * @valid Cisco_UCSM
     */
    @XmlElement(name = "system_type")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    @XmlElement(name = "min_processors")
    @Range(min = 1, max = 65535)
    public Integer getMinProcessors() {
        return minProcessors;
    }

    public void setMinProcessors(Integer minProcessors) {
        this.minProcessors = minProcessors;
    }

    @XmlElement(name = "max_processors")
    @Range(min = 1, max = 65535)
    public Integer getMaxProcessors() {
        return maxProcessors;
    }

    public void setMaxProcessors(Integer maxProcessors) {
        this.maxProcessors = maxProcessors;
    }

    @XmlElement(name = "min_total_cores")
    @Range(min = 1, max = 65535)
    public Integer getMinTotalCores() {
        return minTotalCores;
    }

    public void setMinTotalCores(Integer minTotalCores) {
        this.minTotalCores = minTotalCores;
    }

    @XmlElement(name = "max_total_cores")
    @Range(min = 1, max = 65535)
    public Integer getMaxTotalCores() {
        return maxTotalCores;
    }

    public void setMaxTotalCores(Integer maxTotalCores) {
        this.maxTotalCores = maxTotalCores;
    }

    @XmlElement(name = "min_total_threads")
    @Range(min = 1, max = 65535)
    public Integer getMinTotalThreads() {
        return minTotalThreads;
    }

    public void setMinTotalThreads(Integer minTotalThreads) {
        this.minTotalThreads = minTotalThreads;
    }

    @XmlElement(name = "max_total_threads")
    @Range(min = 1, max = 65535)
    public Integer getMaxTotalThreads() {
        return maxTotalThreads;
    }

    public void setMaxTotalThreads(Integer maxTotalThreads) {
        this.maxTotalThreads = maxTotalThreads;
    }

    @XmlElement(name = "min_cpu_speed")
    @Range(min = 1, max = 65535)
    public Integer getMinCpuSpeed() {
        return minCpuSpeed;
    }

    public void setMinCpuSpeed(Integer minCpuSpeed) {
        this.minCpuSpeed = minCpuSpeed;
    }

    @XmlElement(name = "max_cpu_speed")
    @Range(min = 1, max = 65535)
    public Integer getMaxCpuSpeed() {
        return maxCpuSpeed;
    }

    public void setMaxCpuSpeed(Integer maxCpuSpeed) {
        this.maxCpuSpeed = maxCpuSpeed;
    }

    @XmlElement(name = "min_memory")
    @Range(min = 1, max = 65535)
    public Integer getMinMemory() {
        return minMemory;
    }

    public void setMinMemory(Integer minMemory) {
        this.minMemory = minMemory;
    }

    @XmlElement(name = "max_memory")
    @Range(min = 1, max = 65535)
    public Integer getMaxMemory() {
        return maxMemory;
    }

    public void setMaxMemory(Integer maxMemory) {
        this.maxMemory = maxMemory;
    }

    @XmlElement(name = "min_nics")
    @Range(min = 1, max = 65535)
    public Integer getMinNics() {
        return minNics;
    }

    public void setMinNics(Integer minNics) {
        this.minNics = minNics;
    }

    @XmlElement(name = "max_nics")
    @Range(min = 1, max = 65535)
    public Integer getMaxNics() {
        return maxNics;
    }

    public void setMaxNics(Integer maxNics) {
        this.maxNics = maxNics;
    }

    @XmlElement(name = "min_hbas")
    @Range(min = 1, max = 65535)
    public Integer getMinHbas() {
        return minHbas;
    }

    public void setMinHbas(Integer minHbas) {
        this.minHbas = minHbas;
    }

    @XmlElement(name = "max_hbas")
    @Range(min = 1, max = 65535)
    public Integer getMaxHbas() {
        return maxHbas;
    }

    public void setMaxHbas(Integer maxHbas) {
        this.maxHbas = maxHbas;
    }

    @XmlElementWrapper(name = "varrays")
    /**
     * The virtual arrays for the virtual pool
     * 
     * @valid none
     */
    @XmlElement(name = "varray")
    public Set<String> getVarrays() {
        if (varrays == null) {
            varrays = new HashSet<String>();
        }
        return varrays;
    }

    public void setVarrays(Set<String> varrays) {
        this.varrays = varrays;
    }

    /**
     * Determines if matched or valid assigned Compute Elements are returned
     * from command to retrieve the list of Compute Elements.
     * 
     * @valid true
     * @valid false
     */
    @XmlElement(name = "use_matched_elements")
    public Boolean getUseMatchedElements() {
        return useMatchedElements;
    }

    public void setUseMatchedElements(Boolean useMatchedElements) {
        this.useMatchedElements = useMatchedElements;
    }

    @XmlElementWrapper(name = "service_profile_templates")
    /**
     * The service Profile templates for the virtual pool
     * 
     * @valid none
     */
    @XmlElement(name = "service_profile_template")
    public Set<String> getServiceProfileTemplates() {
        if (serviceProfileTemplates == null) {
            serviceProfileTemplates = new HashSet<String>();
        }
        return serviceProfileTemplates;
    }

    public void setServiceProfileTemplates(Set<String> serviceProfileTemplates) {
        this.serviceProfileTemplates = serviceProfileTemplates;
    }
}