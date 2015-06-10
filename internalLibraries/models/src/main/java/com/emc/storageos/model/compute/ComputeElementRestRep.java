/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.compute;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DiscoveredSystemObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "compute_element")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ComputeElementRestRep extends DiscoveredSystemObjectRestRep {

	private Long ram;
	private RelatedResourceRep computeSystem;
	private Integer numOfCores;
	private Short numOfProcessors;
	private Integer numOfThreads;
	private String processorSpeed;
	private String uuid;
	private String originalUuid;
	private Boolean available;
	private String model;

	public ComputeElementRestRep() {
	}

	@XmlElement(name = "ram")
	public Long getRam() {
		return ram;
	}

	public void setRam(Long ram) {
		this.ram = ram;
	}

	@XmlElement(name = "num_of_cores")
	public Integer getNumOfCores() {
		return numOfCores;
	}

	public void setNumOfCores(Integer numOfCores) {
		this.numOfCores = numOfCores;
	}

	@XmlElement(name = "uuid")
	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	
	@XmlElement(name = "number_of_processors")
	public Short getNumOfProcessors() {
		return numOfProcessors;
	}

	public void setNumOfProcessors(Short numOfProcessors) {
		this.numOfProcessors = numOfProcessors;
	}

	@XmlElement(name = "number_of_threads")
	public Integer getNumOfThreads() {
		return numOfThreads;
	}

	public void setNumOfThreads(Integer numOfThreads) {
		this.numOfThreads = numOfThreads;
	}

	@XmlElement(name = "processor_speed")
	public String getProcessorSpeed() {
		return processorSpeed;
	}

	public void setProcessorSpeed(String processorSpeed) {
		this.processorSpeed = processorSpeed;
	}

	@XmlElement
	public RelatedResourceRep getComputeSystem() {
		return computeSystem;
	}

	public void setComputeSystem(RelatedResourceRep computeSystem) {
		this.computeSystem = computeSystem;
	}
	
	@XmlElement
	public String getOriginalUuid() {
		return originalUuid;
	}

	public void setOriginalUuid(String originalUuid) {
		this.originalUuid = originalUuid;
	}
	
	@XmlElement
	public Boolean getAvailable() {
		return available;
	}

	public void setAvailable(Boolean available) {
		this.available = available;
	}
	
	@XmlElement
	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}
	
}