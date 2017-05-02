/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.metering.vnxfile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.AccessPolicy;
import com.emc.nas.vnxfile.xmlapi.CelerraSystemQueryParams;
import com.emc.nas.vnxfile.xmlapi.CheckpointQueryParams;
import com.emc.nas.vnxfile.xmlapi.CifsOptions;
import com.emc.nas.vnxfile.xmlapi.CifsServerQueryParams;
import com.emc.nas.vnxfile.xmlapi.DeleteCheckpoint;
import com.emc.nas.vnxfile.xmlapi.DeleteFileSystem;
import com.emc.nas.vnxfile.xmlapi.DeleteMount;
import com.emc.nas.vnxfile.xmlapi.DeleteNfsExport;
import com.emc.nas.vnxfile.xmlapi.DeleteTree;
import com.emc.nas.vnxfile.xmlapi.ExtendFileSystem;
import com.emc.nas.vnxfile.xmlapi.ExtendFileSystem.StoragePool;
import com.emc.nas.vnxfile.xmlapi.ExtendFileSystem.StoragePool.EnableAutoExt;
import com.emc.nas.vnxfile.xmlapi.FileSystem;
import com.emc.nas.vnxfile.xmlapi.FileSystemAlias;
import com.emc.nas.vnxfile.xmlapi.FileSystemQueryParams;
import com.emc.nas.vnxfile.xmlapi.FileSystemQueryParams.AspectSelection;
import com.emc.nas.vnxfile.xmlapi.FileSystemType;
import com.emc.nas.vnxfile.xmlapi.FileSystemUsageSet;
import com.emc.nas.vnxfile.xmlapi.LockingPolicy;
import com.emc.nas.vnxfile.xmlapi.ModifyFileSystem;
import com.emc.nas.vnxfile.xmlapi.ModifyFileSystem.AutoExtend;
import com.emc.nas.vnxfile.xmlapi.ModifyTreeQuota;
import com.emc.nas.vnxfile.xmlapi.MountQueryParams;
import com.emc.nas.vnxfile.xmlapi.MoverOrVdmRef;
import com.emc.nas.vnxfile.xmlapi.MoverQueryParams;
import com.emc.nas.vnxfile.xmlapi.MoverRef;
import com.emc.nas.vnxfile.xmlapi.MoverStatsSetQueryParams;
import com.emc.nas.vnxfile.xmlapi.MoverStatsSetType;
import com.emc.nas.vnxfile.xmlapi.NewCheckpoint;
import com.emc.nas.vnxfile.xmlapi.NewFileSystem;
import com.emc.nas.vnxfile.xmlapi.NewMount;
import com.emc.nas.vnxfile.xmlapi.NewTree;
import com.emc.nas.vnxfile.xmlapi.NfsExportQueryParams;
import com.emc.nas.vnxfile.xmlapi.NfsOptions;
import com.emc.nas.vnxfile.xmlapi.Query;
import com.emc.nas.vnxfile.xmlapi.QueryStats;
import com.emc.nas.vnxfile.xmlapi.QuotaLimits;
import com.emc.nas.vnxfile.xmlapi.RestoreCheckpoint;
import com.emc.nas.vnxfile.xmlapi.StoragePoolQueryParams;
import com.emc.nas.vnxfile.xmlapi.Task;
import com.emc.nas.vnxfile.xmlapi.TreeQuotaQueryParams;
import com.emc.nas.vnxfile.xmlapi.UserAccountQueryParams;
import com.emc.nas.vnxfile.xmlapi.VdmQueryParams;
import com.emc.nas.vnxfile.xmlapi.VolumeStatsSetQueryParams;
import com.emc.nas.vnxfile.xmlapi.VolumeStatsSetType;
import com.emc.storageos.plugins.common.ArgsCreator;
import com.emc.storageos.plugins.common.Util;
import com.emc.storageos.plugins.common.domainmodel.Argument;

/**
 * This class is implemented to create the VNXFile input request xml
 * 
 */
public class VNXFileArgsCreator extends ArgsCreator {
    private static final Logger _logger = LoggerFactory
            .getLogger(VNXFileArgsCreator.class);

    // Auto extension high water mark
    private static final int EXT_HW_MARK = 90;

    // Minimum VNX File file system array size in MB
    private static final Long MIN_FILESYSTEM_SIZE = 2L;

    private VNXFileInputRequestBuilder _vnxFileInputRequestBuilder = null;

    public VNXFileArgsCreator(Util util) {
        super(util);
    }

    public void setVnxFileRequest(VNXFileInputRequestBuilder vnxFileInputRequestBuilder) {
        _vnxFileInputRequestBuilder = vnxFileInputRequestBuilder;
    }

    /**
     * create a FileSystemUsage XML request and returns a stream after marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchFileSystemUsageStats(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Creating filesystem usage query");
        InputStream iStream = null;
        try {
            QueryStats queryStats = new QueryStats();
            FileSystemUsageSet fsUsageSet = new FileSystemUsageSet();
            queryStats.setFileSystemUsage(fsUsageSet);
            iStream = _vnxFileInputRequestBuilder.getSingleQueryStatsPacket(queryStats);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for fileSystem usage info",
                    jaxbException.getCause());
        }
        return iStream;
    }

    /**
     * Create VNX Information input request xml and return its stream after
     * marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchCelerraSystemInfo(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Creating celerra system query");
        InputStream iStream = null;
        try {
            Query query = new Query();
            CelerraSystemQueryParams celerraParams = new CelerraSystemQueryParams();
            query.getQueryRequestChoice().add(celerraParams);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(celerraParams, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for celerra system info",
                    jaxbException.getCause());
        }
        return iStream;

    }

    /**
     * Create VNX Information input request xml and return its stream after
     * marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchStoragePoolInfo(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Creating VNX StorgePool Query");
        InputStream iStream = null;
        try {
            Query query = new Query();
            StoragePoolQueryParams spParams = new StoragePoolQueryParams();
            query.getQueryRequestChoice().add(spParams);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(spParams, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for celerra system info",
                    jaxbException.getCause());
        }
        return iStream;

    }

    /**
     * Create VNX Information input request xml and return its stream after
     * marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchStoragePortGroupInfo(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Creating VNX Port group Query...");
        InputStream iStream = null;
        try {
            Query query = new Query();
            MoverQueryParams dataMovers = new MoverQueryParams();
            com.emc.nas.vnxfile.xmlapi.MoverQueryParams.AspectSelection selection = new com.emc.nas.vnxfile.xmlapi.MoverQueryParams.AspectSelection();
            selection.setMovers(true);
            dataMovers.setAspectSelection(selection);
            query.getQueryRequestChoice().add(dataMovers);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(dataMovers, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for celerra port group info",
                    jaxbException.getCause());
        }
        return iStream;

    }

    /**
     * Create VNX Information input request xml and return its stream after
     * marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchStoragePortInfo(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Creating VNX Port query");
        InputStream iStream = null;
        try {
            Query query = new Query();
            MoverQueryParams dataMovers = new MoverQueryParams();
            com.emc.nas.vnxfile.xmlapi.MoverQueryParams.AspectSelection selection = new com.emc.nas.vnxfile.xmlapi.MoverQueryParams.AspectSelection();
            selection.setMoverInterfaces(true);
            dataMovers.setAspectSelection(selection);

            query.getQueryRequestChoice().add(dataMovers);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(dataMovers, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for celerra storage port info",
                    jaxbException.getCause());
        }
        return iStream;

    }

    /**
     * create checkpoint information query and returns its stream after marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchCheckpointInfo(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Creating checkpoint info query");
        InputStream iStream = null;
        try {
            Query query = new Query();
            CheckpointQueryParams ckptParams = new CheckpointQueryParams();
            query.getQueryRequestChoice().add(ckptParams);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(ckptParams, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for celerra system info",
                    jaxbException.getCause());
        }
        return iStream;

    }

    /**
     * create Mount query and returns its stream after marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchMountFSInfo(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("VNX File System Mount info query");
        InputStream iStream = null;
        try {
            Query query = new Query();
            MountQueryParams mountQueryParams = new MountQueryParams();
            query.getQueryRequestChoice().add(mountQueryParams);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(mountQueryParams, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for celerra system info",
                    jaxbException.getCause());
        }
        return iStream;

    }

    /**
     * Create Filesystem information input XML request and returns stream after marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchFileSystemInfo(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Creating filesystem info query");
        InputStream iStream = null;
        try {
            Query query = new Query();
            FileSystemQueryParams fsQueryParam = new FileSystemQueryParams();
            AspectSelection selection = new AspectSelection();
            selection.setFileSystems(true);
            fsQueryParam.setAspectSelection(selection);
            query.getQueryRequestChoice().add(fsQueryParam);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(fsQueryParam, true);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for fileSystem info",
                    jaxbException.getCause());
        }
        return iStream;
    }

    /**
     * Create Filesystem information and FileSystem capcacity input XML request and returns stream after marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchFileSystemInfoWithSize(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Creating filesystem info query");
        InputStream iStream = null;
        try {
            Query query = new Query();
            FileSystemQueryParams fsQueryParam = new FileSystemQueryParams();
            AspectSelection selection = new AspectSelection();
            selection.setFileSystems(true);
            selection.setFileSystemCapacityInfos(true);
            fsQueryParam.setAspectSelection(selection);
            query.getQueryRequestChoice().add(fsQueryParam);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(fsQueryParam, true);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for fileSystem info",
                    jaxbException.getCause());
        }
        return iStream;
    }

    /**
     * Create Filesystem information and FileSystem capcacity input XML request and returns stream after marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchSelectedFileSystemInfo(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Creating filesystem info query");
        InputStream iStream = null;
        try {
            Query query = new Query();
            FileSystemQueryParams fsQueryParam = new FileSystemQueryParams();
            AspectSelection selection = new AspectSelection();
            selection.setFileSystems(true);
            selection.setFileSystemCapacityInfos(true);
            fsQueryParam.setAspectSelection(selection);
            FileSystemAlias fsAlias = new FileSystemAlias();
            fsAlias.setName((String) keyMap.get(VNXFileConstants.FILESYSTEM_NAME));
            fsQueryParam.setAlias(fsAlias);
            query.getQueryRequestChoice().add(fsQueryParam);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(fsQueryParam, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for fileSystem info",
                    jaxbException.getCause());
        }
        return iStream;
    }

    /**
     * Creates File Export input XML request and returns stream after marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchFileExportInfo(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Creating File Export info query.");
        InputStream iStream = null;
        try {
            Query query = new Query();
            NfsExportQueryParams nfsExportQueryParam = new NfsExportQueryParams();
            query.getQueryRequestChoice().add(nfsExportQueryParam);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(nfsExportQueryParam, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for file export info",
                    jaxbException.getCause());
        }
        return iStream;
    }

    /**
     * Create CIFS Config XML request and return stream after marhalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @throws com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException
     * 
     */
    public InputStream fetchCifsServerParams(final Argument argument,
            final Map<String, Object> keyMap,
            int index)
            throws VNXFilePluginException {
        _logger.info("Creating CIFS Server Params Query");
        InputStream iStream = null;

        try {
            String moverId = (String) keyMap.get(VNXFileConstants.MOVER_ID);
            String isVDM = (String) keyMap.get(VNXFileConstants.ISVDM);

            CifsServerQueryParams cifsQuery = new CifsServerQueryParams();

            MoverOrVdmRef mover = new MoverOrVdmRef();
            Boolean moverIsVdm = new Boolean(false);
            if (moverId != null) {
                mover.setMover(moverId);
                if (isVDM != null) {
                    if (isVDM.equalsIgnoreCase("true")) {
                        moverIsVdm = new Boolean(true);
                    }
                }
                mover.setMoverIdIsVdm(moverIsVdm);
                cifsQuery.setMoverOrVdm(mover);
            }

            Query query = new Query();
            query.getQueryRequestChoice().add(cifsQuery);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(cifsQuery, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for  Cifs server info",
                    jaxbException.getCause());
        }

        return iStream;
    }

    /**
     * Create volume stats XML request query and returns a stream after marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    @SuppressWarnings("unchecked")
    public InputStream fetchVolumeStats(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Creating volume stats query");
        InputStream iStream = null;
        List<QueryStats> statList = new ArrayList<QueryStats>();
        try {
            Set<String> movers = (Set<String>) keyMap.get(VNXFileConstants.MOVERLIST);
            if (null != movers && !movers.isEmpty()) {
                for (String moverID : movers) {
                    QueryStats queryStats = new QueryStats();
                    VolumeStatsSetQueryParams params = new VolumeStatsSetQueryParams();
                    params.setStatsSet(VolumeStatsSetType.ALL);
                    params.setMover(moverID);
                    queryStats.setVolumeStats(params);
                    statList.add(queryStats);
                }
                iStream = _vnxFileInputRequestBuilder.getMultiRequestQueryStatsPacket(statList);
            } else {
                _logger.error("No movers found to construct volumeStats query.");
            }
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while fetching fileSystem info",
                    jaxbException.getCause());
        }
        return iStream;
    }

    public InputStream fetchDataMoverInfo(final Argument argument,
            final Map<String, Object> keyMap,
            int index) throws VNXFilePluginException {
        _logger.info("Creating data mover info query");
        InputStream iStream = null;
        try {
            Query query = new Query();
            MoverQueryParams moverQuery = new MoverQueryParams();
            MoverQueryParams.AspectSelection selection = new MoverQueryParams.AspectSelection();
            selection.setMovers(true);
            moverQuery.setAspectSelection(selection);
            query.getQueryRequestChoice().add(moverQuery);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(moverQuery, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for datamover info",
                    jaxbException.getCause());
        }
        return iStream;
    }

    public InputStream fetchVdmInfo(final Argument argument,
            final Map<String, Object> keyMap,
            int index) throws VNXFilePluginException {
        _logger.info("Creating VDM info query");
        InputStream iStream = null;
        try {
            Query query = new Query();
            VdmQueryParams vdmQuery = new VdmQueryParams();
            query.getQueryRequestChoice().add(vdmQuery);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(vdmQuery, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for VDM info",
                    jaxbException.getCause());
        }
        return iStream;
    }

    /**
     * create Mover stats query and returns its stream after marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchMoverStats(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("VNX Mover Stats query");
        InputStream iStream = null;
        List<QueryStats> statsList = new ArrayList<QueryStats>();
        try {
            Set<String> movers = (Set<String>) keyMap.get(VNXFileConstants.MOVERLIST);
            if (null != movers && !movers.isEmpty()) {
                for (String moverID : movers) {
                    QueryStats queryStats = new QueryStats();
                    MoverStatsSetQueryParams moverStatsSetQueryParams = new MoverStatsSetQueryParams();
                    moverStatsSetQueryParams.setStatsSet(MoverStatsSetType.NETWORK_DEVICES);
                    moverStatsSetQueryParams.setMover(moverID);
                    queryStats.setMoverStats(moverStatsSetQueryParams);
                    statsList.add(queryStats);
                }
                iStream = _vnxFileInputRequestBuilder.getMultiRequestQueryStatsPacket(statsList);
            } else {
                _logger.error("No movers found to construct volumeStats query.");
            }

        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for celerra mover stats",
                    jaxbException.getCause());
        }
        return iStream;
    }

    /**
     * create Mover interface info query and returns its stream after marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchMoverInterfacesInfo(final Argument argument,
            final Map<String, Object> keyMap,
            int index) throws VNXFilePluginException {
        _logger.info("mover interfaces info query");
        InputStream iStream = null;
        try {
            Query query = new Query();
            MoverQueryParams moverQuery = new MoverQueryParams();
            MoverQueryParams.AspectSelection selection = new MoverQueryParams.AspectSelection();
            selection.setMoverNetworkDevices(true);
            moverQuery.setAspectSelection(selection);
            query.getQueryRequestChoice().add(moverQuery);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(moverQuery, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for datamover info",
                    jaxbException.getCause());
        }
        return iStream;
    }

    /**
     * Performs a query for the user accounts on the specified data mover.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return iStream
     * @throws VNXFilePluginException
     */
    public InputStream fetchUserAccounts(final Argument argument,
            final Map<String, Object> keyMap,
            int index) throws VNXFilePluginException {
        _logger.info("Creating User Accounts Query");
        InputStream iStream = null;
        try {
            String dataMover = (String) keyMap.get(VNXFileConstants.DATAMOVER_ID);
            _logger.info("using data mover {}", dataMover);
            Query query = new Query();
            UserAccountQueryParams userQuery = new UserAccountQueryParams();
            userQuery.setMover(dataMover);
            query.getQueryRequestChoice().add(userQuery);
            iStream = _vnxFileInputRequestBuilder.getQueryExParamPacket(userQuery);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for user account info",
                    jaxbException.getCause());
        }

        return iStream;
    }

    /**
     * Create Quota Tree information input XML request and returns stream after marshalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @return
     * @throws VNXFilePluginException
     */
    public InputStream fetchQuotaDirInfo(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Creating quota tree info query");
        InputStream iStream = null;
        try {
            Query query = new Query();

            // Verify that the prior command quota create/update executed properly.
            verifyPreviousResults(keyMap);

            TreeQuotaQueryParams queryParam = new TreeQuotaQueryParams();
            TreeQuotaQueryParams.AspectSelection selection = new TreeQuotaQueryParams.AspectSelection();
            selection.setTreeQuotas(true);
            queryParam.setAspectSelection(selection);

            // Set the parent file system.
            String fsId = (String) keyMap.get(VNXFileConstants.FILESYSTEM_ID);
            if (!isInValid(fsId)) {
                queryParam.setFileSystem(fsId);
            }

            query.getQueryRequestChoice().add(queryParam);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(queryParam, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for fileSystem info",
                    jaxbException.getCause());
        }
        return iStream;
    }

    public InputStream createFileSystem(final Argument argument,
            final Map<String, Object> keyMap,
            int index) throws VNXFilePluginException {
        _logger.info("Creating a new VNX File file system");

        InputStream iStream = null;
        try {

            // Verify that the prior command executed properly.
            verifyPreviousResults(keyMap);

            Task task = new Task();
            NewFileSystem newFs = new NewFileSystem();

            String fsName = (String) keyMap.get(VNXFileConstants.FILESYSTEM_NAME);
            String poolName = (String) keyMap.get(VNXFileConstants.POOL_NAME);
            Long fsSize = (Long) keyMap.get(VNXFileConstants.FS_INIT_SIZE);
            Set<String> movers = (Set<String>) keyMap.get(VNXFileConstants.MOVERLIST);
            Boolean virtualProvisioning = (Boolean) keyMap.get(VNXFileConstants.FILESYSTEM_VIRTUAL_PROVISIONING);

            if (isInValid(fsName) || isInValid(poolName) || null == fsSize || null == movers || (movers.isEmpty())) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            String dataMover = null;
            if (!movers.isEmpty()) {
                Iterator<String> iter = movers.iterator();
                dataMover = iter.next();
            }

            _logger.debug("new file system name: {}", fsName);
            _logger.debug("using virtual prov: {}", virtualProvisioning);
            newFs.setName(fsName);

            StoragePool pool = new StoragePool();
            pool.setPool(poolName);
            pool.setVirtualProvisioning(virtualProvisioning);

            if (virtualProvisioning) {
                EnableAutoExt autoExt = new EnableAutoExt();
                autoExt.setAutoExtensionMaxSize(fsSize);
                autoExt.setHighWaterMark(EXT_HW_MARK);

                pool.setEnableAutoExt(autoExt);
                pool.setSize(MIN_FILESYSTEM_SIZE);
            } else {
                pool.setSize(fsSize);
            }

            newFs.setStoragePool(pool);
            newFs.setType(FileSystemType.UXFS);

            MoverRef mvRef = new MoverRef();
            mvRef.setMover(dataMover);
            newFs.setMover(mvRef);

            task.setNewFileSystem(newFs);
            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task, true);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for creating new file system",
                    jaxbException.getCause());
        }

        return iStream;
    }

    public InputStream createSnapshot(final Argument argument,
            final Map<String, Object> keyMap,
            int index) throws VNXFilePluginException {
        _logger.info("ArgsCreator: Create VNX Snapshot");

        InputStream iStream = null;
        try {
            // Verify that the prior command executed properly.
            verifyPreviousResults(keyMap);

            Task task = new Task();
            NewCheckpoint snap = new NewCheckpoint();

            String fsId = (String) keyMap.get(VNXFileConstants.FILESYSTEM_ID);
            String name = (String) keyMap.get(VNXFileConstants.SNAPSHOT_NAME);
            _logger.debug("snapshot for file system id: {}", fsId);

            if (isInValid(fsId) || isInValid(name)) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            snap.setCheckpointOf(fsId);
            snap.setName(name);
            task.setNewCheckpoint(snap);
            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for delete file system",
                    jaxbException.getCause());
        }

        return iStream;
    }

    public InputStream createQuotaDirectory(final Argument argument,
            final Map<String, Object> keyMap,
            int index) throws VNXFilePluginException {
        _logger.info("ArgsCreator: Create VNX QuotaDirectory....");

        InputStream iStream = null;
        try {

            // Verify that the prior command executed properly.
            verifyPreviousResults(keyMap);

            Task task = new Task();
            NewTree quotaTree = new NewTree();
            QuotaLimits quota = new QuotaLimits();

            String fsId = (String) keyMap.get(VNXFileConstants.FILESYSTEM_ID);
            String name = (String) keyMap.get(VNXFileConstants.QUOTA_DIR_NAME);
            Long hardQuota = (Long) keyMap.get(VNXFileConstants.HARD_QUOTA);
            Long softQuota = (Long) keyMap.get(VNXFileConstants.SOFT_QUOTA);
            _logger.info("Quota directory for file system id: {}", fsId);

            if (isInValid(fsId) || isInValid(name)) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            // Set the Quota limits
            if (hardQuota != null) {
                quota.setSpaceHardLimit(hardQuota.intValue());
                // If we do not specify soft-limit, even hard limit values shows unlimited size.
                // so, setting the soft-limit same as hard-limit.
                quota.setSpaceSoftLimit(hardQuota.intValue());
            }

            if (softQuota != null) {
                quota.setSpaceSoftLimit(softQuota.intValue());
            }

            _logger.info("fsId {}, name {}", fsId, name);
            quotaTree.setFileSystem(fsId);
            final String quotaDirPath = "/" + name;
            quotaTree.setPath(quotaDirPath);
            quotaTree.setLimits(quota);
            task.setNewTree(quotaTree);
            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for create qTree",
                    jaxbException.getCause());
        }

        return iStream;
    }

    public InputStream deleteQuotaDirectory(final Argument argument,
            final Map<String, Object> keyMap,
            int index) throws VNXFilePluginException {
        _logger.info("ArgsCreator: Delete VNX QuotaDirectory...");

        InputStream iStream = null;
        try {

            // Verify that the prior command executed properly.
            verifyPreviousResults(keyMap);

            Task task = new Task();
            DeleteTree quotaTree = new DeleteTree();

            String fsId = (String) keyMap.get(VNXFileConstants.FILESYSTEM_ID);
            String name = (String) keyMap.get(VNXFileConstants.QUOTA_DIR_NAME);
            _logger.info("QuotaDirectory delation for file system id: {}", fsId);

            if (isInValid(fsId) || isInValid(name)) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            _logger.info("fsId {}, name {}", fsId, name);
            quotaTree.setFileSystem(fsId);
            quotaTree.setPath("/" + name);
            quotaTree.setTaskDescription("Deleting the quota directory.");
            task.setDeleteTree(quotaTree);
            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for delete quota tree",
                    jaxbException.getCause());
        }

        return iStream;
    }

    public InputStream modifyQuotaDirectory(final Argument argument,
            final Map<String, Object> keyMap,
            int index) throws VNXFilePluginException {
        _logger.info("ArgsCreator: Modify VNX QuotaDirectory...");

        InputStream iStream = null;
        try {

            // Verify that the prior command executed properly.
            verifyPreviousResults(keyMap);

            Task task = new Task();
            ModifyTreeQuota quotaTree = new ModifyTreeQuota();
            QuotaLimits quota = new QuotaLimits();

            String fsId = (String) keyMap.get(VNXFileConstants.FILESYSTEM_ID);
            String name = (String) keyMap.get(VNXFileConstants.QUOTA_DIR_NAME);
            Long hardQuota = (Long) keyMap.get(VNXFileConstants.HARD_QUOTA);
            Long softQuota = (Long) keyMap.get(VNXFileConstants.SOFT_QUOTA);
            String fsQuotaPath = (String) keyMap.get(VNXFileConstants.QUOTA_DIR_PATH);

            _logger.info("QuotaDirectory for file system id: {}", fsId);
            _logger.info("Space Quotas: {} {}", hardQuota, softQuota);

            if (isInValid(fsId) || isInValid(name)) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            // Set the Quota limits
            if (hardQuota != null) {
                quota.setSpaceHardLimit(hardQuota.intValue());
                // If we do not specify soft-limit, even hard limit values shows unlimited size.
                // so, setting the soft-limit same as hard-limit.
                quota.setSpaceSoftLimit(hardQuota.intValue());
            }

            if (softQuota != null) {
                quota.setSpaceSoftLimit(softQuota.intValue());
            }

            _logger.info("fsId {}, name {}", fsId, name);
            quotaTree.setFileSystem(fsId);
            if (fsQuotaPath == null || fsQuotaPath.isEmpty()) {
                fsQuotaPath = "/" + name;
            }
            quotaTree.setPath(fsQuotaPath);
            quotaTree.setLimits(quota);
            task.setModifyTreeQuota(quotaTree);
            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for modify qTree",
                    jaxbException.getCause());
        }

        return iStream;
    }

    public InputStream doUnexport(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {

        _logger.info("ArgsCreator: VNX Un-Export");

        InputStream iStream = null;
        try {
            Task task = new Task();
            DeleteNfsExport delFSExport = new DeleteNfsExport();
            String dataMoverId = String.valueOf(keyMap.get(VNXFileConstants.MOVER_ID)); // --
            String path = (String) keyMap.get(VNXFileConstants.MOUNT_PATH);  // --

            if (isInValid(dataMoverId) || isInValid(path)) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            _logger.debug("Deleting export file system mover id: {}, path {}", dataMoverId, path);

            delFSExport.setMover(dataMoverId);
            delFSExport.setPath(path);

            task.setDeleteNfsExport(delFSExport);
            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task);

        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while fetching fileSystem info",
                    jaxbException.getCause());
        }

        return iStream;
    }

    public InputStream deleteFileSystem(final Argument argument,
            final Map<String, Object> keyMap,
            int index) throws VNXFilePluginException {
        _logger.info("ArgsCreator: Deleting VNX File System {}", keyMap.get(VNXFileConstants.FILESYSTEM_ID));

        InputStream iStream = null;
        try {
            Task task = new Task();
            DeleteFileSystem delFS = new DeleteFileSystem();

            String fsId = (String) keyMap.get(VNXFileConstants.FILESYSTEM_ID);

            if (isInValid(fsId)) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            _logger.info("deleting file system id: {}", fsId);
            delFS.setFileSystem(fsId);
            task.setDeleteFileSystem(delFS);

            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for delete file system",
                    jaxbException.getCause());
        }

        return iStream;
    }

    public InputStream deleteSnapshot(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("ArgsCreator: Deleting VNX Snapshot");

        InputStream iStream = null;
        try {
            Task task = new Task();
            DeleteCheckpoint delSnap = new DeleteCheckpoint();
            String snapId = (String) keyMap.get(VNXFileConstants.SNAPSHOT_ID);

            if (isInValid(snapId)) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            _logger.debug("deleting Snapshot id: {}", snapId);
            delSnap.setCheckpoint(snapId);
            task.setDeleteCheckpoint(delSnap);

            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for delete Snapshot ",
                    jaxbException.getCause());
        }

        return iStream;
    }

    public InputStream doExpand(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {

        _logger.info("ArgsCreator: VNX File Expand");

        InputStream iStream = null;
        try {
            // Verify that the prior command executed properly.
            verifyPreviousResults(keyMap);

            Task task = new Task();
            ExtendFileSystem extendFS = new ExtendFileSystem();

            FileSystem fsSystem = (FileSystem) keyMap.get(VNXFileConstants.FILESYSTEM);
            String fsName = (String) keyMap.get(VNXFileConstants.FILESYSTEM_NAME);
            String fsId = (String) keyMap.get(VNXFileConstants.FILESYSTEM_ID);
            Long fsSize = (Long) keyMap.get(VNXFileConstants.FILESYSTEM_SIZE);
            Long fsAllocatedSize = (Long) keyMap.get(VNXFileConstants.ORIGINAL_FS_SIZE);
            Boolean isVirtualProvisioning = (Boolean) keyMap.get(VNXFileConstants.FILESYSTEM_VIRTUAL_PROVISIONING);

            Long fsThinPerAllocSize = (Long) keyMap.get(VNXFileConstants.THIN_FS_ALLOC_SIZE);

            if (null == fsSystem || isInValid(fsName) || isInValid(fsId) || null == fsSize || null == fsAllocatedSize) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            Long fsExpandSize = fsSize - fsAllocatedSize;
            if (fsExpandSize < 0) {
                fsExpandSize = fsSize;
            }
            // How much more I should allocate for you?
            if (isVirtualProvisioning) {
                fsExpandSize = fsThinPerAllocSize;
                if (fsAllocatedSize < fsThinPerAllocSize) {
                    fsExpandSize = fsThinPerAllocSize - fsAllocatedSize;
                }

            }

            _logger.info("Expanding File system size : {}, thin {}", fsExpandSize, isVirtualProvisioning);

            String pool = fsSystem.getStoragePools().get(0);
            String storage = fsSystem.getStorages().get(0);

            if (isInValid(pool) || isInValid(storage)) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            ExtendFileSystem.StoragePool sp = new ExtendFileSystem.StoragePool();
            sp.setPool(pool);
            if (fsExpandSize > 0) {
                sp.setSize(fsExpandSize);
            }
            sp.setStorage(storage);
            _logger.info("Expanding File system using StoragePool : {}, storage {}", pool, storage);
            _logger.info("Expanding File system size : {}, name {}", fsExpandSize, fsName);

            extendFS.setFileSystem(fsSystem.getFileSystem());
            extendFS.setStoragePool(sp);

            task.setExtendFileSystem(extendFS);
            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task);

        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while fetching fileSystem info",
                    jaxbException.getCause());
        }

        return iStream;
    }

    public InputStream doModifyFS(final Argument argument, final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {

        _logger.info("ArgsCreator: VNX File Modify");

        InputStream iStream = null;
        try {
            // Verify that the prior command executed properly.
            verifyPreviousResults(keyMap);

            Task task = new Task();
            ModifyFileSystem modifyFS = new ModifyFileSystem();

            FileSystem fsSystem = (FileSystem) keyMap.get(VNXFileConstants.FILESYSTEM);
            String fsName = (String) keyMap.get(VNXFileConstants.FILESYSTEM_NAME);
            String fsId = (String) keyMap.get(VNXFileConstants.FILESYSTEM_ID);
            Long size = (Long) keyMap.get(VNXFileConstants.FILESYSTEM_SIZE);
            Boolean isVirtualProvisioning = (Boolean) keyMap.get(VNXFileConstants.FILESYSTEM_VIRTUAL_PROVISIONING);

            if (null == fsSystem || isInValid(fsName) || isInValid(fsId) || null == size) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            modifyFS.setVirtualProvisioning(isVirtualProvisioning);

            if (isVirtualProvisioning) {
                AutoExtend autoExtend = new AutoExtend();
                autoExtend.setHighWaterMark(EXT_HW_MARK);
                autoExtend.setAutoExtensionMaxSize(size);
                modifyFS.setAutoExtend(autoExtend);
            }
            _logger.info("Modifying File system max size : {}, name {}", size, fsName);

            modifyFS.setFileSystem(fsSystem.getFileSystem());

            task.setModifyFileSystem(modifyFS);
            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task, true);

        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException("Exception occurred while fetching fileSystem info", jaxbException.getCause());
        }

        return iStream;
    }

    public InputStream doSnapshotRestore(final Argument argument,
            final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {

        _logger.info("ArgsCreator: VNX Snapshot Restore");

        InputStream iStream = null;
        try {
            Task task = new Task();
            RestoreCheckpoint snapRestore = new RestoreCheckpoint();

            String snapId = (String) keyMap.get(VNXFileConstants.SNAPSHOT_ID);
            String snapName = (String) keyMap.get(VNXFileConstants.SNAPSHOT_NAME);
            String fsName = (String) keyMap.get(VNXFileConstants.FILESYSTEM_NAME);

            if (isInValid(fsName) || isInValid(snapName) || isInValid(snapId)) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            _logger.info("Snapshot id  to restore : {}", snapId);
            _logger.info("Snapshot Name : {}, FileSystem name : {}", snapName, fsName);
            snapRestore.setCheckpoint(snapId);

            task.setRestoreCheckpoint(snapRestore);
            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task);

        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while fetching fileSystem info",
                    jaxbException.getCause());
        }

        return iStream;
    }

    public InputStream mountFileSystem(final Argument argument, final Map<String, Object> keyMap, int index)
            throws VNXFilePluginException {
        _logger.info("Mounting VNX File System");

        InputStream iStream = null;
        try {
            Task task = new Task();
            NewMount mount = new NewMount();

            String path = (String) keyMap.get(VNXFileConstants.MOUNT_PATH);
            String id = (String) keyMap.get(VNXFileConstants.FILESYSTEM_ID);
            String isVirtual = (String) keyMap.get(VNXFileConstants.ISVDM);
            Set<String> moverIds = (Set<String>) keyMap.get(VNXFileConstants.MOVERLIST);

            if (isInValid(path) || isInValid(id) || null == moverIds || (moverIds.isEmpty())) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            if (moverIds.isEmpty()) {
                throw new VNXFilePluginException(
                        "No movers found to mount", new Exception());
            }

            String movers[] = moverIds.toArray(new String[0]);

            _logger.info("Mount file system id: {} and isVirtual {}", id, isVirtual);
            _logger.info("Mount file system path: {}, Mover: {} ", path, movers[0]);

            Boolean moverType = new Boolean(isVirtual);
            MoverOrVdmRef mov = new MoverOrVdmRef();
            mov.setMover(movers[0]);
            mov.setMoverIdIsVdm(moverType);

            NfsOptions nfs = new NfsOptions();
            nfs.setPrefetch(false);
            nfs.setRo(false);
            nfs.setUncached(true);
            nfs.setVirusScan(false);

            CifsOptions cif = new CifsOptions();
            cif.setAccessPolicy(AccessPolicy.NATIVE);
            cif.setCifsSyncwrite(true);
            cif.setLockingPolicy(LockingPolicy.NOLOCK);
            cif.setNotify(true);
            cif.setNotifyOnAccess(true);
            cif.setOplock(true);
            cif.setTriggerLevel(128);
            cif.setNotifyOnWrite(true);

            mount.setMoverOrVdm(mov);
            mount.setPath(path);
            mount.setFileSystem(id);
            mount.setNfsOptions(nfs);
            mount.setCifsOptions(cif);

            task.setNewMount(mount);
            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for unmount file system",
                    jaxbException.getCause());
        }

        return iStream;
    }

    public InputStream unmountFileSystem(final Argument argument,
            final Map<String, Object> keyMap,
            int index) throws VNXFilePluginException {
        _logger.info("Unmounting VNX File System");

        InputStream iStream = null;
        try {
            // Verify that the prior command executed properly.
            verifyPreviousResults(keyMap);

            Task task = new Task();
            DeleteMount delMount = new DeleteMount();

            String path = (String) keyMap.get(VNXFileConstants.MOUNT_PATH);
            String mover = (String) keyMap.get(VNXFileConstants.MOVER_ID);
            String isVirtual = (String) keyMap.get(VNXFileConstants.ISVDM);
            Boolean moverType = new Boolean(isVirtual);

            if (isInValid(path) || isInValid(mover)) {
                throw new VNXFilePluginException("Prior command did not execute successfully",
                        VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
            }

            _logger.debug("unmount file system name: {}, Mover: {} ", path, mover);

            delMount.setPath(path);
            delMount.setMover(mover);
            delMount.setMoverIdIsVdm(moverType);

            task.setDeleteMount(delMount);
            iStream = _vnxFileInputRequestBuilder.getTaskParamPacket(task);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for unmount file system",
                    jaxbException.getCause());
        }

        return iStream;
    }

    /**
     * Create CIFS Config XML request and return stream after marhalling.
     * 
     * @param argument
     * @param keyMap
     * @param index
     * @throws com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException
     * 
     */
    public InputStream fetchCifsServerInfo(final Argument argument,
            final Map<String, Object> keyMap,
            // final Boolean moverOrVdm,
            int index)
            throws VNXFilePluginException {
        _logger.info("Creating CIFS Server info Query");
        InputStream iStream = null;

        try {
            _logger.info("Creating CIFS Server info Query for Mover {} {} isVDM? {}",
                    (String) keyMap.get(VNXFileConstants.MOVER_ID) + ":" +
                            (String) keyMap.get(VNXFileConstants.DATAMOVER_NAME),
                    keyMap.get(VNXFileConstants.ISVDM));
            MoverOrVdmRef mover = new MoverOrVdmRef();
            mover.setMover((String) keyMap.get(VNXFileConstants.MOVER_ID));
            mover.setMoverIdIsVdm(Boolean.valueOf((String) keyMap.get(VNXFileConstants.ISVDM)));

            CifsServerQueryParams cifsQuery = new CifsServerQueryParams();
            cifsQuery.setMoverOrVdm(mover);

            Query query = new Query();
            query.getQueryRequestChoice().add(cifsQuery);
            iStream = _vnxFileInputRequestBuilder.getQueryParamPacket(cifsQuery, false);
        } catch (JAXBException jaxbException) {
            throw new VNXFilePluginException(
                    "Exception occurred while generating input xml for file export info",
                    jaxbException.getCause());
        }

        return iStream;
    }

    private void verifyPreviousResults(Map<String, Object> keyMap) throws VNXFilePluginException {
        String result = (String) keyMap.get(VNXFileConstants.CMD_RESULT);
        if (null == result || !result.equals(VNXFileConstants.CMD_SUCCESS)) {
            StringBuilder errorMessage = new StringBuilder("Prior command did not execute successfully -- ");
            errorMessage.append((String) keyMap.get(VNXFileConstants.FAULT_MSG));
            throw new VNXFilePluginException(errorMessage.toString(),
                    VNXFilePluginException.ERRORCODE_ILLEGALARGUMENTEXCEPTION);
        }
    }

    private boolean isInValid(String toValidate) {
        if (null == toValidate || toValidate.trim().equals("")) {
            return true;
        }
        return false;
    }
    /*
     * 
     * 
     * public final InputStream getVolumeStatsQuery( final Argument arg, final
     * Map<String, Object> keyMap, int index) { try { Marshaller marshaller =
     * getMarshaller(RequestPacket.class); RequestPacket packet = new
     * RequestPacket(); List<Integer> movers = (List<Integer>)
     * keyMap.get(VNXFileConstants.MOVERLIST); for (Integer moverID : movers) {
     * Request request = new Request(); QueryStats queryStats = new
     * QueryStats(); VolumeStatsSetQueryParams params = new
     * VolumeStatsSetQueryParams(); params.setStatsSet(VolumeStatsSetType.ALL);
     * params.setMover(moverID.toString()); request.setQueryStats(queryStats);
     * request.getQueryStats().setVolumeStats(params);
     * packet.getRequestOrRequestEx().add(request); } marshaller.marshal(packet,
     * System.out); } catch (JAXBException e) { e.printStackTrace(); } return
     * null; }
     * 
     * public static void main(String arg[]) { VNXFileArgsCreator creator = new
     * VNXFileArgsCreator(); Map<String, Object> keyMap = new HashMap<String,
     * Object>(); List<Integer> movers = new ArrayList<Integer>();
     * movers.add(new Integer(1)); movers.add(new Integer(2));
     * keyMap.put(VNXFileConstants.MOVERS, movers);
     * creator.getVolumeStatsQuery(null, keyMap, 0); }
     * 
     * private Marshaller getMarshaller(Class className) throws JAXBException {
     * JAXBContext context = JAXBContext.newInstance(className); Marshaller
     * jaxbMarshaller = context.createMarshaller(); // output pretty printed
     * jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
     * return jaxbMarshaller; }
     */

}
