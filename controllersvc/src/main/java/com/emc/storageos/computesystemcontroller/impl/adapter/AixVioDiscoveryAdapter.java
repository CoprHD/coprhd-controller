/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl.adapter;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.emc.aix.AixVioCLI;
import com.emc.aix.model.AixVersion;
import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.HostType;
import com.emc.storageos.db.client.model.HostInterface.Protocol;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.util.SanUtils;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.iwave.ext.command.CommandException;
import com.iwave.ext.linux.model.HBAInfo;

@Component
public class AixVioDiscoveryAdapter extends AbstractHostDiscoveryAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(AixVioDiscoveryAdapter.class);
    private static final String ENT0 = "ent0";

    @Override
    protected String getSupportedType() {
        return HostType.AIXVIO.name();
    }

    @Override
    protected void discoverHost(Host host, HostStateChange changes) {
        validateHost(host);
        AixVersion version = getVersion(host);
        host.setOsVersion(version.toString());

        if (getVersionValidator().isValidAixVioVersion(version)) {
            host.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
            save(host);
            super.discoverHost(host, changes);
        }
        else {
            host.setCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.name());
            save(host);
            throw ComputeSystemControllerException.exceptions.incompatibleHostVersion(
                    getSupportedType(), version.toString(),
                    getVersionValidator().getAixVioMinimumVersion(false).toString()); 
        }
    }
    
    protected void validateHost(Host host) {
        getCli(host).executeCommand("pwd");
    }
    
    private AixVioCLI getCli(Host vio) {
        return new AixVioCLI(vio.getHostName(), vio.getPortNumber(), vio.getUsername(), vio.getPassword());
    }

    public AixVersion getVersion(Host vio) {
        return getCli(vio).getVersion();
    }
    
	public void setDbCLient(DbClient dbClient) {
		super.setDbClient(dbClient);
	}

    @Override
    protected void discoverIpInterfaces(Host host,
            List<IpInterface> oldIpInterfaces) {
        // Unable to discover IP interfaces on AIX VIO
        
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
    protected void discoverInitiators(Host host, List<Initiator> oldInitiators, HostStateChange changes) {
        AixVioCLI cli = getCli(host);
        List<Initiator> addedInitiators = Lists.newArrayList();
        
        try {
            for (HBAInfo hba : cli.listInitiators()) {
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
        }
        catch (Exception e) {
            LOG.error("Failed to list FC Ports, skipping");
        }

        try {
            for (String iqn : cli.listIQNs()) {
                Initiator initiator;
                if (findInitiatorByPort(oldInitiators, iqn) == null) {
                    initiator = getOrCreateInitiator(oldInitiators, iqn);
                    addedInitiators.add(initiator);
                } else {
                    initiator = getOrCreateInitiator(oldInitiators, iqn);
                }
                discoverISCSIInitiator(host, initiator, iqn);
            }
        }
        catch (Exception e) {
            LOG.error("Failed to list iSCSI Ports, skipping");
        }
        // update export groups with new initiators if host is in use.
        if (!addedInitiators.isEmpty()) {
            Collection<URI> addedInitiatorIds = Lists.newArrayList(Collections2.transform(addedInitiators, CommonTransformerFunctions.fctnDataObjectToID()));
            changes.setNewInitiators(addedInitiatorIds);
        }
    }

    @Override
    protected void setNativeGuid(Host host) {
        AixVioCLI cli = getCli(host);
        try {
            String macAddress = cli.getNetworkAdapterMacAddress(ENT0);
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
