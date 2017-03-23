/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl.adapter;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.computesystemcontroller.impl.DiscoveryStatusUtils;
import com.emc.storageos.computesystemcontroller.impl.HostToComputeElementMatcher;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.HostType;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.util.SanUtils;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.iwave.ext.vmware.EsxVersion;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.HostConfigInfo;
import com.vmware.vim25.HostFibreChannelHba;
import com.vmware.vim25.HostHardwareInfo;
import com.vmware.vim25.HostHostBusAdapter;
import com.vmware.vim25.HostInternetScsiHba;
import com.vmware.vim25.HostIpConfigIpV6Address;
import com.vmware.vim25.HostIpConfigIpV6AddressConfiguration;
import com.vmware.vim25.HostRuntimeInfo;
import com.vmware.vim25.HostSystemConnectionState;
import com.vmware.vim25.HostVirtualNic;
import com.vmware.vim25.mo.HostSystem;

/**
 * 
 * Discovery Adapter for ESX hosts.
 * 
 * @author kumara4
 * 
 */
@Component
public class EsxHostDiscoveryAdapter extends AbstractHostDiscoveryAdapter {

    // VMWare KB 1006250: The UUID of a host can be non unique on White box hardware.
    // This is a known non-unique UUID
    private static String KNOWN_DUPLICATE_UUID = "03000200-0400-0500-0006-000700080009";

    /**
     * Create helper API instance of VCenter to traverse tree structure of mob.
     * 
     * @param host
     *            - {@link Host} instance
     * @return {@link VCenterAPI}
     */
    public static VCenterAPI createVCenterAPI(Host host) {
        int port = host.getPortNumber() != null ? host.getPortNumber() : 443;

        URL url;
        try {
            url = new URL("https", host.getHostName(), port, "/sdk");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e.getMessage());
        }
        String username = host.getUsername();
        String password = host.getPassword();
        return new VCenterAPI(url, username, password);
    }

    /**
     * Returns host type (supported type)
     * 
     * @return
     */
    @Override
    protected String getSupportedType() {
        return HostType.Esx.name();
    }

    /**
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.computesystemcontroller.impl.ComputeSystemDiscoveryAdapter #discoverTarget(java.lang.String)
     */
    @Override
    public void discoverTarget(String targetId) {
        Host host = getModelClient().hosts().findById(targetId);
        HostStateChange changes = new HostStateChange(host, host.getCluster());
        if (checkHostCredentials(host))
        {
            discoverEsxHost(host, changes);
        } else
        {
            debug("Skipping Esx host discovery, credentials not found for host - %s", host.getHostName());
        }
    }

    /**
     * Check if the given host has credentials
     * 
     * @param host - {@link Host}
     * @return
     */
    private boolean checkHostCredentials(Host host) {
        boolean hasCredentials = false;
        if (null != host.getUsername() && null != host.getPassword()) {
            hasCredentials = true;
        }
        return hasCredentials;

    }

    /**
     * Discover Esx host
     * 
     * @param host
     *            {@link Host} instance to be discovered
     * @param changes
     *            {@link HostStateChange} instance containing host changes
     *            during discovery.
     */
    private void discoverEsxHost(Host host, HostStateChange changes) {
        EsxVersion esxVersion = getVersion(host);
        if (null != esxVersion
                && getVersionValidator().isValidEsxVersion(esxVersion)) {
            changes.setNewCluster(host.getCluster());
            discoverHost(host, changes);
            processHostChanges(changes);
            matchHostToComputeElements(host);
        } else {
            host.setCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.name());
            save(host);
            throw ComputeSystemControllerException.exceptions
                    .incompatibleHostVersion("Esx", esxVersion.toString(),
                            getVersionValidator().getEsxMinimumVersion(false)
                                    .toString());
        }
    }

    /**
     * Match hosts to compute elements
     */
    @Override
    public void matchHostToComputeElements(Host host) {
        HostToComputeElementMatcher.matchHostToComputeElements(getDbClient(),host.getId());
    }

    /**
     * Discover Esx host
     * 
     * @param host
     *            {@link Host} instance to be discovered
     * @param changes
     *            {@link HostStateChange} instance containing host changes
     *            during discovery.
     */
    @Override
    protected void discoverHost(Host host, HostStateChange changes) {
        VCenterAPI api = createVCenterAPI(host);
        try {
            List<HostSystem> hostSystems = api.listAllHostSystems();
            if (null != hostSystems && !hostSystems.isEmpty())
            {
                // getting the 0th element only coz we are querying an ESX for
                // hostsystems and this will always return one or none.
                HostSystem hostSystem = hostSystems.get(0);

                Host targetHost = null;
                HostHardwareInfo hw = hostSystem.getHardware();
                String uuid = null;
                if (hw != null && hw.systemInfo != null
                        && StringUtils.isNotBlank(hw.systemInfo.uuid)) {
                    // try finding host by UUID
                    uuid = hw.systemInfo.uuid;
                    if (KNOWN_DUPLICATE_UUID.equalsIgnoreCase(uuid)) {
                        info("Host " + hostSystem.getName() + " contains a known non-unique UUID");
                    }
                    // search host by uuid in VIPR if host already discovered
                    targetHost = findHostByUuid(uuid);
                    checkDuplicateHost(host, targetHost);
                }

                if (targetHost == null) {
                    // if target host is null, this is a new discovery.
                    targetHost = host;
                }
                targetHost.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
                targetHost.setDiscoverable(true);
                if (targetHost.getId() == null) {
                    targetHost
                            .setRegistrationStatus(RegistrationStatus.REGISTERED
                                    .toString());
                }
                targetHost.setOsVersion(hostSystem.getConfig().getProduct()
                        .getVersion());

                if (hw != null && hw.biosInfo != null
                        && StringUtils.isNotBlank(hw.biosInfo.biosVersion)) {
                    targetHost.setBios(hw.biosInfo.biosVersion);
                }
                
                if (null != uuid) {
                    targetHost.setUuid(uuid);
                }
                save(targetHost);

                DiscoveryStatusUtils.markAsProcessing(getModelClient(),
                        targetHost);
                try {
                    discoverHost(hostSystem, targetHost, changes);
                    DiscoveryStatusUtils.markAsSucceeded(getModelClient(),
                            targetHost);
                } catch (RuntimeException e) {
                    warn(e, "Problem discovering host %s",
                            targetHost.getLabel());
                    DiscoveryStatusUtils.markAsFailed(getModelClient(),
                            targetHost, e.getMessage(), e);
                }
            }
        } finally {
            api.logout();
        }
    }

    /**
     * Check if the host already exists in VIPR
     * 
     * @param host - {@link Host} instance being discovered / added.
     * @param targetHost {@link Host} instance from VIPR DB.
     */
    private void checkDuplicateHost(Host host, Host targetHost) {
        if (targetHost != null && !(host.getId().equals(targetHost.getId())))
        {
            ComputeSystemControllerException ex =
                    ComputeSystemControllerException.exceptions.duplicateSystem("Host", targetHost.getLabel());
            DiscoveryStatusUtils.markAsFailed(modelClient, host, ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Discover Exs host and its IpIterfaces and Initiators.
     * 
     * @param hostSystem
     *            - {@link HostSystem} VI SDK managedObject instance
     * @param targetHost
     *            - {@link Host} being discovered.
     * @param changes
     *            {@link HostStateChange} instance containing host changes
     *            during discovery.
     */
    private void discoverHost(HostSystem hostSystem, Host targetHost,
            HostStateChange changes) {
        // Only attempt to update ip interfaces or initiators for connected
        // hosts
        HostSystemConnectionState connectionState = getConnectionState(hostSystem);
        if (connectionState == HostSystemConnectionState.connected) {

            // discover initiators
            List<Initiator> oldInitiators = new ArrayList<Initiator>();
            List<Initiator> addedInitiators = new ArrayList<Initiator>();

            discoverConnectedHostInitiators(hostSystem, targetHost,
                    oldInitiators, addedInitiators);

            if (!oldInitiators.isEmpty() || !addedInitiators.isEmpty()) {
                Collection<URI> oldInitiatorIds = Lists
                        .newArrayList(Collections2
                                .transform(oldInitiators,
                                        CommonTransformerFunctions
                                                .fctnDataObjectToID()));
                changes.setOldInitiators(oldInitiatorIds);

                Collection<URI> addedInitiatorIds = Lists
                        .newArrayList(Collections2
                                .transform(addedInitiators,
                                        CommonTransformerFunctions
                                                .fctnDataObjectToID()));
                changes.setNewInitiators(addedInitiatorIds);
            }
        } else {
            if (connectionState == HostSystemConnectionState.disconnected) {
                throw new IllegalStateException("Host is disconnected");
            } else if (connectionState == HostSystemConnectionState.notResponding) {
                throw new IllegalStateException("Host is not responding");
            } else {
                throw new IllegalStateException(
                        "Could not determine host connection state");
            }
        }
    }

    /**
     * Discovers connected Host's Initiators and Ipinterfaces
     * 
     * @param hostSystem
     *            - {@link HostSystem} VI SDK managedObject instance
     * @param targetHost
     *            - {@link Host} being discovered.
     * @param oldInitiators
     *            - old initiator list
     * @param addedInitiators
     *            - new/added initiator list
     */
    protected void discoverConnectedHostInitiators(HostSystem hostSystem,
            Host targetHost, List<Initiator> oldInitiators,
            List<Initiator> addedInitiators) {

        // discover ipInterfaces
        info(String.format("Discovering IP interfaces for %s", targetHost.forDisplay()));
        List<IpInterface> oldIpInterfaces = new ArrayList<IpInterface>();
        Iterables.addAll(oldIpInterfaces, getIpInterfaces(targetHost));
        for (HostVirtualNic nic : getNics(hostSystem)) {
            if (isIp6Interface(nic)) {
                IpInterface ipInterface = getOrCreateIpInterface(
                        oldIpInterfaces, nic.spec.getIp().ipAddress);
                discoverIp6Interface(targetHost, ipInterface, nic);
            }
            if (isIp4Interface(nic)) {
                IpInterface ipInterface = getOrCreateIpInterface(
                        oldIpInterfaces, nic.spec.getIp().ipAddress);
                discoverIp4Interface(targetHost, ipInterface, nic);
            }
        }
        removeDiscoveredInterfaces(oldIpInterfaces);

        info(String.format("Discovering initiators for %s", targetHost.forDisplay()));
        Iterables.addAll(oldInitiators, getInitiators(targetHost));
        for (HostHostBusAdapter hba : getHostBusAdapters(hostSystem)) {
            if (hba instanceof HostFibreChannelHba) {
                String port = SanUtils.normalizeWWN(((HostFibreChannelHba) hba)
                        .getPortWorldWideName());
                Initiator initiator;
                if (findInitiatorByPort(oldInitiators, port) == null) {
                    initiator = getOrCreateInitiator(targetHost.getId(), oldInitiators, port);
                    addedInitiators.add(initiator);
                } else {
                    initiator = getOrCreateInitiator(targetHost.getId(), oldInitiators, port);
                }
                discoverInitiator(targetHost, initiator,
                        (HostFibreChannelHba) hba);
            } else if (hba instanceof HostInternetScsiHba) {
                String iqn = ((HostInternetScsiHba) hba).getIScsiName();
                Initiator initiator;
                if (findInitiatorByPort(oldInitiators, iqn) == null) {
                    initiator = getOrCreateInitiator(targetHost.getId(), oldInitiators, iqn);
                    addedInitiators.add(initiator);
                } else {
                    initiator = getOrCreateInitiator(targetHost.getId(), oldInitiators, iqn);
                }
                discoverInitiator(targetHost, initiator,
                        (HostInternetScsiHba) hba);
            }
        }

        if (!oldInitiators.isEmpty()) {
            clearScaleIOInitiators(oldInitiators);
        }
    }

    /**
     * Get version of host
     * 
     * @param host
     *            {@link Host} being discovered
     * @return
     */
    protected EsxVersion getVersion(Host host) {
        EsxVersion esxVersion = null;
        VCenterAPI api = createVCenterAPI(host);
        try {
            esxVersion = api.getEsxVersion();
        } finally {
            api.logout();
        }
        return esxVersion;
    }

    /**
     * Lookup for host in the db by uuid
     * 
     * @param uuid
     *            - uuid of host
     * @return
     */
    protected Host findHostByUuid(String uuid) {
        return getModelClient().hosts().findByUuid(uuid);
    }

    /**
     * Find an existing host with matching label or ip address
     * 
     * @param hostSystem
     *            the host system to use
     * @return host that has a matching label or ip address, null if can't be
     *         found
     */
    protected Host findExistingHost(HostSystem hostSystem) {
        List<Host> hosts = CustomQueryUtility.queryActiveResourcesByConstraint(
                dbClient, Host.class, PrefixConstraint.Factory
                        .getFullMatchConstraint(Host.class, "label",
                                hostSystem.getName()));
        for (Host host : hosts) {
            if (isEsxOtherOrNoOsHost(host)) {
                return host;
            }
        }

        List<Host> results = CustomQueryUtility.queryActiveResourcesByAltId(
                dbClient, Host.class, "hostName", hostSystem.getName());
        for (Host host : results) {
            if (isEsxOtherOrNoOsHost(host)) {
                return host;
            }
        }

        List<String> ipAddresses = getHostIpAddresses(hostSystem);
        for (String ipAddress : ipAddresses) {
            hosts = CustomQueryUtility.queryActiveResourcesByConstraint(
                    dbClient, Host.class, PrefixConstraint.Factory
                            .getFullMatchConstraint(Host.class, "label",
                                    ipAddress));
            for (Host host : hosts) {
                if (isEsxOtherOrNoOsHost(host)) {
                    return host;
                }
            }
        }

        return null;
    }

    /**
     * Finds a matching value in the DB by label, or creates one if none is
     * found. If a match is found in the list, it will be removed from the list
     * before returning.
     * 
     * @param hosts
     *            - host list
     * @param name
     * @return
     */
    protected Host getOrCreateHost(List<Host> hosts, String name) {
        return getOrCreate(Host.class, hosts, name);
    }

    /**
     * Returns true if the host is of type Esx, Other, or No OS
     * 
     * @param host
     *            host to check the type
     * @return true if Esx, Other, or No OS, otherwise false
     */
    private boolean isEsxOtherOrNoOsHost(Host host) {
        return StringUtils.equalsIgnoreCase(host.getType(),
                HostType.Esx.toString())
                || StringUtils.equalsIgnoreCase(host.getType(),
                        HostType.Other.toString())
                || StringUtils.equalsIgnoreCase(host.getType(),
                        HostType.No_OS.toString());
    }

    /**
     * Get list of IP addresses for the given host
     * 
     * @param hostSystem
     *            {@link HostSystem} vi sdk MO
     * @return
     */
    private List<String> getHostIpAddresses(HostSystem hostSystem) {
        List<String> ipAddresses = Lists.newArrayList();
        for (HostVirtualNic vnic : getNics(hostSystem)) {
            if (vnic.getSpec() != null && vnic.getSpec().getIp() != null) {
                String ipAddress = vnic.getSpec().getIp().getIpAddress();
                if (!StringUtils.isEmpty(ipAddress)) {
                    ipAddresses.add(ipAddress);
                }
            }
        }
        return ipAddresses;
    }

    /**
     * Fetch Nics for the hostsystem
     * 
     * @param hostSystem
     *            - {@link HostSystem} vi sdk MO
     * @return
     */
    protected List<HostVirtualNic> getNics(HostSystem hostSystem) {
        List<HostVirtualNic> nics = Lists.newArrayList();
        HostConfigInfo config = hostSystem.getConfig();
        if ((config != null) && (config.getNetwork() != null)
                && (config.getNetwork().getVnic() != null)) {
            for (HostVirtualNic nic : config.getNetwork().getVnic()) {
                nics.add(nic);
            }
        }
        return nics;
    }

    /**
     * Find the connection state of the hostsystem
     * 
     * @param source
     *            - {@link HostSystem} vi sdk MO
     * @return
     */
    protected HostSystemConnectionState getConnectionState(HostSystem source) {
        HostRuntimeInfo runtime = source.getRuntime();
        HostSystemConnectionState connectionState = (runtime != null) ? runtime
                .getConnectionState() : null;
        return connectionState;
    }

    /**
     * Fetches the {@link IpInterface} for the {@link Host}
     * 
     * @param host
     *            {@link Host}
     * @return
     */
    protected Iterable<IpInterface> getIpInterfaces(Host host) {
        return getModelClient().ipInterfaces().findByHost(host.getId(), true);
    }

    /**
     * Check if {@link HostVirtualNic} is Ip4 interface
     * 
     * @param nic
     *            - {@link HostVirtualNic}
     * @return
     */
    protected boolean isIp4Interface(HostVirtualNic nic) {
        return (nic.getSpec() != null) && (nic.getSpec().getIp() != null);
    }

    /**
     * Check if {@link HostVirtualNic} is Ip6 interface
     * 
     * @param nic
     *            - {@link HostVirtualNic}
     * @return
     */
    protected boolean isIp6Interface(HostVirtualNic nic) {
        if ((nic.getSpec() != null) && (nic.getSpec().getIp() != null)) {
            return getIp6Address(nic) != null;
        }
        return false;
    }

    /**
     * Discovery of Ip interface
     * 
     * @param host
     *            {@linkk Host}
     * @param ipInterface
     *            {@link IpInterface}
     * @param nic
     *            {@link HostVirtualNic}
     */
    protected void discoverIp4Interface(Host host, IpInterface ipInterface,
            HostVirtualNic nic) {
        setHostInterfaceRegistrationStatus(ipInterface, host);
        ipInterface.setHost(host.getId());
        ipInterface.setProtocol(Protocol.IPV4.name());
        ipInterface.setIpAddress(nic.getSpec().getIp().getIpAddress());
        ipInterface.setNetmask(nic.getSpec().getIp().getSubnetMask());
        ipInterface.setIsManualCreation(false);
        save(ipInterface);
    }

    /**
     * Discovery of Ip interface
     * 
     * @param host
     *            {@linkk Host}
     * @param ipInterface
     *            {@link IpInterface}
     * @param nic
     *            {@link HostVirtualNic}
     */
    protected void discoverIp6Interface(Host host, IpInterface ipInterface,
            HostVirtualNic nic) {
        HostIpConfigIpV6Address config = getIp6Address(nic);
        setHostInterfaceRegistrationStatus(ipInterface, host);
        ipInterface.setHost(host.getId());
        ipInterface.setProtocol(Protocol.IPV6.name());
        ipInterface.setIpAddress(config.getIpAddress());
        ipInterface.setPrefixLength(config.getPrefixLength());
        ipInterface.setIsManualCreation(false);
        save(ipInterface);
    }

    /**
     * Fetch the IPv6 address of {@link HostVirtualNic}
     * 
     * @param nic
     *            {@link HostVirtualNic}
     * @return
     */
    protected HostIpConfigIpV6Address getIp6Address(HostVirtualNic nic) {
        HostIpConfigIpV6AddressConfiguration ip6Config = nic.getSpec().getIp()
                .getIpV6Config();
        if (ip6Config != null) {
            HostIpConfigIpV6Address[] address = ip6Config.getIpV6Address();
            if ((address != null) && (address.length == 1)) {
                return address[0];
            }
        }
        return null;
    }

    /**
     * Finds a matching value in the list of IpInterfaces by ipAddress, or
     * creates one if none is found. If a match is found in the list, it will be
     * removed from the list before returning.
     * 
     * @param ipInterfaces
     * @param ip
     * @return
     */
    protected IpInterface getOrCreateIpInterface(
            List<IpInterface> ipInterfaces, String ip) {
        return getOrCreateInterfaceByIp(ipInterfaces, ip);
    }

    /**
     * Fetch initiators corresponding the {@link Host}
     * 
     * @param host
     *            {@link Host}
     * @return
     */
    protected Iterable<Initiator> getInitiators(Host host) {
        return getModelClient().initiators().findByHost(host.getId(), true);
    }

    /**
     * Fetch {@link HostHostBusAdapter} corresponding the {@link HostSystem}
     * 
     * @param host
     *            {@link HostSystem}
     * @return
     */
    protected List<HostHostBusAdapter> getHostBusAdapters(HostSystem host) {
        List<HostHostBusAdapter> hostBusAdapters = Lists.newArrayList();
        HostStorageAPI storageAPI = new HostStorageAPI(host);
        for (HostHostBusAdapter hba : storageAPI.listHostBusAdapters()) {
            hostBusAdapters.add(hba);
        }
        return hostBusAdapters;
    }

    /**
     * Discover FC Initiator
     * 
     * @param host
     *            {@link Host}
     * @param initiator
     *            {@link Initiator}
     * @param hba
     *            {@link HostFibreChannelHba}
     */
    protected void discoverInitiator(Host host, Initiator initiator,
            HostFibreChannelHba hba) {
        setInitiatorHost(initiator, host);
        initiator.setProtocol(Protocol.FC.name());
        initiator
                .setInitiatorNode(SanUtils.normalizeWWN(hba.nodeWorldWideName));
        initiator
                .setInitiatorPort(SanUtils.normalizeWWN(hba.portWorldWideName));
        initiator.setIsManualCreation(false);
        initiator.setLabel(SanUtils.normalizeWWN(hba.portWorldWideName));
        save(initiator);
    }

    /**
     * Discover Scsi Initiator
     * 
     * @param host
     *            {@link Host}
     * @param initiator
     *            {@link Initiator}
     * @param hba
     *            {@link HostInternetScsiHba}
     */
    protected void discoverInitiator(Host host, Initiator initiator,
            HostInternetScsiHba hba) {
        setInitiatorHost(initiator, host);
        initiator.setProtocol(Protocol.iSCSI.name());
        initiator.setInitiatorNode("");
        initiator.setInitiatorPort(hba.getIScsiName());
        initiator.setIsManualCreation(false);
        initiator.setLabel(hba.getIScsiName());
        save(initiator);
    }

    /**
     * Sets properties pertaining to the {@link Initiator}
     * 
     * @param initiator
     *            {@link Initiator}
     * @param host
     *            {@link Host}
     */
    protected void setInitiatorHost(Initiator initiator, Host host) {
        setHostInterfaceRegistrationStatus(initiator, host);
        initiator.setHost(host.getId());
        initiator.setHostName(host.getHostName());
    }

    @Override
    protected void discoverIpInterfaces(Host host,
            List<IpInterface> oldIpInterfaces) {
        // Do nothing, for ESX host ip interfaces are discovered differently

    }

    @Override
    protected void discoverInitiators(Host host, List<Initiator> oldInitiators,
            HostStateChange changes) {
        // Do nothing, for ESX host Initiators are discovered differently

    }

    @Override
    protected void setNativeGuid(Host host) {
        // TODO Auto-generated method stub

    }
}
