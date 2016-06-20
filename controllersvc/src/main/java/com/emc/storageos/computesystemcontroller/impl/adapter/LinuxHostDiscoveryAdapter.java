/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl.adapter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.emc.storageos.computesystemcontroller.exceptions.CompatibilityException;
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
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.linux.command.MultipathCommand;
import com.iwave.ext.linux.command.MultipathException;
import com.iwave.ext.linux.command.powerpath.PowerPathException;
import com.iwave.ext.linux.command.powerpath.PowermtCheckRegistrationCommand;
import com.iwave.ext.linux.command.version.GetLinuxVersionLSBReleaseCommand;
import com.iwave.ext.linux.command.version.GetRedhatVersionCommandOne;
import com.iwave.ext.linux.command.version.GetRedhatVersionCommandTwo;
import com.iwave.ext.linux.command.version.GetRedhatVersionCommandThree;
import com.iwave.ext.linux.command.version.GetSuSEVersionCommand;
import com.iwave.ext.linux.command.version.LinuxVersionCommand;
import com.iwave.ext.linux.model.HBAInfo;
import com.iwave.ext.linux.model.IPInterface;
import com.iwave.ext.linux.model.LinuxVersion;
import com.iwave.ext.linux.model.LinuxVersion.LinuxDistribution;
import com.jcraft.jsch.JSchException;

@Component
public class LinuxHostDiscoveryAdapter extends AbstractHostDiscoveryAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(LinuxHostDiscoveryAdapter.class);

    private static final String ETH0 = "eth0";

    @Override
    protected String getSupportedType() {
        return HostType.Linux.name();
    }

    @Override
    protected void discoverHost(Host host, HostStateChange changes) {
        validateHost(host);
        List<LinuxVersion> versions = getVersions(host);
        LinuxVersion version = findVersion(versions);
        host.setOsVersion(version.toString());

        if (getVersionValidator().isValidLinuxVersion(version)) {
            host.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
            save(host);
            super.discoverHost(host, changes);
            checkMultipathSoftwareCompatibility(host);
        }
        else {
            host.setCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.name());
            save(host);
            throw ComputeSystemControllerException.exceptions.incompatibleLinuxHostVersion(
                    getSupportedType(), version.toString(), getVersionValidator().getSuSELinuxMinimumVersion(false).toString(),
                    getVersionValidator().getRedhatLinuxMinimumVersion(false).toString());
        }
    }

    /**
     * Returns the first valid version from list of versions.
     * If no valid version is found, returns the first version or unknown version if list is empty.
     * 
     * @param versions list of versions
     * @return valid version or first version if no valid version found
     */
    private LinuxVersion findVersion(List<LinuxVersion> versions) {
        for (LinuxVersion version : versions) {
            if (getVersionValidator().isValidLinuxVersion(version)) {
                return version;
            }
        }
        return !versions.isEmpty() ? versions.get(0) : new LinuxVersion(LinuxDistribution.UNKNOWN, "");
    }

    private void checkMultipathSoftwareCompatibility(Host host) {
        LinuxSystemCLI cli = createLinuxCLI(host);
        String powerpathMessage = null;
        String multipathMessage = null;
        try {
            PowermtCheckRegistrationCommand command = new PowermtCheckRegistrationCommand();
            cli.executeCommand(command);
            // powerpath is installed
            LOG.info("PowerPath is installed");
            return;
        } catch (PowerPathException e) {
            powerpathMessage = e.getMessage();
            LOG.info("PowerPath is unavailable: " + powerpathMessage);
        }

        try {
            MultipathCommand command = new MultipathCommand();
            command.addArgument("-l");
            cli.executeCommand(command);
            // multipath is installed
            LOG.info("Multipath is installed");
            return;
        } catch (MultipathException e) {
            multipathMessage = e.getMessage();
            LOG.info("Multipath is unavailable: " + multipathMessage);
        }
        throw new CompatibilityException("No multipath software available: \n" + powerpathMessage + "\n"
                + multipathMessage);
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

    protected void validateHost(Host host) {
        createLinuxCLI(host).executeCommand("pwd");
    }

    protected List<LinuxVersion> getVersions(Host host) {
        LinuxSystemCLI cli = createLinuxCLI(host);
        List<LinuxVersion> versions = new ArrayList<LinuxVersion>();

        LinuxVersionCommand[] commands = { new GetSuSEVersionCommand(), new GetRedhatVersionCommandOne(),
                new GetRedhatVersionCommandTwo(), new GetRedhatVersionCommandThree(), new GetLinuxVersionLSBReleaseCommand() };

        for (LinuxVersionCommand command : commands) {

            try {
                cli.executeCommand(command);
                LinuxVersion version = command.getResults();
                if (version != null) {
                    versions.add(version);
                }
            } catch (CommandException e) {
                warn("Could not retrieve linux version", e);
            }
        }

        if (versions.isEmpty()) {
            error("Could not determine version of linux host %s", host.getLabel());
            versions.add(new LinuxVersion(LinuxDistribution.UNKNOWN, ""));
        }

        return versions;
    }

    @Override
    protected void discoverInitiators(Host host, List<Initiator> oldInitiators, HostStateChange changes) {
        LinuxSystemCLI linux = createLinuxCLI(host);
        List<Initiator> addedInitiators = Lists.newArrayList();

        try {
            for (HBAInfo hba : linux.listHBAs()) {
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
            for (String iqn : linux.listIQNs()) {
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

        try {
            String cephPseudoPort = String.format("rbd:%s", linux.getMachineId());
            Initiator initiator;
            if (findInitiatorByPort(oldInitiators, cephPseudoPort) == null) {
                initiator = getOrCreateInitiator(oldInitiators, cephPseudoPort);
                addedInitiators.add(initiator);
            } else {
                initiator = getOrCreateInitiator(oldInitiators, cephPseudoPort);
            }
            discoverRBDInitiator(host, initiator, cephPseudoPort);
        } catch (Exception e) {
            LOG.error("Failed to create RBD pseudo port, skipping");
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

    private void discoverRBDInitiator(Host host, Initiator initiator, String port) {
        setInitiator(initiator, host);
        initiator.setInitiatorNode("");
        initiator.setInitiatorPort(port);
        initiator.setProtocol(Protocol.RBD.name());
        setHostInterfaceRegistrationStatus(initiator, host);
        save(initiator);
    }

    @Override
    protected void discoverIpInterfaces(Host host, List<IpInterface> oldIpInterfaces) {
        LinuxSystemCLI linux = createLinuxCLI(host);

        for (IPInterface nic : linux.listIPInterfaces()) {
            if (StringUtils.isNotBlank(nic.getIpAddress())) {
                IpInterface ipInterface = getOrCreateIpInterface(oldIpInterfaces, nic.getIpAddress());
                discoverIp4Interface(host, ipInterface, nic);
            }
            if (StringUtils.isNotBlank(nic.getIP6Address())) {
                IpInterface ipInterface = getOrCreateIpInterface(oldIpInterfaces,
                        StringUtils.substringBefore(nic.getIP6Address(), "/"));
                discoverIp6Interface(host, ipInterface, nic);
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

    private void discoverIp6Interface(Host host, IpInterface ipInterface, IPInterface nic) {
        String ipAddress = StringUtils.substringBefore(nic.getIP6Address(), "/");
        String prefixLength = StringUtils.substringAfter(nic.getIP6Address(), "/");

        ipInterface.setHost(host.getId());
        ipInterface.setProtocol(Protocol.IPV6.name());
        ipInterface.setIpAddress(ipAddress);
        ipInterface.setPrefixLength(NumberUtils.toInt(prefixLength));
        ipInterface.setIsManualCreation(false);
        setHostInterfaceRegistrationStatus(ipInterface, host);
        save(ipInterface);
    }

    public static LinuxSystemCLI createLinuxCLI(Host host) {
        if ((host.getPortNumber() != null) && (host.getPortNumber() > 0)) {
            return new LinuxSystemCLI(host.getHostName(), host.getPortNumber(), host.getUsername(), host.getPassword());
        }
        else {
            return new LinuxSystemCLI(host.getHostName(), host.getUsername(), host.getPassword());
        }
    }

    public void setDbCLient(DbClient dbClient) {
        super.setDbClient(dbClient);
    }

    @Override
    protected void setNativeGuid(Host host) {
        LinuxSystemCLI linux = createLinuxCLI(host);
        for (IPInterface nic : linux.listIPInterfaces()) {
            if (nic.getInterfaceName().equalsIgnoreCase(ETH0)) {
                if (!host.getNativeGuid().equalsIgnoreCase(nic.getMacAddress())) {
                    checkDuplicateHost(host, nic.getMacAddress());
                    info("Setting nativeGuid for " + host.getId() + " as " + nic.getMacAddress());
                    host.setNativeGuid(nic.getMacAddress());
                    save(host);
                }
                break;
            }
        }
    }
}
