/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.datadomain.restapi.model;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@JsonRootName(value="stats_capacity")
public class DDStatsCapacityInfos {
	
	@SerializedName("data_view")
	@JsonProperty(value="data_view")
	private String dataView;

	@SerializedName("stats_capacity")
	@JsonProperty(value="stats_capacity")
	private List<DDStatsCapacityInfo> statsCapacity;

	@SerializedName("requested_data_interval")
	@JsonProperty(value="requested_data_interval")
	private DDStatsIntervalQuery requestedDataInterval;

	@SerializedName("returned_data_interval")
	@JsonProperty(value="returned_data_interval")
	private DDStatsIntervalQuery returnedDataInterval;

	@SerializedName("paging_info")
	@JsonProperty(value="paging_info")
	private DDPaging pagingInfo;

	private List<DDRestLinkRep> link;

	public List<DDStatsCapacityInfo> getStatsCapacity() {
		return statsCapacity;
	}

	public void setStatsCapacity(List<DDStatsCapacityInfo> statsCapacity) {
		this.statsCapacity = statsCapacity;
	}

	public DDStatsIntervalQuery getRequestedDataInterval() {
		return requestedDataInterval;
	}

	public void setRequestedDataInterval(DDStatsIntervalQuery requestedDataInterval) {
		this.requestedDataInterval = requestedDataInterval;
	}

	public DDStatsIntervalQuery getReturnedDataInterval() {
		return returnedDataInterval;
	}

	public void setReturnedDataInterval(DDStatsIntervalQuery returnedDataInterval) {
		this.returnedDataInterval = returnedDataInterval;
	}

	public DDPaging getPagingInfo() {
		return pagingInfo;
	}

	public void setPagingInfo(DDPaging pagingInfo) {
		this.pagingInfo = pagingInfo;
	}

	public List<DDRestLinkRep> getLink() {
		return link;
	}

	public void setLink(List<DDRestLinkRep> link) {
		this.link = link;
	}

	public String toString() {
		return new Gson().toJson(this).toString();
	}

}
