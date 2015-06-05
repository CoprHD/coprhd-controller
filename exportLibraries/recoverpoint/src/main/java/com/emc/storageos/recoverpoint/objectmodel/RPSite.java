/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 **/
package com.emc.storageos.recoverpoint.objectmodel;

import java.io.Serializable;
import java.sql.Timestamp;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.emc.fapiclient.ws.ClusterSANVolumes;
import com.emc.storageos.recoverpoint.utils.TimestampAdapter;
import com.emc.storageos.recoverpoint.utils.TimestampFormatter;

@XmlRootElement
public class RPSite implements Serializable {
	private static final long serialVersionUID = -7772320180511549531L;
	private String _siteName;
	private long _siteUID;
	private String _siteManagementIPv4;	
	private String _name; // The name is a combination of the site IP addr and the site UID (e.g. 10.241.176.16:1)
	private String _siteVersion;
	private int _numRPAs;
	private Timestamp _lastDiscovered;
	private String _username;
	private String _password;
	private String _model = "RecoverPoint Site";
	private ClusterSANVolumes _siteVolumes;
	private String _siteGUID;
	private String _internalSiteName;

    public RPSite() {
		super();
	}

	public void setName(String name) {
		this._name = name;
	}

	@XmlElement
	public String getName() {
		return _name;
	}

	@XmlElement
	public String getUsername() {
		return _username;
	}

	@XmlElement
	public String getPassword() {
		return _password;
	}

	@XmlElement
	public String getSiteName() {
		return _siteName;
	}

	@XmlElement
	public long getSiteUID() {
		return _siteUID;
	}

	@XmlElement
	public String getSiteManagementIPv4() {
		return _siteManagementIPv4;
	}

	/**
	 * Get the last modified timestamp in a nice format
	 * 
	 * @return timestamp object
	 */
	@XmlElement
	public String getLastDiscoveredFormatted() {
		return TimestampFormatter.toString(_lastDiscovered);
	}

	@XmlJavaTypeAdapter(TimestampAdapter.class)
	public Timestamp getLastDiscovered() {
		return _lastDiscovered;
	}

	public void setLastDiscovered(Timestamp lastDiscovered) {
		this._lastDiscovered = lastDiscovered;
	}

	@XmlElement
	public String getSiteVersion() {
		return _siteVersion;
	}

	public void setSiteVersion(String siteVersion) {
		this._siteVersion = siteVersion;
	}

	@XmlElement
	public int getNumRPAs() {
		return _numRPAs;
	}

	public void setNumRPAs(int numRPAs) {
		this._numRPAs = numRPAs;
	}

	public void setCredentials(String username, String password) {
		this._username = username;
		this._password = password;
	}

	public void setSiteName(String name) {
		this._siteName = name;
	}

	public void setSiteUID(long l) {
		this._siteUID = l;
	}

	public void setSiteManagementIPv4(String IPv4Addr) {
		this._siteManagementIPv4 = IPv4Addr;
	}

	public void cloneMe(RPSite clone) {
		setSiteName(clone.getSiteName());
		setSiteUID(clone.getSiteUID());
		setSiteManagementIPv4(clone.getSiteManagementIPv4());
		setName(clone.getName());
		setSiteVersion(clone.getSiteVersion());
		setNumRPAs(clone.getNumRPAs());
		setLastDiscovered(clone.getLastDiscovered());
		setUsername(clone.getUsername());
		setPassword(clone.getPassword());
		setSiteVolumes(clone.getSiteVolumes());
		setSiteGUID(clone.getSiteGUID());
	}

	public void setUsername(String username) {
		this._username = username;
	}

	public void setPassword(String password) {
		this._password = password;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((_siteManagementIPv4 == null) ? 0 : _siteManagementIPv4.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (this == obj)
			return true;
		if (getClass() != obj.getClass())
			return false;
		RPSite other = (RPSite) obj;
		if (_siteManagementIPv4 == null) {
			if (other._siteManagementIPv4 != null)
				return false;
		} else if (!_siteManagementIPv4.equals(other._siteManagementIPv4))
			return false;
		return true;
	}

	/**
	 * @return the model
	 */
	public String getModel() {
		return _model;
	}

	/**
	 * @param model the model to set
	 */
	public void setModel(String model) {
		this._model = model;
	}

	@XmlElement
	public ClusterSANVolumes getSiteVolumes() {
		return _siteVolumes;
	}

	public void setSiteVolumes(ClusterSANVolumes siteVolumes) {
		this._siteVolumes = siteVolumes;
	}

	public String getSiteGUID() {
		return _siteGUID;
	}

	public void setSiteGUID(String siteGUID) {
		this._siteGUID = siteGUID;
	}

    public String getInternalSiteName() {
        return _internalSiteName;
    }

    public void setInternalSiteName(String internalSiteName) {
        this._internalSiteName = internalSiteName;
    }

}
