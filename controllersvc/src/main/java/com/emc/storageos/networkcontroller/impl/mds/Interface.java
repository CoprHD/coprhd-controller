/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
   instance of CISCO_PhysicalElement {
   Caption = null;
   Description = null;
   InstallDate = null;
   Name = "fc1/47";
   OperationalStatus = null;
   StatusDescriptions = null;
   Status = null;
   Tag = "16965632";
   CreationClassName = "CISCO_PhysicalElement";
   ElementName = "fc1/47";
   Manufacturer = "Cisco Systems";
   Model = "MDS 9148 FC (1 Slot) Chassis";
   SKU = null;
   SerialNumber = "202F00059B243DD0";
   Version = "5.0(4b)";
   PartNumber = "73-13049-05";
   OtherIdentifyingInfo = null;
   PoweredOn = null;
   ManufactureDate = null;
   VendorEquipmentType = null;
   UserTracking = null;
   CanBeFRUed = null;
};
 */

public class Interface {
    public static Map<String, Interface> snToInterface = new HashMap<String, Interface>();
    public static List<Interface> interfaces = new ArrayList<Interface>();
    public static final Boolean debug = false;
    
    String name;
    String tag;
    String elementName;
    String description;
    String wwpn;      // In MDS SMI-S, We depend on sn having the wwpn of the interface to match with other structures.
    String status;
    String mode;
    String fcid;
    String vsan;
    
    public Interface(String name) {
        this.name = name;
    }
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getWwpn() {
		return wwpn;
	}
	public void setWwpn(String wwpn) {
		this.wwpn = wwpn;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public String getMode() {
		return mode;
	}
	public void setMode(String mode) {
		this.mode = mode;
	}
	public String getVsan() {
		return vsan;
	}
	public void setVsan(String vsan) {
		this.vsan = vsan;
	}
	public String getFcid() {
		return fcid;
	}
	public void setFcid(String fcid) {
		this.fcid = fcid;
	}
}
