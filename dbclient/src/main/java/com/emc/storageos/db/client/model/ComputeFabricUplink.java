/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.Set;

public abstract class ComputeFabricUplink extends DataObject {

	private URI computeSystem;
	private String dn;
	private String wwpn;
	private String vsanId;
	private String switchId;
	private StringSet vsans;
	private String peerDn;

	@RelationIndex(cf = "ComputeRelationIndex", type = ComputeSystem.class)
	@Name("computeSystem")
	public URI getComputeSystem() {
		return computeSystem;
	}

	public void setComputeSystem(URI computeSystem) {
		this.computeSystem = computeSystem;
		setChanged("computeSystem");
	}

	@Name("dn")
	public String getDn() {
		return dn;
	}

	public void setDn(String dn) {
		this.dn = dn;
		setChanged("dn");
	}

	@Name("wwpn")
	public String getWwpn() {
		return wwpn;
	}

	public void setWwpn(String wwpn) {
		this.wwpn = wwpn;
		setChanged("wwpn");
	}

	@Name("vsanId")
	public String getVsanId() {
		return vsanId;
	}

	public void setVsanId(String vsanId) {
		this.vsanId = vsanId;
		setChanged("vsanId");
	}

	@Name("switchId")
	public String getSwitchId() {
		return switchId;
	}

	public void setSwitchId(String switchId) {
		this.switchId = switchId;
		setChanged("switchId");
	}

	@Name("vsans")
	public StringSet getVsans() {
		return vsans;
	}

	public void setVsans(final StringSet vsans) {
		this.vsans = vsans;
		setChanged("vsans");
	}

	public void addVsans(final Set<String> vsans) {
		if (null == this.vsans) {
			setVsans(new StringSet());
		}
		if (0 < vsans.size()) {
			this.vsans.addAll(vsans);
		}
	}

	@Name("peerDn")
	public String getPeerDn() {
		return peerDn;
	}

	public void setPeerDn(String peerDn) {
		this.peerDn = peerDn;
		setChanged("peerDn");
	}
}
