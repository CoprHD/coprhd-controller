/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl.adapter;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.emc.aix.AixSystem;
import com.emc.aix.model.AixVersion;
import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ControlStation;
import com.emc.storageos.db.client.model.ControlStation.ControlStationType;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.util.SanUtils;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.iwave.ext.command.CommandException;
import com.iwave.ext.linux.model.HBAInfo;
import com.iwave.ext.linux.model.IPInterface;
import com.jcraft.jsch.JSchException;

@Component
public class HMCControlStationDiscoveryAdapter extends AbstractHostDiscoveryAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(HMCControlStationDiscoveryAdapter.class);

    private static final String ENT0 = "ent0";

    @Override
    protected String getSupportedType() {
        return ControlStationType.HMC.name();
    }

    @Override
    protected void discoverHost(Host host, HostStateChange changes) {
        validateHost(host);
        AixVersion version = getVersion(host);
        host.setOsVersion(version.toString());

        if (getVersionValidator().isValidAixVersion(version)) {
            host.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
            save(host);
            super.discoverHost(host, changes);
        } else {
            host.setCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.name());
            save(host);
            throw ComputeSystemControllerException.exceptions.incompatibleHostVersion(
                    getSupportedType(), version.toString(),
                    getVersionValidator().getAixMinimumVersion(false).toString());
        }
    }

    @Override
    public String getErrorMessage(Throwable t) {
        Throwable rootCause = getRootCause(t);
        if (rootCause instanceof JSchException) {
            if (StringUtils.equals("Auth fail", rootCause.getMessage())) {
                return "Login failed, invalid username or password";
            }
        }
        return super.getErrorMessage(t);
    }

    protected void validateHost(ControlStation cs) {
        getCli(cs).executeCommand("pwd");
    }

    protected AixVersion getVersion(Host host) {
        AixSystem cli = getCli(host);
        AixVersion version = cli.getVersion();
        if (version == null) {
            error("Could not determine version of aix host %s", host.getLabel());
            return new AixVersion("");
        } else {
            return version;
        }
    }

    private AixSystem getCli(Host vio) {
        return new AixSystem(vio.getHostName(), vio.getPortNumber(), vio.getUsername(), vio.getPassword());
    }

    @Override
    protected void discoverInitiators(Host host, List<Initiator> oldInitiators, HostStateChange changes) {
        AixSystem aix = getCli(host);
        List<Initiator> addedInitiators = Lists.newArrayList();

        try {
            for (HBAInfo hba : aix.listInitiators()) {
                Initiator initiator;
                String wwpn = SanUtils.normalizeWWN(hba.getWwpn());
                if (findInitiatorByPort(oldInitiators, wwpn) == null) {
                    initiator = getOrCreateInitiator(oldInitiators, wwpn);
                    addedInitiators.add(initiator);
                } else {
                    initiator = getOrCreateInitiator(oldInitiators, wwpn);
                }
                discoverFCInitiator(host, initiator, hba);
            }
        } catch (Exception e) {
            LOG.error("Failed to list FC Ports, skipping");
        }

        try {
            for (String iqn : aix.listIQNs()) {
                Initiator initiator;
                if (findInitiatorByPort(oldInitiators, iqn) == null) {
                    initiator = getOrCreateInitiator(oldInitiators, iqn);
                    addedInitiators.add(initiator);
                } else {
                    initiator = getOrCreateInitiator(oldInitiators, iqn);
                }
                discoverISCSIInitiator(host, initiator, iqn);
            }
        } catch (Exception e) {
            LOG.error("Failed to list iSCSI Ports, skipping");
        }

        // update export groups with new initiators if host is in use.
        if (!addedInitiators.isEmpty()) {
            Collection<URI> addedInitiatorIds = Lists.newArrayList(Collections2.transform(addedInitiators,
                    CommonTransformerFunctions.fctnDataObjectToID()));
            changes.setNewInitiators(addedInitiatorIds);
        }
    }

    private void discoverFCInitiator(Host host, Initiator initiator, HBAInfo hba) {
        setInitiator(initiator, host);
        initiator.setProtocol(Protocol.FC.name());
        initiator.setInitiatorNode(SanUtils.normalizeWWN(hba.getWwnn()));
        initiator.setInitiatorPort(SanUtils.normalizeWWN(hba.getWwpn()));
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
        AixSystem aix = getCli(host);

        for (IPInterface nic : aix.listIPInterfaces()) {
            if (StringUtils.isNotBlank(nic.getIpAddress())) {
                IpInterface ipInterface = getOrCreateIpInterface(oldIpInterfaces, nic.getIpAddress());
                discoverIp4Interface(host, ipInterface, nic);
            }
        }
    }

    private void discoverIp4Interface(Host host, IpInterface ipInterface, IPInterface nic) {
        ipInterface.setHost(host.getId());
        ipInterface.setProtocol(Protocol.IPV4.name());
        ipInterface.setIpAddress(nic.getIpAddress());
        ipInterface.setNetmask(nic.getNetMask());
        ipInterface.setIsManualCreation(false);
        setHostInterfaceRegistrationStatus(ipInterface, host);
        save(ipInterface);
    }

    public void setDbCLient(DbClient dbClient) {
        super.setDbClient(dbClient);
    }

    @Override
    protected void setNativeGuid(Host host) {
        AixSystem aix = getCli(host);
        try {
            String macAddress = aix.getNetworkAdapterMacAddress(ENT0);
            if (macAddress != null && !host.getNativeGuid().equalsIgnoreCase(macAddress)) {
                checkDuplicateHost(host, macAddress);
                info("Setting nativeGuid for " + host.getId() + " as " + macAddress);
                host.setNativeGuid(macAddress);
                save(host);
            }
        } catch (CommandException ex) {
            LOG.warn("Failed to get MAC address of adapter during discovery");
        }
    }
}
