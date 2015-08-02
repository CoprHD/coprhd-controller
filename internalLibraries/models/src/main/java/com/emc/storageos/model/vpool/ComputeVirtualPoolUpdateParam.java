/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import com.emc.storageos.model.valid.Length;
import com.emc.storageos.model.valid.Range;

@XmlRootElement(name = "compute_vpool_update")
public class ComputeVirtualPoolUpdateParam {

    private String name;
    private String description;
    private String systemType;
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
    private Boolean useMatchedElements;
    private VirtualArrayAssignmentChanges varrayChanges;
    private ServiceProfileTemplateAssignmentChanges sptChanges;

    public ComputeVirtualPoolUpdateParam() {
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
     * @valid Cisco_CSeries
     * @valid Generic
     */
    @XmlElement(name = "system_type")
    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    @XmlElement(name = "min_processors")
    @Range(min = 0, max = 65535)
    public Integer getMinProcessors() {
        return minProcessors;
    }

    public void setMinProcessors(Integer minProcessors) {
        this.minProcessors = minProcessors;
    }

    @XmlElement(name = "max_processors")
    @Range(min = 0, max = 65535)
    public Integer getMaxProcessors() {
        return maxProcessors;
    }

    public void setMaxProcessors(Integer maxProcessors) {
        this.maxProcessors = maxProcessors;
    }

    @XmlElement(name = "min_total_cores")
    @Range(min = 0, max = 65535)
    public Integer getMinTotalCores() {
        return minTotalCores;
    }

    public void setMinTotalCores(Integer minTotalCores) {
        this.minTotalCores = minTotalCores;
    }

    @XmlElement(name = "max_total_cores")
    @Range(min = 0, max = 65535)
    public Integer getMaxTotalCores() {
        return maxTotalCores;
    }

    public void setMaxTotalCores(Integer maxTotalCores) {
        this.maxTotalCores = maxTotalCores;
    }

    @XmlElement(name = "min_total_threads")
    @Range(min = 0, max = 65535)
    public Integer getMinTotalThreads() {
        return minTotalThreads;
    }

    public void setMinTotalThreads(Integer minTotalThreads) {
        this.minTotalThreads = minTotalThreads;
    }

    @XmlElement(name = "max_total_threads")
    @Range(min = 0, max = 65535)
    public Integer getMaxTotalThreads() {
        return maxTotalThreads;
    }

    public void setMaxTotalThreads(Integer maxTotalThreads) {
        this.maxTotalThreads = maxTotalThreads;
    }

    @XmlElement(name = "min_cpu_speed")
    @Range(min = 0, max = 65535)
    public Integer getMinCpuSpeed() {
        return minCpuSpeed;
    }

    public void setMinCpuSpeed(Integer minCpuSpeed) {
        this.minCpuSpeed = minCpuSpeed;
    }

    @XmlElement(name = "max_cpu_speed")
    @Range(min = 0, max = 65535)
    public Integer getMaxCpuSpeed() {
        return maxCpuSpeed;
    }

    public void setMaxCpuSpeed(Integer maxCpuSpeed) {
        this.maxCpuSpeed = maxCpuSpeed;
    }

    @XmlElement(name = "min_memory")
    @Range(min = 0, max = 65535)
    public Integer getMinMemory() {
        return minMemory;
    }

    public void setMinMemory(Integer minMemory) {
        this.minMemory = minMemory;
    }

    @XmlElement(name = "max_memory")
    @Range(min = 0, max = 65535)
    public Integer getMaxMemory() {
        return maxMemory;
    }

    public void setMaxMemory(Integer maxMemory) {
        this.maxMemory = maxMemory;
    }

    @XmlElement(name = "min_nics")
    @Range(min = 0, max = 65535)
    public Integer getMinNics() {
        return minNics;
    }

    public void setMinNics(Integer minNics) {
        this.minNics = minNics;
    }

    @XmlElement(name = "max_nics")
    @Range(min = 0, max = 65535)
    public Integer getMaxNics() {
        return maxNics;
    }

    public void setMaxNics(Integer maxNics) {
        this.maxNics = maxNics;
    }

    @XmlElement(name = "min_hbas")
    @Range(min = 0, max = 65535)
    public Integer getMinHbas() {
        return minHbas;
    }

    public void setMinHbas(Integer minHbas) {
        this.minHbas = minHbas;
    }

    @XmlElement(name = "max_hbas")
    @Range(min = 0, max = 65535)
    public Integer getMaxHbas() {
        return maxHbas;
    }

    public void setMaxHbas(Integer maxHbas) {
        this.maxHbas = maxHbas;
    }

    /**
     * Determines if matched or valid assigned compute elements are returned from
     * command to retrieve the list of compute elements.
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

    /**
     * The virtual array assignment changes for the virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "varray_changes")
    public VirtualArrayAssignmentChanges getVarrayChanges() {
        return varrayChanges;
    }

    public void setVarrayChanges(VirtualArrayAssignmentChanges varrayChanges) {
        this.varrayChanges = varrayChanges;
    }

    /**
     * The viService Profile Template assignment changes for the virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "service_profile_template_changes")
    public ServiceProfileTemplateAssignmentChanges getSptChanges() {
        return sptChanges;
    }

    public void setSptChanges(ServiceProfileTemplateAssignmentChanges sptChanges) {
        this.sptChanges = sptChanges;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Name:" + this.getName() + ", Description:" + this.getDescription() + "\n");
        buf.append("UseMatchedElements:" + this.getUseMatchedElements() + "  SystemType:" + this.getSystemType() + "\n");
        buf.append("Qualifiers:\n");
        buf.append("Processors -  Min:" + this.getMinProcessors() + "  Max:" + this.getMaxProcessors() + "\n");
        buf.append("Cores - Min:" + this.getMinTotalCores() + " Max:" + this.getMaxTotalCores() + "\n");
        buf.append("Threads - Min:" + this.getMinTotalThreads() + "   Max:" + this.getMaxTotalThreads() + "\n");
        buf.append("Cpu Speed - Min:" + this.getMinCpuSpeed() + "    Max:" + this.getMaxCpuSpeed() + "\n");
        buf.append("Memory - Min:" + this.getMinMemory() + "     Max:" + this.getMaxCpuSpeed() + "\n");
        buf.append("Nics - Min:" + this.getMinNics() + "   Max:" + this.getMaxNics() + "\n");
        buf.append("Hbas - Min:" + this.getMinHbas() + "   Max:" + this.getMaxHbas() + "\n");
        return buf.toString();
    }
}
