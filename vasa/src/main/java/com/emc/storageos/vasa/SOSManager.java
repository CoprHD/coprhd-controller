/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* 
 Copyright (c) 2012-13 EMC Corporation
 All Rights Reserved

 This software contains the intellectual property of EMC Corporation
 or is licensed to EMC Corporation from third parties.  Use of this
 software and the intellectual property contained therein is expressly
 imited to the terms and conditions of the License Agreement under which
 it is provided by or on behalf of EMC.
 */
package com.emc.storageos.vasa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.emc.storageos.vasa.data.internal.CoS;
import com.emc.storageos.vasa.data.internal.Event;
import com.emc.storageos.vasa.data.internal.Event.EventList;
import com.emc.storageos.vasa.data.internal.FileShare;
import com.emc.storageos.vasa.data.internal.Volume;
import com.emc.storageos.vasa.data.internal.Volume.AssociatedPool;
import com.emc.storageos.vasa.data.internal.Volume.HighAvailabilityVolumes;
import com.emc.storageos.vasa.data.internal.Volume.Itls;
import com.emc.storageos.vasa.fault.SOSAuthenticationFailure;
import com.emc.storageos.vasa.fault.SOSFailure;
import com.emc.storageos.vasa.util.FaultUtil;
import com.emc.storageos.vasa.util.Util;
import com.vmware.vim.vasa._1_0.InvalidArgument;
import com.vmware.vim.vasa._1_0.InvalidLogin;
import com.vmware.vim.vasa._1_0.InvalidSession;
import com.vmware.vim.vasa._1_0.LostAlarm;
import com.vmware.vim.vasa._1_0.LostEvent;
import com.vmware.vim.vasa._1_0.NotFound;
import com.vmware.vim.vasa._1_0.NotImplemented;
import com.vmware.vim.vasa._1_0.StorageFault;
import com.vmware.vim.vasa._1_0.data.xsd.AlarmStatusEnum;
import com.vmware.vim.vasa._1_0.data.xsd.BaseStorageEntity;
import com.vmware.vim.vasa._1_0.data.xsd.BlockEnum;
import com.vmware.vim.vasa._1_0.data.xsd.EntityTypeEnum;
import com.vmware.vim.vasa._1_0.data.xsd.EventConfigTypeEnum;
import com.vmware.vim.vasa._1_0.data.xsd.EventTypeEnum;
import com.vmware.vim.vasa._1_0.data.xsd.FileSystemEnum;
import com.vmware.vim.vasa._1_0.data.xsd.FileSystemInfo;
import com.vmware.vim.vasa._1_0.data.xsd.FileSystemVersionEnum;
import com.vmware.vim.vasa._1_0.data.xsd.HostInitiatorInfo;
import com.vmware.vim.vasa._1_0.data.xsd.MountInfo;
import com.vmware.vim.vasa._1_0.data.xsd.ProfileEnum;
import com.vmware.vim.vasa._1_0.data.xsd.StorageAlarm;
import com.vmware.vim.vasa._1_0.data.xsd.StorageArray;
import com.vmware.vim.vasa._1_0.data.xsd.StorageCapability;
import com.vmware.vim.vasa._1_0.data.xsd.StorageEvent;
import com.vmware.vim.vasa._1_0.data.xsd.StorageFileSystem;
import com.vmware.vim.vasa._1_0.data.xsd.StorageLun;
import com.vmware.vim.vasa._1_0.data.xsd.StoragePort;
import com.vmware.vim.vasa._1_0.data.xsd.StorageProcessor;
import com.vmware.vim.vasa._1_0.data.xsd.UsageContext;
import com.vmware.vim.vasa._1_0.data.xsd.VasaAssociationObject;

/**
 * All functions we query Bourne for required details and bind the Bourne
 * response objects to VASA recognized data structures
 */
public class SOSManager {

    private static Logger log = Logger.getLogger(SOSManager.class);

    private Config _config;
    private SyncManager _syncManager;
    private SOSAlarmManager _alarmManager;
    private SOSEventManager _eventManager;
    private ContextManager _contextManager;
    private List<String> _reportedFileSystemIdList;
    private List<String> _reportedVolumeIdList;
    private List<com.emc.storageos.vasa.data.internal.StoragePort> _reportedStoragePortList;
    private long _lastEventId;
    private long _lastAlarmId;

    private static final String STORAGEPROCESSOR_IDENTIFIER_PREFIX = "urn:storageos:StorageProcessor:";
    private static final String STORAGEARRAY_IDENTIFIER_PREFIX = "urn:storageos:StorageArray:";
    private static final String STORAGEPORT_IDENTIFIER_PREFIX = "urn:storageos:StoragePort:";
    private static final String COS_IDENTIFIER_PREFIX = "urn:storageos:VirtualPool:";
    private static final String FILESYSTEM_IDENTIFIER_PREFIX = "urn:storageos:FileShare:";
    private static final String VOLUME_IDENTIFIER_PREFIX = "urn:storageos:Volume:";
    private static final String EXPORTGROUP_IDENTIFIER_PREFIX = "urn:storageos:ExportGroup:";

    public SOSManager() {

        _config = Config.getInstance();

        String baseURL = null;

        String configBaseURL = _config.getConfigValue("config/service/baseURL");
        if (Util.isEmpty(configBaseURL)) {
            baseURL = "https://localhost:4443";
        } else {
            log.debug("Reading base URL in config: [" + configBaseURL + "]");
            baseURL = new String(configBaseURL);
        }

        log.info("StorageOS URL: " + baseURL);
        _syncManager = new SyncManager(baseURL);
        _alarmManager = new SOSAlarmManager();
        _eventManager = new SOSEventManager();
        _lastEventId = -1L;
        _lastAlarmId = -1L;
    }

    /**
     * Verifies login details by querying Bourne and reports error in case of
     * incorrect attempt
     * 
     * @param username
     * @param password
     * @throws InvalidLogin
     * @throws StorageFault
     */
    public void verifyLoginCredentials(String username, String password)
            throws InvalidLogin, StorageFault {

        final String methodName = "verifyLoginCredentials(): ";
        log.debug(methodName + "Entry with inputs username[" + username
                + "] password[****]");

        try {
            if (Util.isEmpty(username) || Util.isEmpty(password)) {
                throw FaultUtil.InvalidLogin("Username or password is invalid");
            }
            if (username.contains("\\")) {
                String[] domainAduser = username.split("\\\\");
                if (domainAduser.length == 2) {
                    if (Util.isEmpty(domainAduser)
                            || Util.isEmpty(domainAduser[0])
                            || Util.isEmpty(domainAduser[1])) {
                        throw FaultUtil.InvalidLogin("Username is invalid["
                                + username + "]");
                    }
                    StringBuffer aduser = new StringBuffer(
                            domainAduser[1].trim());
                    aduser.append("@").append(domainAduser[0].trim());
                    log.debug(methodName + "Username for ViPR is ["
                            + aduser.toString() + "]");
                    _syncManager.verifyLogin(aduser.toString(), password);

                } else {
                    throw FaultUtil.InvalidLogin("Username is invalid["
                            + username + "]");
                }

            } else {
                _syncManager.verifyLogin(username, password);
            }
        } catch (SOSAuthenticationFailure e) {
            log.error(methodName + "StorageOSAuthenticationFailure occured", e);
            throw FaultUtil.InvalidLogin(e);
        } catch (SOSFailure e) {
            log.error(methodName + "StorageOSFailure occured", e);
            throw FaultUtil.StorageFault(e);
        }
        log.debug(methodName + "Exit");

    }

    /**
     * Caches project and tenant Ids for further operations
     * 
     * @throws StorageFault
     */
    public void sync() throws StorageFault {

        final String methodName = "sync(): ";
        log.debug(methodName + "Entry");
        try {
            log.trace(methodName + "calling syncmanger syncAll()");
            _syncManager.syncAll();
        } catch (SOSFailure e) {
            log.error(methodName + "StorageOSFailure occured", e);
            throw FaultUtil.StorageFault(e);
        }

        log.debug(methodName + "Exit");

    }

    /**
     * Makes a call to Bourne and returns list of storage port Ids
     * 
     * @return list of storage port Ids
     * @throws StorageFault
     * @throws InvalidSession
     */
    public List<String> getStoragePortIds() throws StorageFault, InvalidSession {

        final String methodName = "getStoragePortIds(): ";

        log.debug(methodName + "Entry");

        List<String> idList = null;

        try {
            String initiatorList = this
                    .getCSVListOfInitiatorsFromUsageContext();

            idList = _syncManager.getStoragePortIdList(initiatorList);

        } catch (InvalidSession e) {
            log.error(methodName + "InvalidSession occured", e);
            throw e;
        } catch (SOSFailure e) {
            log.error(methodName + "SOSFailure occured", e);
            throw FaultUtil.StorageFault(e);
        }

        log.debug(methodName + "Exit returning port Ids of size["
                + idList.size() + "]");
        return idList;
    }

    /**
     * Makes a call to Bourne and returns list of storage capability Ids
     * 
     * @return list of storage capability Ids
     * @throws StorageFault
     */
    public List<String> getCosIds() throws StorageFault {

        final String methodName = "getCosIds(): ";

        log.debug(methodName + "Entry");

        List<String> idList = new ArrayList<String>();

        try {
            for (CoS cos : _syncManager.getCosDetailList()) {
                idList.add(cos.getId());
            }
        } catch (SOSFailure e) {
            log.error(methodName + "StorageOSFailure occured", e);
            throw FaultUtil.StorageFault(e);
        }

        log.debug(methodName + "Exit returning capability Ids of size["
                + idList.size() + "]");

        return idList;
    }

    public List<String> getCosIds(boolean refresh) throws StorageFault {
        if (refresh) {
            _syncManager.resetCoS();
        }
        return this.getCosIds();
    }

    /**
     * Makes a call to Bourne and returns list of file system Ids
     * 
     * @return list of file system Ids
     * @throws StorageFault
     * @throws InvalidSession
     */
    private void setFileSystemIds() throws StorageFault, InvalidSession {

        final String methodName = "setFileSystemIds(): ";

        log.debug(methodName + "Entry ");

        if (_reportedFileSystemIdList != null) {
            return;
        }
        List<String> vcMountPaths = this.getMountPathsFromUsageContext();

        try {
            _reportedFileSystemIdList = _syncManager
                    .getFileSystemIdList(vcMountPaths);

        } catch (SOSFailure e) {
            log.error(methodName + "StorageOSFailure occured", e);
            throw FaultUtil.StorageFault(e);
        }

        log.debug(methodName + "Exit setting file system ids of size["
                + _reportedFileSystemIdList.size() + "]");

    }

    private void setFileSystemIds(boolean refresh) throws StorageFault,
            InvalidSession {
        final String methodName = "setFileSystemIds(): ";

        log.debug(methodName + "Entry with input: refresh[" + refresh + "]");
        if (refresh) {
            _reportedFileSystemIdList = null;
        }
        setFileSystemIds();

        log.debug(methodName + "Exit ");

    }

    private void setVolumeIds(boolean refresh) throws StorageFault,
            InvalidSession {
        final String methodName = "setVolumeIds(): ";

        log.debug(methodName + "Entry with input: refresh[" + refresh + "]");
        if (refresh) {
            _reportedVolumeIdList = null;
        }
        setVolumeIds();

        log.debug(methodName + "Exit ");

    }

    private void setVolumeIds() throws StorageFault, InvalidSession {

        final String methodName = "setVolumeIds(): ";

        log.debug(methodName + "Entry");

        if (_reportedVolumeIdList != null) {
            return;
        }

        String initiatorList = this.getCSVListOfInitiatorsFromUsageContext();

        try {

            _reportedVolumeIdList = _syncManager.getVolumeIdList(initiatorList);

        } catch (SOSFailure e) {
            throw FaultUtil.StorageFault(e);
        }

        log.debug(methodName + "Exit setting volume ids of size["
                + _reportedVolumeIdList.size() + "]");

    }

    public List<String> getVolumeIds() throws StorageFault, InvalidSession {

        final String methodName = "getVolumeIds(): ";

        log.debug(methodName + "Entry");
        setVolumeIds();
        log.debug(methodName + "Exit");

        return _reportedVolumeIdList;
    }

    public List<String> getFileSystemIds() throws StorageFault, InvalidSession {

        final String methodName = "getFileSystemIds(): ";

        log.debug(methodName + "Entry");
        setFileSystemIds();
        log.debug(methodName + "Exit");

        return _reportedFileSystemIdList;
    }

    private void setStoragePorts(boolean refresh) throws StorageFault,
            InvalidSession {
        if (refresh) {
            _reportedStoragePortList = null;
        }
        this.setStoragePorts();
    }

    private void setStoragePorts() throws StorageFault, InvalidSession {

        final String methodName = "setStoragePorts(): ";

        log.debug(methodName + "Entry");

        if (_reportedStoragePortList != null) {
            return;
        }
        try {

            String initiatorList = this
                    .getCSVListOfInitiatorsFromUsageContext();

            _reportedStoragePortList = _syncManager
                    .getStoragePorts(initiatorList);

            Comparator<com.emc.storageos.vasa.data.internal.StoragePort> c = new Comparator<com.emc.storageos.vasa.data.internal.StoragePort>() {

                @Override
                public int compare(
                        com.emc.storageos.vasa.data.internal.StoragePort sp1,
                        com.emc.storageos.vasa.data.internal.StoragePort sp2) {
                    return sp1.getId().compareTo(sp2.getId());

                }
            };

            Collections.sort(_reportedStoragePortList, c);

        } catch (SOSFailure e) {
            throw FaultUtil.StorageFault(e);
        }

        log.debug(methodName + "Exit setting storage ports of size["
                + _reportedStoragePortList.size() + "]");

    }

    private List<com.emc.storageos.vasa.data.internal.StoragePort> getStoragePorts()
            throws StorageFault, InvalidSession {
        final String methodName = "getStoragePorts(): ";
        log.debug(methodName + "Entry");
        setStoragePorts();
        log.debug(methodName + "Entry");
        return _reportedStoragePortList;
    }

    private List<com.emc.storageos.vasa.data.internal.StoragePort> getStoragePorts(
            List<String> portIds) throws StorageFault, InvalidSession {

        final String methodName = "getStoragePorts(): ";

        List<com.emc.storageos.vasa.data.internal.StoragePort> returnList = new ArrayList<com.emc.storageos.vasa.data.internal.StoragePort>();

        log.debug(methodName + "Entry with input: " + portIds);

        this.setStoragePorts();

        Comparator<com.emc.storageos.vasa.data.internal.StoragePort> c = new Comparator<com.emc.storageos.vasa.data.internal.StoragePort>() {

            @Override
            public int compare(
                    com.emc.storageos.vasa.data.internal.StoragePort sp1,
                    com.emc.storageos.vasa.data.internal.StoragePort sp2) {
                return sp1.getId().compareTo(sp2.getId());

            }
        };

        int index = -1;

        for (String givenPortId : portIds) {

            com.emc.storageos.vasa.data.internal.StoragePort port = new com.emc.storageos.vasa.data.internal.StoragePort(
                    givenPortId);
            index = Collections.binarySearch(_reportedStoragePortList, port, c);
            if (index >= 0) {
                log.trace(methodName + "givenFsId[" + givenPortId
                        + "] is found at portList[" + index + "]");
                returnList.add(_reportedStoragePortList.get(index));
            }
        }

        log.debug(methodName + "Exit returning storage ports of size["
                + returnList.size() + "]");

        return returnList;
    }

    /**
     * Makes a call to Bourne to get the details of given storage ports
     * 
     * @param portIds
     * @return array of <code>StoragePort</code> objects
     * @throws InvalidArgument
     * @throws InvalidSession
     * @throws StorageFault
     * @throws NotImplemented
     */
    public synchronized StoragePort[] queryStoragePorts(String[] portIds)
            throws InvalidArgument, InvalidSession, StorageFault,
            NotImplemented {

        final String methodName = "queryStoragePorts(): ";

        log.debug(methodName + "Entry");

        List<StoragePort> retStoragePorts = null;

        try {

            Boolean supportsBlock = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-block-profile"));

            if (!supportsBlock) {
                log.error(methodName + " This function is not implemented");
                throw FaultUtil
                        .NotImplemented("This function is not implemented");
            }

            List<com.emc.storageos.vasa.data.internal.StoragePort> portList = null;
            // try {
            if (Util.isEmpty(portIds)) {
                portList = this.getStoragePorts();
            } else {

                for (String inputPortId : portIds) {
                    if (!inputPortId.startsWith(STORAGEPORT_IDENTIFIER_PREFIX)) {
                        throw FaultUtil
                                .InvalidArgument("Given portId is invalid: "
                                        + inputPortId);
                    }
                }
                List<String> portIdList = Arrays.asList(portIds);
                portList = this.getStoragePorts(portIdList);

            }

            retStoragePorts = new ArrayList<StoragePort>();

            for (com.emc.storageos.vasa.data.internal.StoragePort storagePortDetail : portList) {

                String portType = storagePortDetail.getTransportType();
                String portNetworkId = storagePortDetail.getPortNetworkId();

                log.trace(methodName + "port type is [" + portType + "]");
                log.trace(methodName + "port nework Id is [" + portNetworkId
                        + "]");

                StoragePort returnStoragePort = new StoragePort();
                returnStoragePort
                        .setUniqueIdentifier(storagePortDetail.getId());
                returnStoragePort.addAlternateName(storagePortDetail
                        .getPortName());

                if ("FC".equalsIgnoreCase(portType)) {
                    log.trace(methodName
                            + "setting port WWN as port network ID ");
                    returnStoragePort.setPortWwn(portNetworkId);
                    returnStoragePort.setPortType(BlockEnum.FC.getValue());
                }

                else if ("ISCSI".equalsIgnoreCase(portType)) {
                    log.trace(methodName
                            + "setting iSCSI identifier as port network ID ");
                    returnStoragePort.setIscsiIdentifier(portNetworkId);
                    returnStoragePort.setPortType(BlockEnum.ISCSI.getValue());
                }

                else if ("IP".equalsIgnoreCase(portType)) {
                    log.trace(methodName
                            + "setting node WWN as port network ID ");
                    returnStoragePort.setNodeWwn(portNetworkId);
                    returnStoragePort.setPortType(BlockEnum.Other.getValue());
                }

                retStoragePorts.add(returnStoragePort);
            }
        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured", e);
            throw e;
        } catch (NotImplemented e) {
            log.error(methodName + "NotImplemented occured", e);
            throw e;
        }

        log.debug(methodName + "Exit returning storage ports of size["
                + retStoragePorts.size() + "]");

        return retStoragePorts.toArray(new StoragePort[0]);

    }

    public String[] getStorageProcessorId() throws StorageFault {

        final String methodName = "getStorageProcessorId(): ";

        List<String> processorIds = new ArrayList<String>();
        try {
            String storageProcessorId = this.getProcessorId();
            processorIds.add(storageProcessorId);
        } catch (Exception e) {
            log.error(methodName + "Unexpected exception occured", e);
            throw FaultUtil.StorageFault(e);
        }
        return processorIds.toArray(new String[0]);

    }

    /**
     * Makes a call to Bourne to get the details of given storage processor Ids
     * 
     * @param processorId
     * @return array of <code>StorageProcessor</code> objects
     * @throws InvalidArgument
     * @throws InvalidSession
     * @throws StorageFault
     * @throws NotImplemented
     */
    public synchronized StorageProcessor[] queryStorageProcessors(
            String[] processorId) throws InvalidArgument, InvalidSession,
            StorageFault, NotImplemented {

        final String methodName = "queryStorageProcessors(): ";

        log.debug(methodName + "Entry");

        List<StorageProcessor> processorList = null;

        try {

            Boolean supportsBlock = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-block-profile"));

            if (!supportsBlock) {
                log.error(methodName + " This function is not implemented");
                throw FaultUtil
                        .NotImplemented("This function is not implemented");
            }

            if (processorId != null) {
                log.debug(methodName + "input processor Ids "
                        + Arrays.asList(processorId));
            }
            else {
                log.debug(methodName + "input processor Ids " + processorId);
            }
            String storageProcessorId = this.getProcessorId();

            if (!Util.isEmpty(processorId)) {

                for (String inputProcessorId : processorId) {
                    if (Util.isEmpty(inputProcessorId) == true
                            || inputProcessorId
                                    .startsWith(STORAGEPROCESSOR_IDENTIFIER_PREFIX) == false) {
                        throw FaultUtil
                                .InvalidArgument("Given processor Id(s) are invalid");
                    }
                }
            }

            processorList = new ArrayList<StorageProcessor>();

            if (Util.isEmpty(processorId)) {
                StorageProcessor processor = new StorageProcessor();
                log.debug(methodName + " adding storage processor id["
                        + storageProcessorId + "]");
                processor.setUniqueIdentifier(storageProcessorId);
                processor.addSpIdentifier(storageProcessorId);
                processorList.add(processor);
            } else {
                for (String inputProcessorId : processorId) {
                    if (inputProcessorId != null
                            && storageProcessorId.equals(inputProcessorId)) {
                        StorageProcessor processor = new StorageProcessor();
                        log.debug(methodName + " adding storage processor id["
                                + storageProcessorId + "]");
                        processor.addSpIdentifier(storageProcessorId);
                        processor.setUniqueIdentifier(storageProcessorId);
                        processorList.add(processor);
                    }
                }
            }

        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured", e);
            throw (e);
        } catch (NotImplemented e) {
            log.error(methodName + "NotImplemented occured", e);
            throw FaultUtil.StorageFault(e);
        } catch (Exception e) {
            log.error(methodName + "Unexpected exception occured", e);
            throw FaultUtil.StorageFault(e);
        }

        log.debug(methodName + "Exit returning processors of size["
                + processorList.size() + "]");
        return processorList.toArray(new StorageProcessor[0]);
    }

    /**
     * Makes a call to Bourne to get the details of given file system Ids
     * 
     * @param filesystemIds
     * @return array of <code>StorageFileSystem</code> objects
     * @throws InvalidArgument
     * @throws InvalidSession
     * @throws StorageFault
     * @throws NotImplemented
     * @throws NotFound
     */
    public synchronized StorageFileSystem[] queryStorageFileSystems(
            String[] fsUniqueIds) throws InvalidArgument, StorageFault,
            NotImplemented, InvalidSession {

        final String methodName = "queryStorageFileSystems(): ";

        log.debug(methodName + "Entry");

        List<StorageFileSystem> list = null;
        try {
            Boolean supportsFile = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-file-profile"));

            if (!supportsFile) {
                log.error(methodName + " This function is not implemented");
                throw FaultUtil
                        .NotImplemented("This function is not implemented");
            }

            if (Util.isEmpty(fsUniqueIds)) {
                throw FaultUtil
                        .InvalidArgument("Given file system Ids are invalid");
            }
            for (String fsId : fsUniqueIds) {
                if (!Util.isEmpty(fsId)) {
                    if (!fsId.startsWith(FILESYSTEM_IDENTIFIER_PREFIX)) {
                        throw FaultUtil
                                .InvalidArgument("Given filesytem Id is invalid: "
                                        + fsId);
                    }
                } else {
                    throw FaultUtil
                            .InvalidArgument("Given filesytem Id is invalid: "
                                    + fsId);
                }
            }

            this.setFileSystemIds();

            List<String> existingFsIdList = new ArrayList<String>();
            for (String inputFSId : fsUniqueIds) {
                if (_reportedFileSystemIdList.contains(inputFSId)) {
                    existingFsIdList.add(inputFSId);
                }
            }

            list = new ArrayList<StorageFileSystem>();

            List<FileShare> fsList = _syncManager
                    .getFileSystemDetailList(existingFsIdList);

            for (FileShare fileshare : fsList) {

                StorageFileSystem fileSystem = new StorageFileSystem();
                fileSystem.setUniqueIdentifier(fileshare.getId());

                if (fileshare.getProtocols().getProtocol().contains("NFS")) {
                    fileSystem.setFileSystem(FileSystemEnum.NFS.getValue());
                } else if (fileshare.getProtocols().getProtocol()
                        .contains("NFSv4")) {
                    fileSystem.setFileSystem(FileSystemEnum.NFS.getValue());
                } else {
                    fileSystem.setFileSystem(FileSystemEnum.Other.getValue());
                }
                fileSystem.setFileSystemVersion(FileSystemVersionEnum.NFSV3_0
                        .getValue());

                FileSystemInfo fsDetail = new FileSystemInfo();

                String fsNetworkId = "";
                if (fileshare.getStoragePort() != null
                        && fileshare.getStorageController() != null) {
                    String storageSystemId = fileshare.getStorageController()
                            .getId();
                    String storagePortId = fileshare.getStoragePort().getId();
                    com.emc.storageos.vasa.data.internal.StoragePort storagePort = _syncManager
                            .getStoragePort(storageSystemId, storagePortId);
                    fsNetworkId = storagePort.getPortNetworkId();

                }
                fsDetail.setIpAddress(fsNetworkId);
                fsDetail.setFileServerName(fsNetworkId);
                fsDetail.setFileSystemPath(fileshare.getMountPath());
                fileSystem.addFileSystemInfo(fsDetail);
                fileSystem.setNativeSnapshotSupported(true);
                fileSystem.setThinProvisioningStatus(AlarmStatusEnum.Green
                        .getValue());

                if (log.isDebugEnabled()) {
                    log.debug(methodName
                            + "filesystem: id["
                            + fileSystem.getUniqueIdentifier()
                            + "] type["
                            + fileSystem.getFileSystem()
                            + "] version["
                            + fileSystem.getFileSystemVersion()
                            + "] thinProvisioningStatus["
                            + fileSystem.getThinProvisioningStatus()
                            + "] snapShotsupported["
                            + fileSystem.getNativeSnapshotSupported()
                            + "] IpAddress["
                            + fileSystem.getFileSystemInfo()[0]
                                    .getFileServerName()
                            + "] serverName["
                            + fileSystem.getFileSystemInfo()[0]
                                    .getFileServerName()
                            + "] fileSystemPath["
                            + fileSystem.getFileSystemInfo()[0]
                                    .getFileSystemPath() + "]");
                }
                list.add(fileSystem);

            }

        } catch (SOSFailure e) {
            log.error(methodName + "StorageOSFailure occured ", e);
            throw FaultUtil.StorageFault(e);
        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured ", e);
            throw e;
        } catch (NotImplemented e) {
            log.error(methodName + "NotImplemented occured ", e);
            throw e;
        }
        log.debug(methodName + "Exit returning list of file systems of size["
                + list.size() + "]");
        return list.toArray(new StorageFileSystem[0]);
    }

    /**
     * Makes a call to Bourne to get the details of given storage lun Ids
     * 
     * @param lunUniqueIds
     * @return array of <code>StorageLun</code> objects
     * @throws InvalidArgument
     * @throws InvalidSession
     * @throws StorageFault
     * @throws NotImplemented
     */
    public synchronized StorageLun[] queryStorageLuns(String[] lunUniqueIds)
            throws InvalidArgument, StorageFault, NotImplemented,
            InvalidSession {

        final String methodName = "queryStorageLuns(): ";

        log.debug(methodName + "Entry");

        List<StorageLun> storageLunList = null;
        try {

            Boolean supportsBlock = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-block-profile"));

            if (!supportsBlock) {
                log.error(methodName + " This function is not implemented");
                throw FaultUtil
                        .NotImplemented("This function is not implemented");
            }

            if (Util.isEmpty(lunUniqueIds)) {
                throw FaultUtil.InvalidArgument("Given LUN Ids are invalid");
            }
            for (String inputLunId : lunUniqueIds) {
                if (!Util.isEmpty(inputLunId)) {
                    if (!inputLunId.startsWith(VOLUME_IDENTIFIER_PREFIX)) {
                        throw FaultUtil
                                .InvalidArgument("Given LUN Id is invalid: "
                                        + inputLunId);
                    }
                } else {
                    throw FaultUtil.InvalidArgument("Given LUN Id is invalid: "
                            + inputLunId);
                }
            }

            // List<String> inputLunIdList = Arrays.asList(lunUniqueIds);

            List<String> existingVolIds = new ArrayList<String>();
            this.setVolumeIds();
            for (String inputLunId : lunUniqueIds) {
                if (_reportedVolumeIdList.contains(inputLunId)) {
                    existingVolIds.add(inputLunId);
                }
            }

            storageLunList = new ArrayList<StorageLun>();

            List<Volume> volumeList = null;

            volumeList = _syncManager.getVolumeDetailList(existingVolIds);

            for (Volume volume : volumeList) {

                StorageLun lun = new StorageLun();
                lun.setUniqueIdentifier(volume.getId());

                Long volumeCapacityInMB = (long) (volume
                        .getRequestedCapacityInGB() * 1024);

                Long volumeUsedCapacityInMB = (long) (volume
                        .getAllocatedCapacityInGB() * 1024);

                lun.setCapacityInMB(volumeCapacityInMB);
                lun.setDisplayName(volume.getName());
                lun.setDrsManagementPermitted(true);
                String esxLunId = "naa.";
                if (volume.getWWN() != null) {
                    esxLunId += volume.getWWN().toLowerCase();
                }
                lun.setEsxLunIdentifier(esxLunId);

                lun.setThinProvisioned(volume.isThinlyProvisioned());
                String alarmStatus = _alarmManager.getThinlyProvisionedStatus(
                        _syncManager, volume);
                lun.setThinProvisioningStatus(alarmStatus);
                lun.setUsedSpaceInMB(volumeUsedCapacityInMB);

                if (log.isDebugEnabled()) {
                    log.debug(methodName + " Lun detail: id["
                            + lun.getUniqueIdentifier() + "] ESXLunIdentifier["
                            + lun.getEsxLunIdentifier() + "] capacityInMB["
                            + lun.getCapacityInMB() + "] name["
                            + lun.getDisplayName()
                            + "] DRSManagementPermitted["
                            + lun.getDrsManagementPermitted()
                            + "] thinProvisioned[" + lun.getThinProvisioned()
                            + "] thinProvisioningStatus["
                            + lun.getThinProvisioningStatus()
                            + "] usedSpaceInMB[" + lun.getUsedSpaceInMB() + "]");
                }

                storageLunList.add(lun);
            }
        } catch (SOSFailure e) {
            log.error(methodName + "StorageOSFailure occured ", e);
            throw FaultUtil.StorageFault(e);
        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured ", e);
            throw e;
        } catch (NotImplemented e) {
            log.error(methodName + "NotImplemented occured ", e);
            throw e;
        }

        log.debug(methodName + "Exit returning LUN list of size["
                + storageLunList.size() + "]");

        return storageLunList.toArray(new StorageLun[0]);
    }

    /**
     * Makes a call to Bourne to get the details of given storage capability Ids
     * 
     * @param capId
     * @return array of <code>StorageCapability</code> objects
     * @throws InvalidArgument
     * @throws InvalidSession
     * @throws StorageFault
     * @throws NotImplemented
     */
    public synchronized StorageCapability[] queryStorageCapabilities(
            String[] capIds) throws InvalidArgument, InvalidSession,
            StorageFault, NotImplemented {

        final String methodName = "queryStorageCapabilities(): ";

        log.debug(methodName + "Entry");

        List<StorageCapability> returnList = null;
        List<CoS> cosList = null;
        try {

            Boolean supportsCapability = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-capability-profile"));

            if (!supportsCapability) {
                log.error(methodName + " This function is not implemented");
                throw FaultUtil
                        .NotImplemented("This function is not implemented");
            }

            if (Util.isEmpty(capIds)) {
                log.debug(methodName + "input capability Ids: " + capIds);
                cosList = _syncManager.getCosDetailList();
            } else {
                for (String inputCapId : capIds) {
                    if (!Util.isEmpty(inputCapId)) {
                        if (!inputCapId.startsWith(COS_IDENTIFIER_PREFIX)) {
                            throw FaultUtil
                                    .InvalidArgument("Storage capability Id is invalid: "
                                            + inputCapId);
                        }

                    } else {
                        throw FaultUtil
                                .InvalidArgument("Storage capability Id is empty: "
                                        + inputCapId);
                    }
                }

                List<String> inputCapIdList = Arrays.asList(capIds);
                log.debug(methodName + "input capability Ids: "
                        + inputCapIdList);

                cosList = _syncManager.getCosDetailList(inputCapIdList);
            }

            returnList = new ArrayList<StorageCapability>();

            for (CoS cos : cosList) {
                StorageCapability capability = new StorageCapability();
                capability.setUniqueIdentifier(cos.getId());
                capability.setCapabilityName(cos.getLabel() + ":"
                        + cos.getType());
                capability.setCapabilityDetail(cos.getDescription());

                if (log.isDebugEnabled()) {
                    log.debug(methodName + "Capability detail: id["
                            + capability.getUniqueIdentifier() + "] name["
                            + capability.getCapabilityName() + "] detail["
                            + capability.getCapabilityDetail() + "]");
                }

                returnList.add(capability);
            }

        } catch (SOSFailure e) {
            log.error(methodName + "StorageOSFailure occured ", e);
            throw FaultUtil.StorageFault(e);
        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured ", e);
            throw e;
        } catch (NotImplemented e) {
            log.error(methodName + "NotImplemented occured ", e);
            throw e;
        }

        return returnList.toArray(new StorageCapability[0]);
    }

    /**
     * Makes a call to Bourne to get the details of associated capability for
     * the given lun Ids
     * 
     * @param lunId
     * @return array of <code>VasaAssociationObject</code> objects
     * @throws InvalidArgument
     * @throws InvalidSession
     * @throws StorageFault
     * @throws NotImplemented
     */
    public synchronized VasaAssociationObject[] queryAssociatedCapabilityForLun(
            String[] lunIds) throws InvalidArgument, InvalidSession,
            StorageFault, NotImplemented {

        final String methodName = "queryAssociatedCapabilityForLun(): ";

        log.debug(methodName + "Entry");

        List<Volume> volumeList = null;
        List<VasaAssociationObject> returnList = null;

        try {

            Boolean supportsBlock = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-block-profile"));

            Boolean supportsCapability = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-capability-profile"));

            if (supportsBlock == false || supportsCapability == false) {
                log.error(methodName + " This function is not implemented");
                throw FaultUtil
                        .NotImplemented("This function is not implemented");
            }

            this.setVolumeIds();

            if (Util.isEmpty(lunIds)) {
                volumeList = _syncManager
                        .getVolumeDetailList(this._reportedVolumeIdList);
            } else {
                List<String> inputLunIds = new ArrayList<String>();
                this.setVolumeIds();
                for (String inputLunId : lunIds) {
                    if (!Util.isEmpty(inputLunId)) {
                        if (!inputLunId.startsWith(VOLUME_IDENTIFIER_PREFIX)) {
                            throw FaultUtil
                                    .InvalidArgument("Given StorageLun Id is invalid: "
                                            + inputLunId);
                        }
                        if (_reportedVolumeIdList.contains(inputLunId)) {
                            inputLunIds.add(inputLunId);
                        }
                    } else {
                        throw FaultUtil
                                .InvalidArgument("Given StorageLun Id is invalid: "
                                        + inputLunId);
                    }
                }
                // log.debug(methodName + "input LUN ids: " + inputLunIds);

                volumeList = _syncManager.getVolumeDetailList(inputLunIds);

            }

            returnList = new ArrayList<VasaAssociationObject>();
            for (Volume volume : volumeList) {

                VasaAssociationObject associationObject = new VasaAssociationObject();

                BaseStorageEntity assoc = new BaseStorageEntity();
                assoc.setUniqueIdentifier(volume.getCos().getId());
                associationObject.addAssociatedId(assoc);

                BaseStorageEntity entity = new BaseStorageEntity();
                entity.setUniqueIdentifier(volume.getId());
                associationObject.addEntityId(entity);

                log.debug(methodName + "LUN id[" + entity.getUniqueIdentifier()
                        + "] is associated to capability["
                        + assoc.getUniqueIdentifier() + "]");
                returnList.add(associationObject);
            }

        } catch (SOSFailure e) {
            log.error(methodName + "StorageOSFailure occured ", e);
            throw FaultUtil.StorageFault(e);
        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured ", e);
            throw e;
        } catch (NotImplemented e) {
            log.error(methodName + "NotImplemented occured ", e);
            throw e;
        }

        log.debug(methodName
                + "Exit returning vasa association objects of size["
                + returnList.size() + "]");

        return returnList.toArray(new VasaAssociationObject[0]);

    }

    /**
     * Makes a call to Bourne to get the details of associated capability for
     * the given file system Ids
     * 
     * @param fsId
     * @return array of <code>VasaAssociationObject</code> objects
     * @throws InvalidArgument
     * @throws InvalidSession
     * @throws StorageFault
     * @throws NotImplemented
     */
    public synchronized VasaAssociationObject[] queryAssociatedCapabilityForFileSystem(
            String[] fsIds) throws InvalidArgument, InvalidSession,
            StorageFault, NotImplemented {

        final String methodName = "queryAssociatedCapabilityForFileSystem(): ";

        log.debug(methodName + "Entry");

        List<FileShare> fsList = null;
        List<VasaAssociationObject> returnList = null;

        Boolean supportsFile = new Boolean(
                _config.getConfigValue("config/service/storageTopology/storageArray/support-file-profile"));

        Boolean supportsCapability = new Boolean(
                _config.getConfigValue("config/service/storageTopology/storageArray/support-capability-profile"));

        try {

            if (supportsFile == false || supportsCapability == false) {
                log.error(methodName + " This function is not implemented");
                throw FaultUtil
                        .NotImplemented("This function is not implemented");
            }

            this.setFileSystemIds();
            if (Util.isEmpty(fsIds)) {
                fsList = _syncManager
                        .getFileSystemDetailList(this._reportedFileSystemIdList);
            } else {

                List<String> inputIdList = new ArrayList<String>();

                for (String inputFsId : fsIds) {
                    if (!Util.isEmpty(inputFsId)) {

                        if (!inputFsId.startsWith(FILESYSTEM_IDENTIFIER_PREFIX)) {
                            throw FaultUtil
                                    .InvalidArgument("Given FileSystem Id is invalid: "
                                            + inputFsId);
                        }
                        if (_reportedFileSystemIdList.contains(inputFsId)) {
                            inputIdList.add(inputFsId);
                        }
                    } else {
                        throw FaultUtil
                                .InvalidArgument("Given FileSystem Id is invalid: "
                                        + inputFsId);
                    }
                }
                log.debug(methodName + "input file system ids: " + inputIdList);

                fsList = _syncManager.getFileSystemDetailList(inputIdList);
            }

            returnList = new ArrayList<VasaAssociationObject>();
            for (FileShare fileShare : fsList) {

                VasaAssociationObject associationObject = new VasaAssociationObject();

                BaseStorageEntity assoc = new BaseStorageEntity();
                assoc.setUniqueIdentifier(fileShare.getCos().getId());

                BaseStorageEntity entity = new BaseStorageEntity();
                entity.setUniqueIdentifier(fileShare.getId());

                associationObject.addAssociatedId(assoc);
                associationObject.addEntityId(entity);

                log.debug(methodName + "File system id["
                        + entity.getUniqueIdentifier()
                        + "] is associated to capability["
                        + assoc.getUniqueIdentifier() + "]");

                returnList.add(associationObject);
            }

        } catch (SOSFailure e1) {
            log.error(methodName + "StorageOSFailure occured ", e1);
            throw FaultUtil.StorageFault(e1);
        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured ", e);
            throw e;
        } catch (NotImplemented e) {
            log.error(methodName + "NotImplemented occured ", e);
            throw e;
        }
        log.debug(methodName
                + "Exit returning vasa association objects of size["
                + returnList.size() + "]");

        return returnList.toArray(new VasaAssociationObject[0]);

    }

    /**
     * Makes a call to Bourne to get the associated processors for the given
     * storage array Ids
     * 
     * @param arrayUniqueIds
     * @return array of <code>VasaAssociationObject</code> objects
     * @throws InvalidArgument
     * @throws InvalidSession
     * @throws StorageFault
     * @throws NotImplemented
     */
    public synchronized VasaAssociationObject[] queryAssociatedProcessorsForArray(
            String[] arrayUniqueIds) throws InvalidArgument, StorageFault,
            NotImplemented, InvalidSession {

        final String methodName = "queryAssociatedProcessorsForArray(): ";

        List<VasaAssociationObject> returnList = null;
        String bourneArrayId = this.getArrayId();
        log.debug(methodName + "Entry");

        Boolean supportsBlock = new Boolean(
                _config.getConfigValue("config/service/storageTopology/storageArray/support-block-profile"));

        try {
            if (!supportsBlock) {
                log.error(methodName + " This function is not implemented");
                throw FaultUtil
                        .NotImplemented("This function is not implemented");
            }

            if (!Util.isEmpty(arrayUniqueIds)) {

                List<String> inputArrayIdList = Arrays.asList(arrayUniqueIds);
                log.debug(methodName + "input array ids: " + inputArrayIdList);

                for (String inputArrayId : inputArrayIdList) {

                    if (!Util.isEmpty(inputArrayId)
                            && !inputArrayId
                                    .startsWith(STORAGEARRAY_IDENTIFIER_PREFIX)) {
                        throw FaultUtil
                                .InvalidArgument("Given array Id is invalid:["
                                        + inputArrayId + "]");
                    }
                }

            }

            returnList = new ArrayList<VasaAssociationObject>();

            VasaAssociationObject associationObject = new VasaAssociationObject();
            BaseStorageEntity entity = new BaseStorageEntity();
            entity.setUniqueIdentifier(bourneArrayId);

            BaseStorageEntity associatedEntity = new BaseStorageEntity();
            associatedEntity.setUniqueIdentifier(this.getProcessorId());

            associationObject.addEntityId(entity);
            associationObject.addAssociatedId(associatedEntity);

            if (Util.isEmpty(arrayUniqueIds)) {
                log.debug(methodName
                        + "Exit returning vasa association objects of size["
                        + returnList.size() + "]");

                returnList.add(associationObject);

                // return returnList.toArray(new VasaAssociationObject[0]);
            } else {
                /*
                 * StorageProcessor[] processorList = this
                 * .queryStorageProcessors(null);
                 */
                for (String arrayID : arrayUniqueIds) {

                    if (bourneArrayId.equals(arrayID)) {

                        /*
                         * VasaAssociationObject associationObject = new
                         * VasaAssociationObject(); BaseStorageEntity entityObj
                         * = new BaseStorageEntity();
                         * entityObj.setUniqueIdentifier(arrayID);
                         * associationObject.addEntityId(entityObj);
                         */

                        // for (StorageProcessor proc : processorList) {

                        log.debug(methodName + "array["
                                + entity.getUniqueIdentifier()
                                + "] is associated to processor["
                                + associatedEntity.getUniqueIdentifier() + "]");

                        returnList.add(associationObject);

                    }
                    // returnList.add(associationObject);
                    // }
                }
            }

        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured ", e);
            throw e;
        } catch (NotImplemented e) {
            log.error(methodName + "NotImplemented occured ", e);
            throw e;
        } catch (StorageFault e) {
            log.error(methodName + "StorageFault occured ", e);
            throw e;
        }

        log.debug(methodName
                + "Exit returning vasa association objects of size["
                + returnList.size() + "]");
        return returnList.toArray(new VasaAssociationObject[0]);

    }

    /**
     * Makes a call to Bourne to get the associated ports for the given storage
     * processor Ids
     * 
     * @param spUniqueIds
     * @return array of <code>VasaAssociationObject</code> objects
     * @throws InvalidArgument
     * @throws InvalidSession
     * @throws StorageFault
     * @throws NotImplemented
     */
    public synchronized VasaAssociationObject[] queryAssociatedPortsForProcessor(
            String[] spUniqueIds) throws InvalidArgument, InvalidSession,
            StorageFault, NotImplemented {

        final String methodName = "queryAssociatedPortsForProcessor(): ";
        log.debug(methodName + "Entry");
        List<VasaAssociationObject> returnList = null;

        try {

            Boolean supportsBlock = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-block-profile"));

            if (!supportsBlock) {
                log.error(methodName + " This function is not implemented");
                throw FaultUtil
                        .NotImplemented("This function is not implemented");
            }

            String bourneProcessorId = this.getProcessorId();
            List<String> bourneStoragePortList = this.getStoragePortIds();
            returnList = new ArrayList<VasaAssociationObject>();

            if (!Util.isEmpty(spUniqueIds)) {
                List<String> inputPortIdList = Arrays.asList(spUniqueIds);
                log.debug(methodName + "input processor ids: "
                        + inputPortIdList);

                for (String storageProcessorId : spUniqueIds) {
                    if (!Util.isEmpty(storageProcessorId)
                            && !storageProcessorId
                                    .startsWith(STORAGEPROCESSOR_IDENTIFIER_PREFIX)) {
                        throw FaultUtil
                                .InvalidArgument("Given processor Id is invalid:["
                                        + storageProcessorId + "]");
                    }

                    if (!Util.isEmpty(storageProcessorId)
                            && bourneProcessorId.equals(storageProcessorId)) {

                        log.debug(methodName
                                + "Input processor Id is matching with valid processor Id:["
                                + storageProcessorId + "]");

                        VasaAssociationObject associationObject = new VasaAssociationObject();

                        BaseStorageEntity entityObject = new BaseStorageEntity();
                        entityObject.setUniqueIdentifier(storageProcessorId);
                        associationObject.addEntityId(entityObject);

                        List<BaseStorageEntity> associatedPortList = new ArrayList<BaseStorageEntity>();
                        for (String bourneStoragePortId : bourneStoragePortList) {

                            BaseStorageEntity associatedPort = new BaseStorageEntity();
                            log.debug(methodName
                                    + "Associating storage port ID ["
                                    + bourneStoragePortId
                                    + "] to processor ID[" + storageProcessorId
                                    + "]");
                            associatedPort
                                    .setUniqueIdentifier(bourneStoragePortId);
                            associatedPortList.add(associatedPort);
                        }

                        associationObject.setAssociatedId(associatedPortList
                                .toArray(new BaseStorageEntity[0]));
                        returnList.add(associationObject);

                    }
                }

                log.debug(methodName
                        + "Exit returning vasa association objects of size["
                        + returnList.size() + "]");
                return returnList.toArray(new VasaAssociationObject[0]);

            } else if (spUniqueIds != null && spUniqueIds.length == 0) {
                log.debug(methodName
                        + "Exit returning vasa association objects of size["
                        + returnList.size() + "]");
                return returnList.toArray(new VasaAssociationObject[0]);
            }

            VasaAssociationObject associationObject = new VasaAssociationObject();
            BaseStorageEntity entityObject = new BaseStorageEntity();
            entityObject.setUniqueIdentifier(bourneProcessorId);
            associationObject.addEntityId(entityObject);

            List<BaseStorageEntity> associatedPortList = new ArrayList<BaseStorageEntity>();
            for (String bourneStoragePortId : bourneStoragePortList) {

                BaseStorageEntity associatedPort = new BaseStorageEntity();
                log.debug(methodName + "Associating storage port ID ["
                        + bourneStoragePortId + "] to processor ID["
                        + bourneProcessorId + "]");
                associatedPort.setUniqueIdentifier(bourneStoragePortId);
                associatedPortList.add(associatedPort);
            }

            associationObject.setAssociatedId(associatedPortList
                    .toArray(new BaseStorageEntity[0]));
            returnList.add(associationObject);

        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured ", e);
            throw e;
        } catch (NotImplemented e) {
            log.error(methodName + "NotImplemented occured ", e);
            throw e;
        } catch (StorageFault e) {
            log.error(methodName + "StorageFault occured ", e);
            throw e;
        }

        log.debug(methodName
                + "Exit returning vasa association objects of size["
                + returnList.size() + "]");

        return returnList.toArray(new VasaAssociationObject[0]);

    }

    /**
     * Makes a call to Bourne to get the associated Luns for the given storage
     * port Ids
     * 
     * @param portUniqueIds
     * @return array of <code>VasaAssociationObject</code> objects
     * @throws InvalidArgument
     * @throws InvalidSession
     * @throws StorageFault
     * @throws NotImplemented
     */
    public synchronized VasaAssociationObject[] queryAssociatedLUNsForPort(
            String[] portUniqueIds) throws InvalidArgument, InvalidSession,
            StorageFault, NotImplemented {

        final String methodName = "queryAssociatedLUNsForPort(): ";

        log.debug(methodName + "Entry");
        List<VasaAssociationObject> returnList = null;
        List<String> inputPortIdList = null;

        log.info(methodName + "Input:[" + portUniqueIds + "]");

        try {

            Boolean supportsBlock = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-block-profile"));

            if (!supportsBlock) {
                log.error(methodName + " This function is not implemented");
                throw FaultUtil
                        .NotImplemented("This function is not implemented");
            }

            String csvSeparatedInitiatorList = this
                    .getCSVListOfInitiatorsFromUsageContext();
            Hashtable<String, List<String>> portToVolumeTable = _syncManager
                    .getStoragePortToVolumeTable(csvSeparatedInitiatorList);

            returnList = new ArrayList<VasaAssociationObject>();

            if (Util.isEmpty(portUniqueIds)) {
                // Return all port associated to LUNs
                for (String portId : portToVolumeTable.keySet()) {

                    VasaAssociationObject associationObject = new VasaAssociationObject();

                    BaseStorageEntity storagePort = new BaseStorageEntity();
                    storagePort.setUniqueIdentifier(portId);
                    associationObject.addEntityId(storagePort);

                    for (String volumeId : portToVolumeTable.get(portId)) {
                        BaseStorageEntity associatedVolume = new BaseStorageEntity();
                        associatedVolume.setUniqueIdentifier(volumeId);
                        associationObject.addAssociatedId(associatedVolume);
                    }

                    returnList.add(associationObject);
                }

            } else {

                inputPortIdList = Arrays.asList(portUniqueIds);
                log.debug(methodName + "Input port ids: " + inputPortIdList);

                for (String inputPortId : inputPortIdList) {
                    if (!Util.isEmpty(inputPortId)) {
                        if (!inputPortId
                                .startsWith(STORAGEPORT_IDENTIFIER_PREFIX)) {
                            throw FaultUtil
                                    .InvalidArgument("Given port Id is invalid["
                                            + inputPortId + "]");
                        } else {

                            List<String> volumeIdList = portToVolumeTable
                                    .get(inputPortId);
                            if (volumeIdList != null && !volumeIdList.isEmpty()) {
                                VasaAssociationObject associationObject = new VasaAssociationObject();

                                BaseStorageEntity storagePort = new BaseStorageEntity();
                                storagePort.setUniqueIdentifier(inputPortId);
                                associationObject.addEntityId(storagePort);

                                for (String volumeId : volumeIdList) {
                                    BaseStorageEntity associatedVolume = new BaseStorageEntity();
                                    associatedVolume
                                            .setUniqueIdentifier(volumeId);
                                    associationObject
                                            .addAssociatedId(associatedVolume);
                                }

                                returnList.add(associationObject);
                            }
                        }
                    }

                    // }
                }
            }

            return returnList.toArray(new VasaAssociationObject[0]);

        } catch (SOSFailure e) {
            log.error("StorageOSFailure occured", e);
            throw FaultUtil.StorageFault("StorageOSFailure occured", e);
        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured ", e);
            throw e;
        } catch (NotImplemented e) {
            log.error(methodName + "NotImplemented occured ", e);
            throw e;
        }

    }

    /**
     * Returns the storage array details for the given storage array Ids
     * 
     * @param arrayId
     * @return array of <code>StorageArray</code> objects. Each having details
     *         of profiles the storage array supports
     * @throws InvalidArgument
     * @throws InvalidSession
     * @throws StorageFault
     */
    public synchronized StorageArray[] queryArrays(String[] arrayId)
            throws InvalidArgument, InvalidSession, StorageFault {

        // Mandatory function

        final String methodName = "queryArrays(): ";
        log.debug(methodName + "Entry");

        List<StorageArray> storageArrayList = null;
        StorageArray[] arrays = null;

        if (!Util.isEmpty(arrayId)) {
            log.debug(methodName + "input array ids: " + Arrays.asList(arrayId));
        } else {
            log.debug(methodName + "input array ids: " + arrayId);
        }
        try {

            String sosArrayId = this.getArrayId();

            if (!Util.isEmpty(arrayId)) {

                storageArrayList = new ArrayList<StorageArray>();

                for (String inputArrayId : arrayId) {
                    if (!inputArrayId
                            .startsWith(STORAGEARRAY_IDENTIFIER_PREFIX)) {
                        throw FaultUtil
                                .InvalidArgument("Given array Id is invalid:["
                                        + arrayId + "]");
                    }

                    if (sosArrayId.equals(inputArrayId)) {
                        StorageArray storageArray = this.getSOSStorageArray();
                        storageArray.setUniqueIdentifier(inputArrayId);
                        storageArrayList.add(storageArray);
                    }

                }
                return storageArrayList.toArray(new StorageArray[0]);
            }

            arrays = new StorageArray[1];
            StorageArray array = this.getSOSStorageArray();
            arrays[0] = array;
        } catch (StorageFault e) {
            log.error("StorageFault occured", e);
            throw e;
        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured ", e);
            throw e;
        }

        log.debug(methodName + "Exit returning arrays of size[" + arrays.length
                + "]");
        return arrays;
    }

    private StorageArray getSOSStorageArray() throws StorageFault {

        final String methodName = "getSOSStorageArray(): ";

        StorageArray sa = new StorageArray();
        String arrayName = _config
                .getConfigValue("config/service/storageTopology/storageArray/name");

        Boolean supportsFile = new Boolean(
                _config.getConfigValue("config/service/storageTopology/storageArray/support-file-profile"));

        Boolean supportsBlock = new Boolean(
                _config.getConfigValue("config/service/storageTopology/storageArray/support-block-profile"));

        Boolean supportsCapabilty = new Boolean(
                _config.getConfigValue("config/service/storageTopology/storageArray/support-capability-profile"));

        sa.setArrayName(arrayName);
        String sosArrayId = this.getArrayId();
        sa.setUniqueIdentifier(sosArrayId);
        sa.setFirmware("1.0");

        if (supportsFile) {
            sa.addSupportedProfile(ProfileEnum.FileSystemProfile.getValue());
            sa.addSupportedFileSystem(FileSystemEnum.NFS.getValue());
        }

        if (supportsBlock) {
            sa.addSupportedProfile(ProfileEnum.BlockDeviceProfile.getValue());
            sa.addSupportedBlock(BlockEnum.FC.getValue());
            sa.addSupportedBlock(BlockEnum.ISCSI.getValue());
        }

        if (supportsCapabilty) {
            sa.addSupportedProfile(ProfileEnum.CapabilityProfile.getValue());
        }

        sa.setAlternateName(new String[] { arrayName });
        sa.setModelId(com.emc.storageos.vasa.Constants.VASA_BOURNE_PROVIDER_VENDOR_MODEL);
        sa.setVendorId(com.emc.storageos.vasa.Constants.VASA_BOURNE_PROVIDER_VENDOR_NAME);

        log.debug(methodName + "array detail: id[" + sa.getUniqueIdentifier()
                + "] name[" + sa.getArrayName() + "] modelId["
                + sa.getModelId() + "] vendorId[" + sa.getVendorId()
                + "] profile(s) supported"
                + Arrays.asList(sa.getSupportedProfile()) + "] firmware["
                + sa.getFirmware() + "]");

        log.debug(methodName + "Exting returning SOS storage array");

        return sa;

    }

    public String getArrayId() throws StorageFault {
        final String methodName = "getArrayId(): ";
        String arrayId = "StorageArray";

        try {
            arrayId = _syncManager.getArrayId();
        } catch (SOSFailure e) {
            log.error(methodName + "unexpected exception occured", e);
            throw FaultUtil.StorageFault(e);
        }

        return arrayId;
    }

    private String getProcessorId() throws StorageFault {
        final String methodName = "getProcessorId(): ";
        String arrayId = "StorageProcessor";

        try {
            arrayId = _syncManager.getProcessorId();
        } catch (SOSFailure e) {
            log.error(methodName + "unexpected exception occured", e);
            throw FaultUtil.StorageFault(e);
        }

        return arrayId;
    }

    /**
     * Provides alarm data that indicates a change in system availability.
     * 
     * Returns List of data objects describing alarms. The first element in the
     * list describes the alarm that follows lastAlarmId.
     * 
     * @param lastAlarmId
     *            Identifier for the most recent alarm known to the vCenter
     *            Server. The value zero indicates a request for all alarms with
     *            a status of RED or YELLOW
     * @throws InvalidArgument
     *             Thrown if the specified event identifier (lastEventId) is not
     *             valid.
     * @throws InvalidSession
     *             Thrown if the VASA Provider determines that the session is
     *             not valid.
     * @throws LostAlarm
     *             Thrown if the VASA Provider determines that the vCenter
     *             Server has lost alarm data, based on the lastAlarmId
     *             parameter.
     * @throws StorageFault
     *             Thrown for an error that is not covered by the other faultse
     */
    public synchronized StorageAlarm[] getAlarms(long lastAlarmId)
            throws LostAlarm, InvalidArgument, InvalidSession, StorageFault {
        // Mandatory function

        final String methodName = "getAlarms(): ";

        log.debug(methodName + "Entry with lastAlarmId[" + lastAlarmId + "]");

        long inputLastAlarmId = lastAlarmId;

        try {
            if (inputLastAlarmId < -1) {
                log.error("Inavlid value of input lastAlarmId[" + lastAlarmId
                        + "]");
                throw FaultUtil
                        .InvalidArgument("Inavlid value of input lastAlarmId["
                                + inputLastAlarmId + "]");
            }

            if ((inputLastAlarmId != -1)
                    && (inputLastAlarmId < this._lastAlarmId)) {
                throw FaultUtil.LostAlarm("Got unexpected last alarm id["
                        + inputLastAlarmId + "]");
            }

            if (inputLastAlarmId > this._lastAlarmId) {
                log.error("Inavlid value of input lastAlarmId["
                        + inputLastAlarmId + "]");
                throw FaultUtil
                        .InvalidArgument("Inavlid value of input lastAlarmId["
                                + inputLastAlarmId + "]");
            }

            this.setVolumeIds();
            List<Volume> volumeList = null;

            this.setVolumeIds();
            volumeList = _syncManager
                    .getVolumeDetailList(_reportedVolumeIdList);

            List<StorageAlarm> alarmList = _alarmManager
                    .getThinProvisionAlarms(_syncManager, volumeList,
                            lastAlarmId);
            if (!alarmList.isEmpty()) {

                lastAlarmId += alarmList.size();

                log.debug(methodName + "New last alarm Id: [" + lastAlarmId
                        + "]");
                this._lastAlarmId = lastAlarmId;

            }

            log.debug(methodName + "Exit returning alarms of size["
                    + alarmList.size() + "]");

            return alarmList.toArray(new StorageAlarm[0]);
        } catch (SOSFailure e) {
            log.error(methodName + "StorageOSFailure occured", e);
            throw FaultUtil.StorageFault(e);
        } catch (LostAlarm e) {
            log.error(methodName + "LostAlarm occured", e);
            throw e;
        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured", e);
            throw e;
        }
    }

    /**
     * Provides event data that describes changes in storageOS configuration.
     * 
     * 
     * Returns the List of data objects describing events. The first element in
     * the list describes the event that follows lastEventId.
     * 
     * @param lastEventId
     *            Identifier for the most recent event known to the vCenter
     *            Server. The value zero indicates a request for all available
     *            events
     * @throws InvalidArgument
     *             Thrown if the specified event identifier (lastEventId) is not
     *             valid
     * @throws InvalidSession
     *             Thrown if the VASA Provider determines that the session is
     *             not valid.
     * @throws LostEvent
     *             Thrown if the VASA Provider determines that the vCenter
     *             Server has lost event data, based on the lastEventId
     *             parameter.
     * @throws StorageFault
     *             Thrown for an error that is not covered by the other faults.
     */
    public synchronized StorageEvent[] getEvents(long lastEventId)
            throws LostEvent, InvalidArgument, InvalidSession, StorageFault {

        // Mandatory function

        final String methodName = "getEvents(): ";
        final long hoursInMillis = 60L * 60L * 1000L;
        boolean eventRecorded = false;

        log.debug(methodName + "Entry with lastEventId[" + lastEventId + "]");

        long inputLastEventId = lastEventId;

        if (inputLastEventId < -1) {
            log.error("Inavlid value of input lastEventId[" + inputLastEventId
                    + "]");
            throw FaultUtil
                    .InvalidArgument("Inavlid value of input lastEventId["
                            + inputLastEventId + "]");
        }

        if ((inputLastEventId != -1) && (inputLastEventId < this._lastEventId)) {
            throw FaultUtil.LostEvent("Got unexpected last event id["
                    + inputLastEventId + "]");
        }

        if (inputLastEventId > this._lastEventId) {
            log.error("Inavlid value of input lastEventId[" + inputLastEventId
                    + "]");
            throw FaultUtil
                    .InvalidArgument("Inavlid value of input lastEventId["
                            + inputLastEventId + "]");
        }

        List<StorageEvent> storageEventList = new ArrayList<StorageEvent>();
        StorageEvent storageEvent = null;

        // Get the events from Q from lastEventId.
        Iterator<StorageEvent> it = _eventManager.getEventQ().iterator();
        // long eventId = lastEventId;
        while (it.hasNext()) {
            storageEvent = it.next();
            if (storageEvent.getEventId() > inputLastEventId) {
                storageEventList.add(storageEvent);
                lastEventId = storageEvent.getEventId();
            }
        }

        // Get when we query last time.
        Calendar last = _eventManager.getLastEventEnqTime();
        if (last == null) {
            last = Calendar.getInstance();
        }
        Calendar now = Calendar.getInstance();
        Calendar lastEnqTime = null;
        while (last.compareTo(now) <= 0) {

            lastEnqTime = _eventManager.getLastEventEnqTime();

            if (lastEnqTime == null) {
                lastEnqTime = Calendar.getInstance();
            }

            // Clean up the Q, if the Q holds last hour events.
            if (lastEnqTime.get(Calendar.HOUR_OF_DAY) != last
                    .get(Calendar.HOUR_OF_DAY)) {
                _eventManager.getEventQ().clear();
            }

            EventList eventListObj = null;
            try {
                eventListObj = _syncManager.getEvents(last);

            } catch (SOSFailure e) {
                log.debug(methodName + "StorageOSFailure occured", e);
                throw FaultUtil.StorageFault(e);
            }

            // Set the query time to current enquired time
            _eventManager.setLastEventEnqTime(last);
            last.setTimeInMillis(last.getTimeInMillis() + hoursInMillis);

            storageEvent = null;
            if (eventListObj != null && eventListObj.getEvents() != null) {
                for (Event event : eventListObj.getEvents()) {

                    eventRecorded = false;

                    // Does this event already captured?.
                    if (_eventManager.isEventExistsInQueue(event)) {
                        continue;
                    }

                    // Is it a Required event?
                    if (!_eventManager.isEventRequired(event.getEventType())) {
                        continue;
                    }

                    if (event.getResourceId() == null) {
                        continue;
                    }
                    String resourceId = event.getResourceId().toString();

                    log.debug(methodName + " Event occurred on resourceId["
                            + resourceId + "]");

                    if (resourceId.startsWith(FILESYSTEM_IDENTIFIER_PREFIX)) {
                        this.setFileSystemIds();
                        List<String> currentFSList = new ArrayList<String>(
                                _reportedFileSystemIdList);
                        if (_reportedFileSystemIdList.contains(resourceId)) {
                            this.setFileSystemIds(true);
                        } else {

                            this.setFileSystemIds(true);
                            List<String> newFSList = new ArrayList<String>(
                                    _reportedFileSystemIdList);

                            if (Util.areListsEqual(currentFSList, newFSList)) {
                                continue;
                            }

                        }

                    } else if (resourceId.startsWith(VOLUME_IDENTIFIER_PREFIX)
                            || resourceId
                                    .startsWith(EXPORTGROUP_IDENTIFIER_PREFIX)) {

                        log.debug(methodName
                                + " Event occurred on Volume/Export group["
                                + resourceId + "]");

                        this.setVolumeIds();

                        List<String> currentVolumeList = new ArrayList<String>(
                                _reportedVolumeIdList);

                        if (_reportedVolumeIdList.contains(resourceId)) {
                            this.setStoragePorts(true);
                            this.setVolumeIds(true);
                        } else {
                            this.setStoragePorts(true);
                            this.setVolumeIds(true);
                            List<String> newVolumeList = new ArrayList<String>(
                                    _reportedVolumeIdList);

                            if (Util.areListsEqual(currentVolumeList,
                                    newVolumeList)) {
                                log.debug(methodName
                                        + " Current and new volume lists are same");
                                continue;
                            } else if (newVolumeList.size() > currentVolumeList
                                    .size()) {
                                // Volume exported

                                log.debug(methodName + "A Volume got exported");
                                storageEvent = _eventManager.createNewEvent(
                                        event.getEventId(),
                                        event.getTimeOccurred(), ++lastEventId,
                                        event.getResourceId().toString(),
                                        EntityTypeEnum.StorageLun.getValue(),
                                        EventTypeEnum.Config.getValue(),
                                        EventConfigTypeEnum.New.getValue(),
                                        "StorageOS.VolumeExported");

                                eventRecorded = true;

                            } else {
                                // Volume un-exported
                                log.debug(methodName
                                        + "A Volume got un-exported");

                                storageEvent = _eventManager.createNewEvent(
                                        event.getEventId(),
                                        event.getTimeOccurred(), ++lastEventId,
                                        event.getResourceId().toString(),
                                        EntityTypeEnum.StorageLun.getValue(),
                                        EventTypeEnum.Config.getValue(),
                                        EventConfigTypeEnum.Delete.getValue(),
                                        "StorageOS.VolumeUnexported");

                                eventRecorded = true;
                            }
                        }

                    } else if (resourceId
                            .startsWith(STORAGEPORT_IDENTIFIER_PREFIX)) {
                        this.setStoragePorts(true);

                    } else if (resourceId.startsWith(COS_IDENTIFIER_PREFIX)) {
                        _syncManager.resetCoS();
                    }
                    if (false == eventRecorded) {
                        // Set the StorageEvent parameters.

                        storageEvent = _eventManager.createNewEvent(event
                                .getEventId(), event.getTimeOccurred(),
                                ++lastEventId,
                                event.getResourceId().toString(), _eventManager
                                        .getEventObjectType(event
                                                .getEventType()),
                                _eventManager.getVasaEventType(event
                                        .getEventType()),
                                _eventManager.getVasaConfigType(event
                                        .getEventType()), _eventManager
                                        .getMessageIdForEvent(event
                                                .getEventType()));

                        /*
                         * storageEvent = new StorageEvent();
                         * storageEvent.setEventId(++lastEventId);
                         * storageEvent.setObjectId(event.getResourceId()
                         * .toString());
                         * storageEvent.setEventObjType(_eventManager
                         * .getEventObjectType(event.getEventType()));
                         * 
                         * storageEvent.setEventType(_eventManager
                         * .getVasaEventType(event.getEventType()));
                         * 
                         * storageEvent.setEventConfigType(_eventManager
                         * .getVasaConfigType(event.getEventType()));
                         * 
                         * GregorianCalendar gc = new GregorianCalendar();
                         * gc.setTimeInMillis(Long.parseLong(event
                         * .getTimeOccurred()));
                         * storageEvent.setEventTimeStamp(gc);
                         * 
                         * storageEvent.setMessageId(_eventManager
                         * .getMessageIdForEvent(event.getEventType()));
                         * 
                         * // Storageos generated eventid is used to check the
                         * // duplicates in the Q!!. NameValuePair nvp = new
                         * NameValuePair(); nvp.setParameterName("SOSEventId");
                         * nvp.setParameterValue(event.getEventId().toString());
                         * storageEvent.addParameterList(nvp);
                         * 
                         * NameValuePair nvp2 = new NameValuePair();
                         * nvp2.setParameterName("resId");
                         * nvp2.setParameterValue
                         * (event.getResourceId().toString());
                         * storageEvent.addParameterList(nvp2);
                         */
                    }
                    log.info(methodName + "Event["
                            + storageEvent.getMessageId() + "] of type["
                            + storageEvent.getEventConfigType() + "] occured");

                    // Add the Event to the list as well as in Q!!.
                    _eventManager.setEventRecord(storageEvent);
                    storageEventList.add(storageEvent);

                }
            }
        }

        this._lastEventId = lastEventId;

        log.debug(methodName + "Exit returning events of size["
                + storageEventList.size() + "]");

        return storageEventList.toArray(new StorageEvent[0]);

    }

    public synchronized boolean queryDRSMigrationCapabilityForPerformance(
            String srcUniqueId, String dstUniqueId, String entityType)
            throws InvalidArgument, NotFound, InvalidSession, StorageFault {

        final String methodName = "queryDRSMigrationCapabilityForPerformance(): ";

        log.debug(methodName + "Entry with input(s) srcUniqueId[" + srcUniqueId
                + "] dstUniqueId[" + dstUniqueId + "] entityType[" + entityType
                + "]");

        try {
            if (Util.isEmpty(entityType)) {
                throw FaultUtil
                        .InvalidArgument("Given entity type is invalid: ["
                                + entityType + "]");
            }

            List<String> validEntityTypeList = new ArrayList<String>();
            validEntityTypeList
                    .add(EntityTypeEnum.StorageFileSystem.getValue());
            validEntityTypeList.add(EntityTypeEnum.StorageLun.getValue());

            if (validEntityTypeList.contains(entityType) == false) {

                throw FaultUtil
                        .InvalidArgument("Given entity type is invalid: ["
                                + entityType + "]");

            }

            if (Util.isEmpty(srcUniqueId) || Util.isEmpty(dstUniqueId)) {
                throw FaultUtil
                        .InvalidArgument("Given identifiers are invalid");
            }

            Boolean supportsFile = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-file-profile"));

            if (EntityTypeEnum.StorageFileSystem.getValue().equals(entityType)) {

                if (!supportsFile) {

                    throw FaultUtil
                            .InvalidArgument("Given entity type is invalid: ["
                                    + entityType
                                    + "]. It does not match with the supported array profile");
                }

                if (!srcUniqueId.startsWith(FILESYSTEM_IDENTIFIER_PREFIX)
                        || !dstUniqueId
                                .startsWith(FILESYSTEM_IDENTIFIER_PREFIX)) {
                    throw FaultUtil
                            .InvalidArgument("Given identifiers are invalid");

                }

            }

            Boolean supportsBlock = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-block-profile"));

            if (EntityTypeEnum.StorageLun.getValue().equals(entityType)) {

                if (!supportsBlock) {
                    throw FaultUtil
                            .InvalidArgument("Given entity type is invalid: ["
                                    + entityType
                                    + "]. It does not match with the supported array profile");
                }

                if (!srcUniqueId.startsWith(VOLUME_IDENTIFIER_PREFIX)
                        || !dstUniqueId.startsWith(VOLUME_IDENTIFIER_PREFIX)) {
                    throw FaultUtil
                            .InvalidArgument("Given identifiers are invalid");
                }

            }

            List<String> inputIdList = new ArrayList<String>();
            inputIdList.add(srcUniqueId);
            inputIdList.add(dstUniqueId);

            if (EntityTypeEnum.StorageFileSystem.getValue().equals(entityType)) {

                this.setFileSystemIds();
                if (srcUniqueId.startsWith(FILESYSTEM_IDENTIFIER_PREFIX)
                        && dstUniqueId.startsWith(VOLUME_IDENTIFIER_PREFIX)) {
                    return false;
                } else {

                    if (!_reportedFileSystemIdList.contains(srcUniqueId)) {
                        log.error(methodName
                                + "Source Id does not exist for the entity type["
                                + entityType + "]");
                        throw FaultUtil
                                .NotFound("Source Id does not exist for the entity type["
                                        + entityType + "]");
                    }

                    if (!_reportedFileSystemIdList.contains(dstUniqueId)) {
                        log.error(methodName
                                + "Destination Id does not exist for the entity type["
                                + entityType + "]");
                        throw FaultUtil
                                .NotFound("Destination Id does not exist for the entity type["
                                        + entityType + "]");
                    }
                }

                List<FileShare> fileShareList = _syncManager
                        .getFileSystemDetailList(inputIdList);
                FileShare fileShare1 = fileShareList.get(0);
                FileShare fileShare2 = fileShareList.get(1);
                if (fileShare1.getPool() != null
                        && fileShare2.getPool() != null
                        && fileShare1.getPool().getId() != null
                        && fileShare2.getPool().getId() != null) {

                    // Don't recommend DRS when both fileshares belong to the
                    // same pool

                    if (fileShare1.getPool().getId()
                            .equals(fileShare2.getPool().getId())) {
                        return false;
                    }
                    else {
                        return true;
                    }

                }

            }

            if (EntityTypeEnum.StorageLun.getValue().equals(entityType)) {

                this.setVolumeIds();
                if (srcUniqueId.startsWith(FILESYSTEM_IDENTIFIER_PREFIX)
                        && dstUniqueId.startsWith(VOLUME_IDENTIFIER_PREFIX)) {
                    return false;
                } else {
                    if (!_reportedVolumeIdList.contains(srcUniqueId)) {
                        log.error(methodName
                                + "Source  Id does not exist for the entity type["
                                + entityType + "]");
                        throw FaultUtil
                                .NotFound("Source Id does not exist for the entity type["
                                        + entityType + "]");
                    }

                    if (!_reportedVolumeIdList.contains(dstUniqueId)) {
                        log.error(methodName
                                + "Destination  Id does not exist for the entity type["
                                + entityType + "]");
                        throw FaultUtil
                                .NotFound("Destination Id does not exist for the entity type["
                                        + entityType + "]");
                    }
                }

                // Check if src and dest are VPLEX volumes
                List<Volume> volumeDetailList = _syncManager
                        .getVolumeDetailList(inputIdList);

                for (Volume volume : volumeDetailList) {
                    if (volume != null) {
                        HighAvailabilityVolumes haVolumes = volume
                                .getHaVolumeList();
                        if (haVolumes != null
                                && haVolumes.getHaVolumeList() != null) {
                            return false;
                        }
                    }
                }

                // Regular volumes
                AssociatedPool associatedPoolForVolume1 = _syncManager
                        .fetchAssociatedPoolOfVolume(srcUniqueId);
                AssociatedPool associatedPoolForVolume2 = _syncManager
                        .fetchAssociatedPoolOfVolume(dstUniqueId);

                if (associatedPoolForVolume1 != null
                        && associatedPoolForVolume2 != null) {

                    if (associatedPoolForVolume1
                            .getStoragepool()
                            .getId()
                            .equals(associatedPoolForVolume2.getStoragepool()
                                    .getId())) {
                        return false;
                    } else {
                        return true;
                    }
                }
            }
        } catch (SOSFailure e) {
            log.error("StorageOSFailure occured", e);
            throw FaultUtil.StorageFault("StorageOSFailure occured", e);
        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured ", e);
            throw e;
        } catch (NotFound e) {
            log.error(methodName + "NotFound occured ", e);
            throw e;
        }

        return false;
    }

    public synchronized String[] queryUniqueIdentifiersForLuns(
            String arrayUniqueId) throws InvalidArgument, NotFound,
            InvalidSession, StorageFault, NotImplemented {

        final String methodName = "queryUniqueIdentifiersForLuns(): ";
        log.debug(methodName + "Entry with input arrayUniqueId["
                + arrayUniqueId + "]");

        List<String> ids = null;
        try {

            Boolean supportsBlock = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-block-profile"));

            if (supportsBlock == false) {
                log.error(methodName + " This function is not implemented");
                throw FaultUtil
                        .NotImplemented("This function is not implemented");
            }

            if (Util.isEmpty(arrayUniqueId)
                    || !arrayUniqueId
                            .startsWith(STORAGEARRAY_IDENTIFIER_PREFIX)) {
                throw FaultUtil.InvalidArgument("Given array Id is invalid: "
                        + arrayUniqueId);
            }

            String sosArrayId = this.getArrayId();

            if (!sosArrayId.equals(arrayUniqueId)) {
                throw FaultUtil.NotFound("Given array Id is not found: "
                        + arrayUniqueId);
            }

            // run function
            ids = this.getVolumeIds();

        } catch (NotFound e) {
            log.error(methodName + "NotFound occured ", e);
            throw e;
        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured ", e);
            throw e;
        } catch (NotImplemented e) {
            log.error(methodName + "NotImplemented occured ", e);
            throw e;
        }

        log.debug(methodName + "Exit returning ids of size[" + ids.size() + "]");
        return ids.toArray(new String[0]);

    }

    public synchronized String[] queryUniqueIdentifiersForFileSystems(
            String arrayUniqueId) throws InvalidArgument, NotFound,
            InvalidSession, StorageFault, NotImplemented {

        final String methodName = "queryUniqueIdentifiersForFileSystems(): ";
        log.debug(methodName + "Entry with input arrayUniqueId["
                + arrayUniqueId + "]");

        List<String> ids = null;

        try {

            Boolean supportsFile = new Boolean(
                    _config.getConfigValue("config/service/storageTopology/storageArray/support-file-profile"));

            if (!supportsFile) {
                log.error(methodName + " This function is not implemented");
                throw FaultUtil
                        .NotImplemented("This function is not implemented");
            }

            if (Util.isEmpty(arrayUniqueId)
                    || !arrayUniqueId
                            .startsWith(STORAGEARRAY_IDENTIFIER_PREFIX)) {
                throw FaultUtil.InvalidArgument("Given array Id is invalid: "
                        + arrayUniqueId);
            }

            String sosArrayId = this.getArrayId();

            if (!sosArrayId.equals(arrayUniqueId)) {
                throw FaultUtil.NotFound("Given array Id is not found: "
                        + arrayUniqueId);
            }

            // run function
            ids = this.getFileSystemIds();
        } catch (NotFound e) {
            log.error(methodName + "NotFound occured ", e);
            throw e;
        } catch (InvalidArgument e) {
            log.error(methodName + "InvalidArgument occured ", e);
            throw e;
        } catch (NotImplemented e) {
            log.error(methodName + "NotImplemented occured ", e);
            throw e;
        }
        log.debug(methodName + "Exit returning ids of size[" + ids.size() + "]");
        return ids.toArray(new String[0]);

    }

    private UsageContext getUsageContext() throws InvalidSession, StorageFault {
        _contextManager = ContextManagerImpl.getInstance();
        UsageContext uc = _contextManager.getUsageContext();
        return uc;
    }

    public boolean isVolumeInvCenterContext(Volume volume)
            throws InvalidSession, StorageFault {

        final String methodName = "isVolumeInvCenterContext(): ";
        log.debug(methodName + "Entry with input: " + volume);

        UsageContext uc = this.getUsageContext();
        HostInitiatorInfo[] hostInitiators = uc.getHostInitiator();

        if (!Util.isEmpty(hostInitiators)) {

            for (HostInitiatorInfo initiator : hostInitiators) {

                if (Util.isEmpty(initiator.getPortWwn())
                        && Util.isEmpty(initiator.getIscsiIdentifier())) {
                    continue;
                }
                if (!Util.isEmpty(initiator.getPortWwn())
                        && !initiator.getPortWwn().startsWith("0x")) {
                    // convert the long into hex string value
                    try {
                        long portWWN = Long.parseLong(initiator.getPortWwn());
                        initiator.setPortWwn("0x"
                                + Long.toHexString(portWWN).toLowerCase());
                    } catch (Exception e) {
                        log.warn(methodName + "Unable to parse portWWN: "
                                + initiator.getPortWwn());
                        continue;
                    }
                }

                Itls volumeExports = volume.getExports();

                List<String> volumeProtocolList = volume.getProtocols()
                        .getProtocol();

                if (volumeExports != null && volumeExports.getItls() != null) {

                    for (Volume.Itls.Itl itl : volumeExports.getItls()) {
                        if (volumeProtocolList.contains("FC")) {

                            String portWWN = initiator.getPortWwn();
                            String initiatorPort = itl.getInitiator().getPort()
                                    .replace(":", "");

                            if (!Util.isEmpty(portWWN)) {
                                portWWN = portWWN.substring(2);

                                log.debug(methodName + " Is portWWN[" + portWWN
                                        + "] equals initiator port["
                                        + initiatorPort + "]");
                                if (initiatorPort.equalsIgnoreCase(portWWN)) {
                                    log.debug(methodName
                                            + "Exit returning [true]");
                                    return true;
                                }
                            }
                        }

                        if (volumeProtocolList.contains("iSCSI")
                                || volumeProtocolList.contains("ISCSI")) {

                            String iscsiId = initiator.getIscsiIdentifier();

                            if (!Util.isEmpty(iscsiId)) {
                                log.debug(methodName + " Is iscsiId[" + iscsiId
                                        + "] equals initiator port["
                                        + itl.getInitiator().getPort() + "]");
                                if (itl.getInitiator().getPort()
                                        .equalsIgnoreCase(iscsiId)) {
                                    log.debug(methodName
                                            + "Exit returning [true]");
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        log.debug(methodName + "Exit returning [false]");
        return false;
    }

    public String getCSVListOfInitiatorsFromUsageContext()
            throws InvalidSession, StorageFault {

        final String methodName = "getCSVListOfInitiatorsFromUsageContext(): ";
        log.debug(methodName + "Entry");

        StringBuffer returnString = new StringBuffer();

        UsageContext uc = this.getUsageContext();
        HostInitiatorInfo[] hostInitiators = uc.getHostInitiator();

        if (!Util.isEmpty(hostInitiators)) {

            for (HostInitiatorInfo initiator : hostInitiators) {

                if (Util.isEmpty(initiator.getPortWwn())
                        && Util.isEmpty(initiator.getIscsiIdentifier())) {
                    continue;
                }
                if (!Util.isEmpty(initiator.getPortWwn())
                        && !initiator.getPortWwn().startsWith("0x")) {
                    // convert the long into hex string value
                    try {
                        long portWWN = Long.parseLong(initiator.getPortWwn());
                        String portWWNString = "0x"
                                + Long.toHexString(portWWN).toLowerCase();
                        StringBuffer portWWNBuffer = new StringBuffer();
                        for (int i = 2; i <= portWWNString.length(); i = i + 2) {
                            String temp = portWWNString.substring(i - 2, i);
                            portWWNBuffer.append(temp).append(":");
                        }
                        portWWNBuffer.deleteCharAt(portWWNBuffer.length() - 1);

                        initiator.setPortWwn(portWWNBuffer.toString());
                    } catch (NumberFormatException e) {
                        log.warn(methodName + "Unable to parse portWWN: "
                                + initiator.getPortWwn());
                        continue;
                    }
                }

                String portWWN = initiator.getPortWwn();
                if (!Util.isEmpty(portWWN)) {
                    returnString.append(portWWN.replace("0x:", "")).append(",");
                }
                String iscsiId = initiator.getIscsiIdentifier();
                if (!Util.isEmpty(iscsiId)) {
                    returnString.append(iscsiId).append(",");
                }
            }
            returnString.deleteCharAt(returnString.length() - 1);
        }

        log.debug(methodName + "Exit returning [" + returnString + "]");

        return returnString.toString();

    }

    public List<String> getMountPathsFromUsageContext() throws InvalidSession,
            StorageFault {

        final String methodName = "getMountPathsFromUsageContext(): ";
        log.debug(methodName + "Entry");

        Set<String> mountPathList = new HashSet<String>();

        UsageContext uc = this.getUsageContext();

        MountInfo[] mountPoints = uc.getMountPoint();

        if (!Util.isEmpty(mountPoints)) {

            for (MountInfo eachMountPointInfo : mountPoints) {

                String filePath = eachMountPointInfo.getFilePath();
                if (!Util.isEmpty(filePath)) {
                    mountPathList.add(filePath);
                }
            }
        }

        log.debug(methodName + "Exit returning [" + mountPathList + "]");

        return new ArrayList<String>(mountPathList);

    }

}
