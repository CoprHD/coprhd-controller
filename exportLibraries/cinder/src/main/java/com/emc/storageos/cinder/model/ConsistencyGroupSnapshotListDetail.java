package com.emc.storageos.cinder.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import org.codehaus.jackson.annotate.JsonProperty;

public class ConsistencyGroupSnapshotListDetail {
	
	List<ConsistencyGroupSnapshotDetail> ConsistencyGroupSnapshotDetailList = new ArrayList<ConsistencyGroupSnapshotDetail>();

	@XmlElement(name = "cgsnapshots")
	@JsonProperty(value = "cgsnapshots")
	public List<ConsistencyGroupSnapshotDetail> getConsistencyGroupSnapshotDetailList() {
		return ConsistencyGroupSnapshotDetailList;
	}

	public void addConsistencyGroupSnapshotDetail(ConsistencyGroupSnapshotDetail cgSnapshotDetail
			) {
		if(null != cgSnapshotDetail){
			ConsistencyGroupSnapshotDetailList.add(cgSnapshotDetail);
		}
	}

}
