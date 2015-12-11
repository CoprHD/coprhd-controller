/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl.adapter;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.computesystemcontroller.impl.DiscoveryStatusUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public abstract class AbstractHostDiscoveryAdapter extends AbstractDiscoveryAdapter {
    protected abstract String getSupportedType();

    @Override
    public boolean isSupportedTarget(String targetId) {
        if (URIUtil.isType(URI.create(targetId), Host.class)) {
            Host host = getModelClient().hosts().findById(targetId);
            return isSupportedHost(host) && isDiscoverable(host);
        }
        else {
            return false;
        }
    }

    @Override
    public void discoveryFailure(DiscoveredSystemObject target, String compatibilityStatus, String errorMessage) {
        target.setCompatibilityStatus(compatibilityStatus);
        target.setLastDiscoveryStatusMessage(errorMessage);
        save(target);
    }

    @Override
    public void discoverTarget(String targetId) {
        Host host = getModelClient().hosts().findById(targetId);
        HostStateChange changes = new HostStateChange(host, host.getCluster());
        discoverHost(host, changes);
        processHostChanges(changes);
    }

    protected boolean isSupportedHost(Host host) {
        return StringUtils.equals(host.getType(), getSupportedType());
    }

    protected boolean isDiscoverable(Host host) {
        return host.getDiscoverable() == null || host.getDiscoverable();
    }

    protected void discoverHost(Host host, HostStateChange changes) {
        setNativeGuid(host);

        List<IpInterface> oldIpInterfaces = new ArrayList<IpInterface>();
        Iterables.addAll(oldIpInterfaces, getIpInterfaces(host));
        discoverIpInterfaces(host, oldIpInterfaces);
        removeDiscoveredInterfaces(oldIpInterfaces);

        List<Initiator> oldInitiators = new ArrayList<Initiator>();
        Iterables.addAll(oldInitiators, getInitiators(host));
        discoverInitiators(host, oldInitiators, changes);

        if (!oldInitiators.isEmpty()) {
            clearScaleIOInitiators(oldInitiators);
        }

        Collection<URI> oldInitiatorIds = Lists.newArrayList(Collections2.transform(oldInitiators,
                CommonTransformerFunctions.fctnDataObjectToID()));
        changes.setOldInitiators(oldInitiatorIds);
    }

    /**
     * Discovers the IP interfaces for the host. The current IP interfaces are supplied and should be reused where
     * appropriate. When an IpInterface is reused, it should be removed from this list. Any remaining in the list after
     * this method are assumed to not exist on the host any longer and will be deleted.
     * 
     * @param host
     *            the host.
     * @param oldIpInterfaces
     *            the old IP interfaces for the host, any remaining after this method will be deleted.
     */
    protected abstract void discoverIpInterfaces(Host host, List<IpInterface> oldIpInterfaces);

    /**
     * Discovers the initiators for the host. The current initiators are supplied and should be reused where
     * appropriate. When reusing an initiator, it should be removed from this list. Any remaining in the list after this
     * method are assumed to not exist on the host any longer and will be deleted.
     * 
     * @param host
     *            the host.
     * @param oldInitiators
     *            the old initiators for the host, any remaining after this method will be deleted.
     * @param changes
     *            the state of changes for the host during discovery
     */
    protected abstract void discoverInitiators(Host host, List<Initiator> oldInitiators, HostStateChange changes);

    protected Iterable<IpInterface> getIpInterfaces(Host host) {
        return getModelClient().ipInterfaces().findByHost(host.getId(), true);
    }

    /**
     * Gets or creates an IP interface. If an IP interface is found in the list, it will be removed from the list before
     * returning.
     * 
     * @param ipInterfaces
     *            the IP interfaces.
     * @param ip
     *            the name of the interface.
     * @return the IP interface.
     */
    protected IpInterface getOrCreateIpInterface(List<IpInterface> ipInterfaces, String ip) {
        return getOrCreateInterfaceByIp(ipInterfaces, ip);
    }

    protected Iterable<Initiator> getInitiators(Host host) {
        return getModelClient().initiators().findByHost(host.getId(), true);
    }

    /**
     * Sets the host/cluster values for the initiator.
     * 
     * @param initiator
     *            the initiator.
     * @param host
     *            the host.
     */
    protected void setInitiator(Initiator initiator, Host host) {
        initiator.setHost(host.getId());
        initiator.setHostName(host.getHostName());
        Cluster cluster = getCluster(host);
        initiator.setClusterName(cluster != null ? cluster.getLabel() : "");
    }

    /**
     * Gets the cluster for the given host.
     * 
     * @param host
     *            the host
     * @return the cluster.
     */
    protected Cluster getCluster(Host host) {
        if (host.getCluster() != null) {
            return get(Cluster.class, host.getCluster());
        }
        else {
            return null;
        }
    }

    /**
     * Finds cluster by IP addresses in the given tenant.
     * 
     * @param tenantId
     *            the tenant ID.
     * @param hostType
     *            the host type.
     * @param clusterIpAddresses
     *            the IP addresses in the cluster.
     * @return the ID of the cluster.
     */
    protected URI findClusterByAddresses(URI tenantId, Host.HostType hostType, List<String> clusterIpAddresses) {
        for (Host host : getModelClient().hosts().findAll(tenantId.toString(), true)) {
            // Skip unclustered hosts
            if (NullColumnValueGetter.isNullURI(host.getCluster())) {
                continue;
            }
            // Skip hosts of other types
            if (!StringUtils.equals(host.getType(), hostType.toString())) {
                continue;
            }

            try {
                InetAddress hostAddress = Inet4Address.getByName(host.getHostName());
                if (clusterIpAddresses.contains(hostAddress.getHostAddress())) {
                    return host.getCluster();
                }
            } catch (UnknownHostException ignore) {
                getLog().error(ignore.getMessage(), ignore);
            }

            // Check the ip interfaces for the host
            for (IpInterface ipInterface : getModelClient().ipInterfaces().findByHost(host.getId(), true)) {
                if (clusterIpAddresses.contains(ipInterface.getIpAddress())) {
                    return host.getCluster();
                }
            }
        }
        return null;
    }

    /**
     * Finds a cluster by name in the given tenant.
     * 
     * @param tenantId
     *            the tenant ID.
     * @param clusterName
     *            the name of the cluster.
     * @return the ID of the cluster.
     */
    protected URI findClusterByName(URI tenantId, String clusterName) {
        for (Cluster cluster : getModelClient().clusters().findByLabel(tenantId.toString(), clusterName, true)) {
            if (cluster.getLabel().equals(clusterName)) {
                return cluster.getId();
            }
        }
        return null;
    }

    protected URI createNewCluster(URI tenant, String clusterName) {
        Cluster cluster = new Cluster();
        cluster.setLabel(clusterName);
        cluster.setTenant(tenant);

        getModelClient().save(cluster);

        return cluster.getId();
    }

    protected abstract void setNativeGuid(Host host);

    protected void checkDuplicateHost(Host host, String nativeGuid) {
        if (nativeGuid != null && !nativeGuid.isEmpty()) {
            for (Host existingHost : modelClient.hosts().findByNativeGuid(nativeGuid, true)) {
                if (!existingHost.getId().equals(host.getId())) {
                    ComputeSystemControllerException ex =
                            ComputeSystemControllerException.exceptions.duplicateSystem("Host", existingHost.getLabel());
                    DiscoveryStatusUtils.markAsFailed(modelClient, host, ex.getMessage(), ex);
                    throw ex;
                }
            }
        }
    }

    /**
     * This method clears/removes ScaleIO initiators
     * 
     * @param initiators
     *            list of initiators
     */
    protected void clearScaleIOInitiators(List<Initiator> initiators) {
        Iterator<Initiator> iterator = initiators.iterator();
        while (iterator.hasNext()) {
            Initiator initiator = iterator.next();
            if (StringUtils.equalsIgnoreCase(initiator.getProtocol(),
                    Protocol.ScaleIO.name())) {
                iterator.remove();
            }
        }
    }

}
