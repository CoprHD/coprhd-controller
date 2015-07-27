/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("FCEndpoint")
public class FCEndpoint extends DataObject{
	
	// vsan id
	private String _fabricId;                    // Vsan 
	// vsan wwn
	private String _fabricWwn;                  // the WWN of the vsan (fabric) 
	// the hostname of the switch this port is in. This is not
	// always the same switch as the parent switch as floggi
	// database can return ports in other connected switches
	private String _switchName;
	// the name of the switch (local) interface of this this port 
	private String _switchInterface;
	// the name of the local port
	private String _switchPortName;
	// fc id
	private String _fcid;
	// the name of the remote port of this connection (WWPN)
	private String _remotePortName;
	// the name of the remote node of this connection (WWNN)
	private String _remoteNodeName;
    // the name of the remote port alias of this connection
    private String _remotePortAlias;
    // the parent FC switch where this port was discovered.
    private URI _networkDevice;
    // The following two fields make connections sort of "sticky".
    // They are retained until a certain number of samples have passwed with us
    // not having seen them, _and_ a certain number of milliseconds have passed without
    // us having seen them. Here AWOL means "Absent Without Leave" (military term).
    private Integer awolCount = 0;			// count of samples with Endpoint missing
    private Long    awolTime;				// time first reported missing
    
    @Name("fabric")
	public String getFabricId() {
		return _fabricId;
	}
	public void setFabricId(String fabricId) {
		this._fabricId = fabricId;
		setChanged("fabric");
	}
	
	@AlternateId("AltIdIndex")
    @Name ("fabricWwn")
	public String getFabricWwn() {
		return _fabricWwn;
	}
	public void setFabricWwn(String _fabricWwn) {
		this._fabricWwn = _fabricWwn.toUpperCase();
		setChanged("fabricWwn");
	}
	
	@Name("switchName")
	public String getSwitchName() {
		return _switchName;
	}
	public void setSwitchName(String switchName) {
		this._switchName = switchName;
		setChanged("switchName");
	}

    @Name("switchInterface")
	public String getSwitchInterface() {
		return _switchInterface;
	}
	public void setSwitchInterface(String switchInterface) {
		this._switchInterface = switchInterface;
		setChanged("switchInterface");
	}

    @Name("switchPortName")
	public String getSwitchPortName() {
		return _switchPortName;
	}
	public void setSwitchPortName(String switchPortName) {
		this._switchPortName = switchPortName;
		setChanged("switchPortName");
	}

    @Name("fcid")
	public String getFcid() {
		return _fcid;
	}
	public void setFcid(String fcid) {
		this._fcid = fcid;
		setChanged("fcid");
	}

	@AlternateId("FCEndPointAltIndex")
    @Name("remotePortName")
	public String getRemotePortName() {
		return _remotePortName;
	}
	public void setRemotePortName(String remotePortName) {
		this._remotePortName = remotePortName.toUpperCase();
		setChanged("remotePortName");
	}

    @Name("remoteNodeName")
	public String getRemoteNodeName() {
		return _remoteNodeName;
	}
	public void setRemoteNodeName(String remoteNodeName) {
		this._remoteNodeName = remoteNodeName.toUpperCase();
		setChanged("remoteNodeName");
	}

    @AlternateId("FCEndPointAliasIndex")
    @Name("remotePortAlias")
	public String getRemotePortAlias() {
        return _remotePortAlias;
    }

    public void setRemotePortAlias(String remotePortAlias) {
        this._remotePortAlias = remotePortAlias; // TODO - Should this be upper-or-lower-cased??
        setChanged("remotePortAlias");
    }

    @RelationIndex(cf = "RelationIndex", type = NetworkSystem.class)
    @Name("networkDevice")
    public URI getNetworkDevice() {
        return _networkDevice;
    }

    public void setNetworkDevice(URI networkdevice) {
        _networkDevice = networkdevice;
        setChanged("networkDevice");
    }
    
    @Name("awolCount")
	public Integer getAwolCount() {
		return awolCount;
	}
	public void setAwolCount(Integer awolCount) {
		this.awolCount = awolCount;
		setChanged("awolCount");
	}
	
	@Name("awolTime")
	public Long getAwolTime() {
		return awolTime;
	}
	public void setAwolTime(Long awolTime) {
		this.awolTime = awolTime;
		setChanged("awolTime");
	}
}
