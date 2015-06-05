/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

@Cf("ComputeVnic")
public class ComputeVnic extends DataObject {

    private URI _computeElement;
    private URI _serviceProfileTemplate;
	private String dn;
    private String name;
    private String mtu;
    private String order;
    private String switchId;
    private String mac;
    private String templateName;

    private StringSet vlans;
    private String nativeVlan;


	@RelationIndex(cf = "ComputeRelationIndex", type = ComputeElement.class)
	@Name("computeElement")
    public URI getComputeElement() {
        return _computeElement;
    }

    public void setComputeElement(URI _computeElement) {
        this._computeElement = _computeElement;
        setChanged("computeElement");
    }

    @RelationIndex(cf = "ComputeRelationIndex", type = UCSServiceProfileTemplate.class)
    @Name("serviceProfileTemplate")
    public URI getServiceProfileTemplate() {
        return this._serviceProfileTemplate;
    }

    public void setServiceProfileTemplate(URI serviceProfileTemplate) {
        this._serviceProfileTemplate = serviceProfileTemplate;
        setChanged("serviceProfileTemplate");
    }

    @Name("dn")
	public String getDn() {
		return dn;
	}

	public void setDn(String dn) {
		this.dn = dn;
		setChanged("dn");
	}

    @Name("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        setChanged("name");
    }

    @Name("vlans")
    public StringSet getVlans() {
        return vlans;
    }

    public void setVlans(StringSet vlans) {
        this.vlans = vlans;
        setChanged("vlans");
    }

    @Name("mtu")
    public String getMtu() {
        return mtu;
    }

    public void setMtu(String mtu) {
        this.mtu = mtu;
        setChanged("mtu");
    }

    @Name("order")
    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
        setChanged("order");
    }

    @Name("switchId")
    public String getSwitchId() {
        return switchId;
    }

    public void setSwitchId(String switchId) {
        this.switchId = switchId;
        setChanged("switchId");
    }

    @Name("mac")
    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
        setChanged("mac");
    }

    @Name("nativeVlan")
    public String getNativeVlan() {
        return nativeVlan;
    }

    public void setNativeVlan(String nativeVlan) {
        this.nativeVlan = nativeVlan;
        setChanged("nativeVlan");
    }
    
    @Name("templateName")
    public String getTemplateName() {
		return templateName;
	}

	public void setTemplateName(String templateName) {
		this.templateName = templateName;
		setChanged("templateName");
	}
}
