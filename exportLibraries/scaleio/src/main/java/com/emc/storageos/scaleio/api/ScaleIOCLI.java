/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api;

import com.emc.storageos.scaleio.ScaleIOException;
import com.google.common.base.Strings;
import com.iwave.utility.ssh.SSHCommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class implements an interface for calling ScaleIO CLI commands over SSH.
 * <p/>
 * Expected usage:
 * <p/>
 * ScaleIOCLI cli = new ScaleIOCLI("MDM-IP/host", port, "username", "password"); ScaleIOFooResult cli.foo();
 */
public class ScaleIOCLI {
    private static final Logger log = LoggerFactory.getLogger(ScaleIOCLI.class);
    private static final Pattern versionPattern = Pattern.compile("(\\d+)_(\\d+)\\.\\d+\\.\\d+");
    /**
     * The SSH host address.
     */
    private String host;
    /**
     * The SSH port (defaults to 22).
     */
    private int port = 22;
    /**
     * The SSH username.
     */
    private String username;
    /**
     * The SSH password.
     */
    private String password;
    /**
     * The ID of the host to which this CLI connects
     */
    private URI hostId;
    /**
     * A string that can be used specify how ScaleIO CLI
     * can be invoked. Will be used for ScaleIO commodity
     */
    private String customInvocation;

    /**
     * SIO 1.30 requires an MDM user + password for running SIO CLI
     */
    private String mdmUsername;

    /**
     * SIO 1.30 requires an MDM user + password for running SIO CLI
     */
    private String mdmPassword;

    private ScaleIOCLICommand.ScaleIOCommandSemantics commandSemantics;

    /**
     * Boolean to indicate if the CLI has been initialized.
     */
    private Boolean isInitialized;

    public ScaleIOCLI() {
    }

    public ScaleIOCLI(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }

    public ScaleIOCLI(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    private void checkIfInitWasCalled() {
        if (isInitialized == null || !isInitialized) {
            log.error("ScaleIO CLI was not initialized before use");
            throw ScaleIOException.exceptions.initWasNotCalled();
        }
    }

    public String init() {
        String version = getVersion();
        int majorVersion = 1;
        int minorVersion = 22;
        Matcher versionParser = versionPattern.matcher(version);
        if (versionParser.matches()) {
            majorVersion = Integer.parseInt(versionParser.group(1));
            minorVersion = Integer.parseInt(versionParser.group(2));
        } else {
            log.error(String.format("Unexpected ScaleIO Version %s, assuming 1.22", version));
        }
        if (majorVersion == 1 && minorVersion < 30) {
            commandSemantics = ScaleIOCLICommand.ScaleIOCommandSemantics.SIO1_2X;
        } else {
            if (Strings.isNullOrEmpty(mdmUsername) || Strings.isNullOrEmpty(mdmPassword)) {
                log.error(String.format("ScaleIO CLI pointed to an %s instance requires MDM username and password",
                        version));
                throw ScaleIOException.exceptions.missingMDMCredentials();
            }
            commandSemantics = ScaleIOCLICommand.ScaleIOCommandSemantics.SIO1_30;
        }
        isInitialized = true;
        return version;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public URI getHostId() {
        return hostId;
    }

    public void setHostId(URI hostId) {
        this.hostId = hostId;
    }

    public String getCustomInvocation() {
        return customInvocation;
    }

    public void setCustomInvocation(String customInvocation) {
        this.customInvocation = customInvocation;
    }

    public String getMdmUsername() {
        return mdmUsername;
    }

    public void setMdmUsername(String mdmUsername) {
        this.mdmUsername = mdmUsername;
    }

    public String getMdmPassword() {
        return mdmPassword;
    }

    public void setMdmPassword(String mdmPassword) {
        this.mdmPassword = mdmPassword;
    }

    public void executeCommand(ScaleIOCLICommand command) {
        // scli --version is a basic command that should run
        // without the need to run a login command (1.30)
        if (!(command instanceof ScaleIOVersionCommand)) {
            checkIfInitWasCalled();
        }
        if (commandSemantics == ScaleIOCLICommand.ScaleIOCommandSemantics.SIO1_30) {
            command.use130Semantics(mdmUsername, mdmPassword);
        }
        SSHCommandExecutor executor = new SSHCommandExecutor(host, port, username, password);
        command.setCommandExecutor(executor);
        command.execute();
    }

    public ScaleIOQueryAllResult queryAll() {
        ScaleIOQueryAllCommand command = new ScaleIOQueryAllCommand(commandSemantics);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOQueryAllSDCResult queryAllSDC() {
        ScaleIOQueryAllSDCCommand command = new ScaleIOQueryAllSDCCommand();
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOQueryAllSDSResult queryAllSDS() {
        ScaleIOQueryAllSDSCommand command = new ScaleIOQueryAllSDSCommand();
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOQueryClusterResult queryClusterCommand() {
        ScaleIOQueryClusterCommand command = new ScaleIOQueryClusterCommand();
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOQueryStoragePoolResult queryStoragePool(String protectionDomainName, String storagePoolName) {
        ScaleIOQueryStoragePoolCommand command =
                new ScaleIOQueryStoragePoolCommand(protectionDomainName, storagePoolName);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOAddVolumeResult addVolume(String protectionDomainName, String storagePoolName,
            String volumeName, String volumeSize) {
        ScaleIOAddVolumeCommand command =
                new ScaleIOAddVolumeCommand(protectionDomainName, storagePoolName, volumeName, volumeSize);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOAddVolumeResult addVolume(String protectionDomainName, String storagePoolName,
            String volumeName, String volumeSize, boolean thinProvisioned) {
        ScaleIOAddVolumeCommand command =
                new ScaleIOAddVolumeCommand(commandSemantics, protectionDomainName, storagePoolName, volumeName, volumeSize,
                        thinProvisioned);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIORemoveVolumeResult removeVolume(String volumeId) {
        ScaleIORemoveVolumeCommand command = new ScaleIORemoveVolumeCommand(commandSemantics, volumeId);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOModifyVolumeCapacityResult modifyVolumeCapacity(String volumeId, String newSizeGB) {
        ScaleIOModifyVolumeCapacityCommand command = new ScaleIOModifyVolumeCapacityCommand(volumeId, newSizeGB);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOSnapshotVolumeResult snapshotVolume(String id, String snapshot) {
        ScaleIOSnapshotVolumeCommand command = new ScaleIOSnapshotVolumeCommand(id, snapshot);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOSnapshotMultiVolumeResult snapshotMultiVolume(Map<String, String> id2snapshot) {
        ScaleIOSnapshotMultiVolumeCommand command = new ScaleIOSnapshotMultiVolumeCommand(id2snapshot);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOQueryAllVolumesResult queryAllVolumes() {
        ScaleIOQueryAllVolumesCommand command = new ScaleIOQueryAllVolumesCommand();
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOMapVolumeToSDCResult mapVolumeToSDC(String volumeId, String sdcId) {
        ScaleIOMapVolumeToSDCCommand command = new ScaleIOMapVolumeToSDCCommand(commandSemantics, volumeId, sdcId);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOUnMapVolumeToSDCResult unMapVolumeToSDC(String volumeId, String sdcId) {
        ScaleIOUnMapVolumeToSDCCommand command =
                new ScaleIOUnMapVolumeToSDCCommand(commandSemantics, volumeId, sdcId);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIORemoveConsistencyGroupSnapshotsResult removeConsistencyGroupSnapshot(String consistencyGroupId) {
        ScaleIORemoveConsistencyGroupSnapshotsCommand command =
                new ScaleIORemoveConsistencyGroupSnapshotsCommand(commandSemantics, consistencyGroupId);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOQueryAllSCSIInitiatorsResult queryAllSCSIInitiators() {
        ScaleIOQueryAllSCSIInitiatorsCommand command =
                new ScaleIOQueryAllSCSIInitiatorsCommand();
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOMapVolumeToSCSIInitiatorResult mapVolumeToSCSIInitiator(String volumeId, String initiatorId) {
        ScaleIOMapVolumeToSCSIInitiatorCommand command =
                new ScaleIOMapVolumeToSCSIInitiatorCommand(commandSemantics, volumeId, initiatorId);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public ScaleIOUnMapVolumeFromSCSIInitiatorResult unMapVolumeFromSCSIInitiator(String volumeId, String initiatorId) {
        ScaleIOUnMapVolumeFromSCSIInitiatorCommand command =
                new ScaleIOUnMapVolumeFromSCSIInitiatorCommand(commandSemantics, volumeId, initiatorId);
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        return command.getResults();
    }

    public String getVersion() {
        ScaleIOVersionCommand command = new ScaleIOVersionCommand();
        command.useCustomInvocationIfSet(customInvocation);
        executeCommand(command);
        ScaleIOVersionResult result = command.getResults();
        return result.getVersion();
    }
}