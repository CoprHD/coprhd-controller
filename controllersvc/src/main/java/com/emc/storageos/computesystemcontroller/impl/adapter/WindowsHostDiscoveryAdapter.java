/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl.adapter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.iwave.ext.windows.WindowsClusterUtils;
import com.iwave.ext.windows.model.wmi.*;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.HostType;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.KerberosUtil;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.iwave.ext.windows.WindowsSystemWinRM;
import com.iwave.ext.windows.winrm.WinRMException;
import com.iwave.ext.windows.winrm.WinRMSoapException;
import com.iwave.ext.windows.winrm.WinRMTarget;

@Component
public class WindowsHostDiscoveryAdapter extends AbstractHostDiscoveryAdapter {

    @Override
    protected String getSupportedType() {
        return HostType.Windows.name();
    }

    protected void init() {
        List<AuthnProvider> authProviders = new ArrayList<AuthnProvider>();
        Iterables.addAll(authProviders, getModelClient().of(AuthnProvider.class).findAll(true));
        KerberosUtil.initializeKerberos(authProviders);
    }

    @Override
    protected void discoverHost(Host host, HostStateChange changes) {
        init();

        WindowsVersion version = getVersion(host);
        host.setOsVersion(version.toString());

        if (getVersionValidator().isValidWindowsVersion(version)) {
            URI cluster = findCluster(host);
            changes.setOldCluster(host.getCluster());
            changes.setNewCluster(cluster);
            host.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
            save(host);
            super.discoverHost(host, changes);
        }
        else {
            host.setCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.name());
            save(host);
            throw ComputeSystemControllerException.exceptions.incompatibleHostVersion(
                    getSupportedType(), version.toString(), getVersionValidator().getWindowsMinimumVersion(false).toString());
        }
    }

    protected URI findCluster(Host host) {
        WindowsSystemWinRM system = createWindowsSystem(host);

        try {
            if (system.isClustered()) {
                Map<String, List<MSClusterNetworkInterface>> clusterToNetworkInterfaces = system.getClusterToNetworkInterfaces();
                String clusterName = WindowsClusterUtils.findWindowsClusterHostIsIn(host.getHostName(), clusterToNetworkInterfaces);
                if (clusterName == null) {
                    if (clusterToNetworkInterfaces.size() == 1) {
                        clusterName = clusterToNetworkInterfaces.keySet().iterator().next();
                    } else if (clusterToNetworkInterfaces.isEmpty()) {
                        warn("Host '%s' appears to be clustered, but cannot find any cluster interfaces",
                                host.getHostName());
                        return NullColumnValueGetter.getNullURI();
                    } else {
                        warn("Host '%s' is configured in multiple clusters %s, cannot determine primary cluster by network interface",
                                host.getHostName(), clusterToNetworkInterfaces.keySet());
                        return NullColumnValueGetter.getNullURI();
                    }
                }

                List<String> clusterIpAddresses = WindowsClusterUtils
                        .getClusterIpAddresses(clusterToNetworkInterfaces.get(clusterName));

                // Find the cluster by address
                URI cluster = findClusterByAddresses(host, host.getTenant(), HostType.Windows, clusterIpAddresses);
                if (cluster != null) {
                    updateClusterName(cluster, clusterName);
                    return cluster;
                }

                // Find the cluster by name
                cluster = findClusterByName(host.getTenant(), clusterName);
                if (cluster != null) {
                    // Ensure the cluster is empty before using it or if host already belongs to this cluster
                    if (Iterables.isEmpty(getModelClient().hosts().findByCluster(cluster, true))
                            || (!NullColumnValueGetter.isNullURI(host.getCluster())
                                    && host.getCluster().toString().equals(cluster.toString()))) {
                        updateClusterName(cluster, clusterName);
                        return cluster;
                    }
                    // Log a warning
                    warn("Host '%s' is in a cluster named '%s' which could not be matched by address. "
                            + "An existing non-empty ViPR cluster exists with the same name; "
                            + "manual cluster assignment required", host.getHostName(), clusterName);
                    return NullColumnValueGetter.getNullURI();
                }

                // No cluster matched by address or name, create a new one
                return createNewCluster(host.getTenant(), clusterName);
            }

            // Host is not currently in a Windows Cluster
            return NullColumnValueGetter.getNullURI();
        } catch (WinRMException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateClusterName(URI clusterId, String name) {
        Cluster cluster = dbClient.queryObject(Cluster.class, clusterId);
        if (cluster != null) {
            cluster.setLabel(name);
            dbClient.updateObject(cluster);
            ComputeSystemHelper.updateInitiatorClusterName(dbClient, clusterId);
        }
    }

    protected WindowsVersion getVersion(Host host) {
        WindowsSystemWinRM system = createWindowsSystem(host);
        try {
            WindowsVersion version = system.getVersion();
            if (version == null) {
                version = new WindowsVersion("-");
            }
            return version;
        } catch (WinRMException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void discoverInitiators(Host host, List<Initiator> oldInitiators, HostStateChange changes) {
        WindowsSystemWinRM windows = createWindowsSystem(host);
        List<Initiator> addedInitiators = new ArrayList<Initiator>();

        try {
            for (FibreChannelHBA hba : windows.listFibreChannelHBAs()) {
                Initiator initiator;
                if (findInitiatorByPort(oldInitiators, hba.getPortWWN()) == null) {
                    initiator = getOrCreateInitiator(host.getId(), oldInitiators, hba.getPortWWN());
                    addedInitiators.add(initiator);
                } else {
                    initiator = getOrCreateInitiator(host.getId(), oldInitiators, hba.getPortWWN());
                }
                discoverFCInitiator(host, initiator, hba);
            }
        } catch (WinRMSoapException e) {
            info("Could not retrieve fibre channel HBAs: %s", e.getMessage());
            clearInitiators(oldInitiators, Protocol.FC.name());
        } catch (WinRMException e) {
            warn(e, "Error while retrieving fibre channel HBAs: %s", e.getMessage());
            clearInitiators(oldInitiators, Protocol.FC.name());
        }
        try {
            for (String iqn : windows.listIScsiInitiators()) {
                Initiator initiator;
                if (findInitiatorByPort(oldInitiators, iqn) == null) {
                    initiator = getOrCreateInitiator(host.getId(), oldInitiators, iqn);
                    addedInitiators.add(initiator);
                } else {
                    initiator = getOrCreateInitiator(host.getId(), oldInitiators, iqn);
                }
                discoverISCSIInitiator(host, initiator, iqn);
            }
        } catch (WinRMSoapException e) {
            info("Could not retrieve iSCSI interfaces: %s", e.getMessage());
            clearInitiators(oldInitiators, Protocol.iSCSI.name());
        } catch (WinRMException e) {
            warn(e, "Error while retrieving iSCSI interfaces: %s", e.getMessage());
            clearInitiators(oldInitiators, Protocol.iSCSI.name());
        }

        // update export groups with new initiators if host is in use.
        if (!addedInitiators.isEmpty()) {
            Collection<URI> addedInitiatorIds = Lists.newArrayList(Collections2.transform(addedInitiators,
                    CommonTransformerFunctions.fctnDataObjectToID()));
            changes.setNewInitiators(addedInitiatorIds);
        }
    }

    private void discoverFCInitiator(Host host, Initiator initiator, FibreChannelHBA hba) {
        setInitiator(initiator, host);
        initiator.setProtocol(Protocol.FC.name());
        initiator.setInitiatorNode(hba.getNodeWWN());
        initiator.setInitiatorPort(hba.getPortWWN());
        setHostInterfaceRegistrationStatus(initiator, host);
        save(initiator);
    }

    private void discoverISCSIInitiator(Host host, Initiator initiator, String iqn) {
        setInitiator(initiator, host);
        initiator.setInitiatorNode("");
        initiator.setInitiatorPort(iqn);
        initiator.setProtocol(Protocol.iSCSI.name());
        setHostInterfaceRegistrationStatus(initiator, host);
        save(initiator);
    }

    @Override
    protected void discoverIpInterfaces(Host host, List<IpInterface> oldIpInterfaces) {
        WindowsSystemWinRM windows = createWindowsSystem(host);

        try {
            for (NetworkAdapter adapter : windows.listNetworkAdapters()) {
                if (StringUtils.isNotBlank(adapter.getIpAddress())) {
                    IpInterface ipInterface = getOrCreateIpInterface(oldIpInterfaces, adapter.getIpAddress());
                    discoverIp4Interface(host, ipInterface, adapter);
                }
                if (StringUtils.isNotBlank(adapter.getIp6Address())) {
                    IpInterface ipInterface = getOrCreateIpInterface(oldIpInterfaces, adapter.getIp6Address());
                    discoverIp6Interface(host, ipInterface, adapter);
                }
            }
        } catch (WinRMException e) {
            warn(e, "Error while retrieving IP interfaces: %s", e.getMessage());
            oldIpInterfaces.clear();
        }
    }

    private void discoverIp4Interface(Host host, IpInterface ipInterface, NetworkAdapter adapter) {
        ipInterface.setHost(host.getId());
        ipInterface.setProtocol(Protocol.IPV4.name());
        ipInterface.setIpAddress(adapter.getIpAddress());
        ipInterface.setNetmask(adapter.getSubnetMask());
        ipInterface.setIsManualCreation(false);
        setHostInterfaceRegistrationStatus(ipInterface, host);
        save(ipInterface);
    }

    private void discoverIp6Interface(Host host, IpInterface ipInterface, NetworkAdapter adapter) {
        ipInterface.setHost(host.getId());
        ipInterface.setProtocol(Protocol.IPV6.name());
        // TODO parse ip6 address?
        ipInterface.setIpAddress(adapter.getIp6Address());
        ipInterface.setIsManualCreation(false);
        setHostInterfaceRegistrationStatus(ipInterface, host);
        save(ipInterface);
    }

    public static WindowsSystemWinRM createWindowsSystem(Host host) {
        boolean ssl = (host.getUseSSL() != null) ? host.getUseSSL() : false;
        int port = (host.getPortNumber() != null) ? host.getPortNumber() : (ssl ? WinRMTarget.DEFAULT_HTTPS_PORT
                : WinRMTarget.DEFAULT_HTTP_PORT);
        WinRMTarget target = new WinRMTarget(host.getHostName(), port, ssl, host.getUsername(), host.getPassword());
        return new WindowsSystemWinRM(target);
    }

    @Override
    public String getErrorMessage(Throwable t) {
        Throwable rootCause = getRootCause(t);
        if (rootCause instanceof WinRMException) {
            if (StringUtils.equals("Authentication Failed", rootCause.getMessage())) {
                return "Login failed, invalid username or password";
            }
        }

        return super.getErrorMessage(t);
    }

    public void setDbCLient(DbClient dbClient) {
        super.setDbClient(dbClient);
    }

    /**
     * Gets the primary network adapter by returning adapter with lowest index (name)
     * 
     * @param adapters
     * @return network adapter
     */
    private NetworkAdapter getPrimaryNetworkAdapter(List<NetworkAdapter> adapters) {
        if (adapters.isEmpty()) {
            return null;
        }
        Collections.sort(adapters, new Comparator<NetworkAdapter>() {
            @Override
            public int compare(NetworkAdapter na1, NetworkAdapter na2) {
                return na1.getName().compareTo(na2.getName());
            }
        });
        return adapters.get(0);
    }

    /**
     * Removes initiators from list that have the given protocol
     * 
     * @param initiators list of initiators
     * @param protocol protocol to compare and remove
     */
    private void clearInitiators(List<Initiator> initiators, String protocol) {
        Iterator<Initiator> iterator = initiators.iterator();
        while (iterator.hasNext()) {
            Initiator initiator = iterator.next();
            if (StringUtils.equals(initiator.getProtocol(), protocol)) {
                iterator.remove();
            }
        }
    }

    @Override
    protected void setNativeGuid(Host host) {
        WindowsSystemWinRM windows = createWindowsSystem(host);

        try {
            NetworkAdapter adapter = getPrimaryNetworkAdapter(windows.listNetworkAdapters());
            if (adapter != null && !host.getNativeGuid().equalsIgnoreCase(adapter.getMacAddress())) {
                checkDuplicateHost(host, adapter.getMacAddress());
                info("Setting nativeGuid for " + host.getId() + " as " + adapter.getMacAddress());
                host.setNativeGuid(adapter.getMacAddress());
                save(host);
            }
        } catch (WinRMException e) {
            throw new RuntimeException(e);
        }
    }
}
