/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller;

import java.net.URI;
import java.util.Map;

import com.emc.cloud.platform.clientlib.ClientGeneralException;
import com.emc.storageos.Controller;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.ComputeVirtualPool;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.TaskCompleter;

public interface ComputeDevice extends Controller {

	public void discoverComputeSystem(URI computeSystemId) throws InternalException;

	public void createHost(ComputeSystem computeSystem,Host host, ComputeVirtualPool vcp,VirtualArray varray,TaskCompleter completer) throws InternalException;

	public Map<String,Boolean> prepareOsInstallNetwork(URI computeSystemId, URI computeElementId)
			throws InternalException;
	
	public void removeOsInstallNetwork(URI computeSystemId, URI computeElementId, Map<String, Boolean> vlanMap)
			throws InternalException;
	
	public String unbindHostFromTemplate(URI computeSystemId, URI hostId)
			throws InternalException;
	
	public void rebindHostToTemplate(URI computeSystemId, URI hostId)
			throws InternalException;

	public void powerUpComputeElement(URI computeSystemId, URI computeElementId)
			throws InternalException;

	public void powerDownComputeElement(URI computeSystemId, URI computeElementId)
			throws InternalException;
	
	public void setLanBootTarget(ComputeSystem cs, URI computeElementId,URI hostId,boolean waitForReboot) throws InternalException;

	public void setSanBootTarget(ComputeSystem cs, URI computeElementId,URI hostId, URI volumeId,boolean waitForReboot) throws InternalException;

    public void clearDeviceSession(URI computeSystemId) throws InternalException;

	public void deactivateHost(ComputeSystem cs, Host host) throws ClientGeneralException;

}
