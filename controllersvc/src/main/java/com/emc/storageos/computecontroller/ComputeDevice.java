/*
 * Copyright (c) 2015 EMC Corporation
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

    /**
     * Discover compute system
     *
     * @param computeSystemId
     *            {@link URI} computeSystem id
     */
    public void discoverComputeSystem(URI computeSystemId) throws InternalException;

    /**
     * Create host using the specified params
     *
     * @param computeSystem
     *            {@link ComputeSystem} computesystem instance
     * @param host
     *            {@link Host} host instance
     * @param vcp
     *            {@link ComputeVirtualPool} compute virtualpool instance
     * @param varray
     *            (@link VirtualArray} virtual array
     * @param completer {@link TaskCompleter} completer
     */
    public void createHost(ComputeSystem computeSystem, Host host, ComputeVirtualPool vcp, VirtualArray varray, TaskCompleter completer)
            throws InternalException;

    /**
     * Sets the OS install VLAN
     * @param computeSystemId {@link URI} computeSystem id
     * @param computeElementId {@link URI} computeElement id
     * @return vlanMap
     * @throws InternalException
     */
    public Map<String, Boolean> prepareOsInstallNetwork(URI computeSystemId, URI computeElementId)
            throws InternalException;

    /**
     * Remove the OS install vLAN
     * @param computeSystemId {@link URI} computeSystem id
     * @param computeElementId {@link URI} computeElement id
     * @param vlanMap {@link Map} VLAN map
     * @throws InternalException
     */
    public void removeOsInstallNetwork(URI computeSystemId, URI computeElementId, Map<String, Boolean> vlanMap)
            throws InternalException;

    /**
     * Unbinds the host from the service profile template
     * @param computeSystemId {@link URI} computeSystem id
     * @param hostId {@link URI} host id
     * @return sptDn service profile template distinguished name
     * @throws InternalException
     */
    public String unbindHostFromTemplate(URI computeSystemId, URI hostId)
            throws InternalException;

    /**
     * Bind host to the service profile template
     * @param computeSystemId {@link URI} computeSystem id
     * @param hostId {@link URI} host id
     * @throws InternalException
     */
    public void rebindHostToTemplate(URI computeSystemId, URI hostId)
            throws InternalException;

    /**
     * Power up/on the compute element
     * @param computeSystemId {@link URI} computeSystem id
     * @param hostId {@link URI} host id
     * @throws InternalException
     */
    public void powerUpComputeElement(URI computeSystemId, URI hostId)
            throws InternalException;

    /**
     * Power down the compute element
     * @param computeSystemId {@link URI} computeSystem id
     * @param hostId {@link URI} host id
     * @throws InternalException
     */
    public void powerDownComputeElement(URI computeSystemId, URI hostId)
            throws InternalException;

    /**
     * Set the service profile to boot from LAN
     * @param cs {@link ComputeSystem} cs instance
     * @param computeElementId {@link URI} computeElement id
     * @param hostId {@link URI} host id
     * @param waitForReboot boolean indicating if reboot requires user ack
     * @throws InternalException
     */
    public void setLanBootTarget(ComputeSystem cs, URI computeElementId, URI hostId, boolean waitForReboot) throws InternalException;

    /**
     * Set the service profile to no boot
     * @param cs {@link ComputeSystem} cs instance
     * @param computeElementId {@link URI} computeElement id
     * @param hostId {@link URI} host id
     * @param waitForReboot boolean indicating if reboot requires user ack
     * @throws InternalException
     */
    public void setNoBoot(ComputeSystem cs, URI computeElementId, URI hostId, boolean waitForReboot) throws InternalException;

    /**
     * Set the service profile to boot from SAN
     * @param cs {@link ComputeSystem} cs instance
     * @param computeElementId {@link URI} computeElement id
     * @param hostId {@link URI} host id
     * @param waitForReboot boolean indicating if reboot requires user ack
     * @throws InternalException
     */
    public void setSanBootTarget(ComputeSystem cs, URI computeElementId, URI hostId, URI volumeId, boolean waitForReboot)
            throws InternalException;

    /**
     * Clear the UCS session
     * @param computeSystemId {@link URI} computeSystem id
     * @throws InternalException
     */
    public void clearDeviceSession(URI computeSystemId) throws InternalException;

    /**
     * Deactivate the host, unbinds and deletes the service profile associated to the host
     * @param cs {@link ComputeSystem} cs instance
     * @param host {@link Host} host instance
     * @throws ClientGeneralException
     */
    public void deactivateHost(ComputeSystem cs, Host host) throws ClientGeneralException;

    /**
     * Unbinds the host's service profile from the associated blade.
     * Determines the service profile to unbind using host's serviceProfile association.
     * In case of host provisioned using pre-Anakin version of ViPR and no serviceProfile association yet set,
     * serviceprofile to unbind will be determined by trying to find a serviceProfile that matches
     * the computeElement's uuid.
     * @param cs {@link ComputeSystem} cs instance
     * @param host {@link Host} host instance
     */
    public void unbindHostFromComputeElement(ComputeSystem cs, Host host) throws ClientGeneralException;

   /**
    * Binds the host's service profile to the associated blade.
    * @param computeSystem {@link ComputeSystem} instance
    * @param hostURI {@link URI} host id
    * @param contextStepId {@link String} step Id to load step data if any else null can be specified
    * @param stepId {@link String} stepId
    */
    public void bindServiceProfileToBlade(ComputeSystem computeSystem, URI hostURI, String contextStepId, String stepId);

    /**
     * Fetch service profile associated state
     * @param computeSystem {@link ComputeSystem} instance
     * @param hostURI {@link URI} host id
     * @param stepId {@link String} stepId
     * @return {@link String} service profile associate state.
     * @throws ClientGeneralException
     */
    public String fetchServiceProfileAssociatedState(ComputeSystem computeSystem, URI hostURI, String stepId)  throws ClientGeneralException;
}
