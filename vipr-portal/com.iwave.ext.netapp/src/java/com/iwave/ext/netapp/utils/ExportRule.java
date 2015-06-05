/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.netapp.utils;

import java.net.URI;
import java.util.Set;

public class ExportRule {

	// Part of payload Model Attributes
	private URI fsID;
	private URI snapShotID;
	private String exportPath;
	private String anon;
	private String secFlavor;
	private Set<String> readOnlyHosts;
	private Set<String> readWriteHosts;
	private Set<String> rootHosts;

	public URI getFsID() {
		return fsID;
	}

	public void setFsID(URI fsID) {
		this.fsID = fsID;
	}

	public URI getSnapShotID() {
		return snapShotID;
	}

	public void setSnapShotID(URI snapShotID) {
		this.snapShotID = snapShotID;
	}

	public String getExportPath() {
		return exportPath;
	}

	public void setExportPath(String exportPath) {
		this.exportPath = exportPath;
	}

	public Set<String> getReadOnlyHosts() {
		return readOnlyHosts;
	}

	public void setReadOnlyHosts(Set<String> readOnlyHosts) {
		this.readOnlyHosts = readOnlyHosts;
	}

	public Set<String> getReadWriteHosts() {
		return readWriteHosts;
	}

	public void setReadWriteHosts(Set<String> readWriteHosts) {
		this.readWriteHosts = readWriteHosts;
	}

	public Set<String> getRootHosts() {
		return rootHosts;
	}

	public void setRootHosts(Set<String> rootHosts) {
		this.rootHosts = rootHosts;
	}

	/**
	 * Security flavor of an export e.g. sys, krb, krbp or krbi
	 * 
	 * @valid none
	 */
	public String getSecFlavor() {
		return secFlavor;
	}

	public void setSecFlavor(String secFlavor) {
		this.secFlavor = secFlavor;
	}

	/**
	 * Anonymous root user mapping e.g. "root", "nobody" or "anyUserName"
	 * 
	 * @valid none
	 */
	public String getAnon() {
		return anon;
	}

	public void setAnon(String anon) {
		this.anon = anon;
	}

	@Override
	public String toString() {

		StringBuilder sb = new StringBuilder();
		sb.append("[ secFlavor : ")
				.append((secFlavor != null) ? secFlavor : "").append("] ");
		sb.append("[anon : ").append((anon != null) ? anon : "").append("] ");
		sb.append("[ number of readOnlyHosts : ")
				.append((readOnlyHosts != null) ? readOnlyHosts.size() : 0)
				.append("] ").append(getHostsPrintLog(readOnlyHosts));

		sb.append("[ number of readWriteHosts : ")
				.append((readWriteHosts != null) ? readWriteHosts.size() : 0)
				.append("] ").append(getHostsPrintLog(readWriteHosts));

		sb.append("[ number of rootHosts : ")
				.append((rootHosts != null) ? rootHosts.size() : 0).append("]")
				.append(getHostsPrintLog(rootHosts));

		return sb.toString();

	}

	private String getHostsPrintLog(Set<String> hosts) {
		StringBuilder sb = new StringBuilder();
		if (hosts != null && hosts.size() > 0) {
			for (String endPoint : hosts) {
				sb.append("{").append(endPoint).append("}");
			}
		}
		return sb.toString();
	}

	/*
	 * private ExportRule(URI fsID, URI snapShotID, String exportPath, String
	 * anon, String secFlavor, Set<String> readOnlyHosts, Set<String>
	 * readWriteHosts, Set<String> rootHosts) { super(); this.fsID = fsID;
	 * this.snapShotID = snapShotID; this.exportPath = exportPath; this.anon =
	 * anon; this.secFlavor = secFlavor; this.readOnlyHosts = readOnlyHosts;
	 * this.readWriteHosts = readWriteHosts; this.rootHosts = rootHosts; }
	 */

	// Empty constructor used for certain container purposes
	public ExportRule() {

	}

}
