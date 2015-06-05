/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/* 
Copyright (c) 2012 EMC Corporation
All Rights Reserved

This software contains the intellectual property of EMC Corporation
or is licensed to EMC Corporation from third parties.  Use of this
software and the intellectual property contained therein is expressly
imited to the terms and conditions of the License Agreement under which
it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vasa.data.internal;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storage_pool")
public class StoragePool {
	@XmlElement
	private String id;
	@XmlElement
	private String name;
	@XmlElement
	private boolean inactive;

	@XmlElement(name = "operational_status")
	private String operationStatus;

	@XmlElement(name = "percent_subscribed")
	private Integer percentSubscribed;

	@XmlElement(name = "percent_used")
	private Integer percentUsed;

	@XmlElement(name = "subscribed_gb")
	private Integer subscribedCapaityInGB;

	@XmlElement(name = "usable_gb")
	private Integer usableCapaityInGB;

	@XmlElement(name = "used_gb")
	private Integer usedCapaityInGB;

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public boolean isInactive() {
		return inactive;
	}

	public String getOperationStatus() {
		return operationStatus;
	}

	public Integer getPercentSubscribed() {
		return percentSubscribed;
	}

	public Integer getPercentUsed() {
		return percentUsed;
	}

	public Integer getSubscribedCapaityInGB() {
		return subscribedCapaityInGB;
	}

	public Integer getUsableCapaityInGB() {
		return usableCapaityInGB;
	}

	public Integer getUsedCapaityInGB() {
		return usedCapaityInGB;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("StoragePool [id=");
		builder.append(id);
		builder.append(", name=");
		builder.append(name);
		builder.append(", inactive=");
		builder.append(inactive);
		builder.append(", operationStatus=");
		builder.append(operationStatus);
		builder.append(", percentSubscribed=");
		builder.append(percentSubscribed);
		builder.append(", percentUsed=");
		builder.append(percentUsed);
		builder.append(", subscribedCapaityInGB=");
		builder.append(subscribedCapaityInGB);
		builder.append(", usableCapaityInGB=");
		builder.append(usableCapaityInGB);
		builder.append(", usedCapaityInGB=");
		builder.append(usedCapaityInGB);
		builder.append("]");
		return builder.toString();
	}

}

