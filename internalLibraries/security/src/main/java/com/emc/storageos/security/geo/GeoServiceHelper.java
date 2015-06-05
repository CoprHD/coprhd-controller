/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.geo;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedQueue;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.VdcOpLog;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.security.SerializerUtils;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

public class GeoServiceHelper {
    public static final String GEOSVC_QUEUE_NAME = "geoService";
    public static final String VDC_ID_PREFIX = "vdc";

    // We allow only one VDC operation at one time.
    public static final int DEFAULT_MAX_THREADS = 1;
    public static final long DEFAULT_MAX_WAIT_STOP = 60 * 1000;

    private DistributedQueue<GeoServiceJob> _queue;
    private CoordinatorClient _coordinator;

    @Autowired
    private DbClient _dbClient;

    @SuppressWarnings("unused")
    final static private Logger log = LoggerFactory.getLogger(GeoServiceHelper.class);

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    /**
     * Start geosvc job queue
     */
    public void startJobQueue() {
        log.info("Starting geosvc job queue");
        try{
            // no job consumer in geoclient
            _queue = _coordinator.getQueue(GEOSVC_QUEUE_NAME, null, new GeoServiceJobSerializer(), DEFAULT_MAX_THREADS);
        }catch(Exception e){
            log.error("can not startup geosvc job queue", e);
        }
    }

    /**
     * Stop geosvc job queue
     */
    public void stopJobQueue() {
        log.info("Stopping geosvc job queue");
        _queue.stop(DEFAULT_MAX_WAIT_STOP);
    }

    /**
     * Enqueue a job to geosvc
     * @param job The job to be enqueued, including add vdc, disconnect vdc, etc.
     * @throws Exception
     */
    public void enqueueJob(GeoServiceJob job) throws Exception {
        log.info("post job {} task {} type {}", new Object[] {job.getVdcId(), job.getTask(), job.getType()});
        // TODO: have different exception here
        if (_queue == null) {
            startJobQueue();
        }
        _queue.put(job);
    }

    /**
     * creates a URI in Mono for an object of type clazz
     * @return
     */
    public String createMonoVdcId() {
        return String.format("%1$s%2$s", VDC_ID_PREFIX, nextVdcId());
    }

    private int nextVdcId() {
        int max_id = 0;
        List<URI> vdcIds = _dbClient.queryByType(VirtualDataCenter.class, false); // include the inactive ones
        for (URI vdcId : vdcIds) {
            VirtualDataCenter vdc = _dbClient.queryObject(VirtualDataCenter.class, vdcId);
            String id = vdc.getShortId().replace(VDC_ID_PREFIX, "");
            int num = Integer.parseInt(id);
            if (num > max_id)
                max_id = num;
        }
        return max_id + 1;
    }

    public static void backupOperationVdc(DbClient dbClient, GeoServiceJob.JobType jobType, URI operatedVdc, String operationParam) {

        VdcOpLog op = new VdcOpLog();
        op.setId(URIUtil.createId(VdcOpLog.class));
        op.setOperationType(jobType.toString());
        op.setOperatedVdc(operatedVdc);
        try {
            byte[] paramBytes = SerializerUtils.serializeAsByteArray(operationParam);
            op.setOperationParam(paramBytes);
        } catch (Exception e) {
            throw InternalServerErrorException.internalServerErrors
                    .genericApisvcError("Internal error when backup vdc config info", e);
        }

        List<VirtualDataCenter> vdcList = new ArrayList();
        List<URI> ids = dbClient.queryByType(VirtualDataCenter.class, true);
        for (URI id : ids) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, id);
            if (vdc != null) {
                if (vdc.getLocal() ||
                        (!vdc.getLocal() && vdc.getRepStatus() != VirtualDataCenter.GeoReplicationStatus.REP_NONE)) {
                    log.info("Add vdc {} {} to backup list", vdc.getId().toString(), vdc.getShortId());
                    vdcList.add(vdc);
                }
            }
        }
        try {
            byte[] vdcBytes = SerializerUtils.serializeAsByteArray(vdcList);
            op.setVdcConfigInfo(vdcBytes);
        } catch (Exception e) {
            throw InternalServerErrorException.internalServerErrors
                    .genericApisvcError("Internal error when backup vdc config info", e);
        }

        dbClient.createObject(op);
        log.info("Backup vdc config info succeed");
    }

    public static VirtualDataCenter prepareVirtualDataCenter(URI vdcId, VirtualDataCenter.ConnectionStatus connStatus,
                                                              VirtualDataCenter.GeoReplicationStatus replicationStatus,
                                                              Properties vdcProp) {

        VirtualDataCenter vdc = new VirtualDataCenter();
        vdc.setId(vdcId);
        vdc.setShortId(vdcProp.getProperty(GeoServiceJob.VDC_SHORT_ID));
        vdc.setConnectionStatus(connStatus);
        vdc.setRepStatus(replicationStatus);
        Date init_date = new Date();
        vdc.setVersion(init_date.getTime()); // timestamp for the new vdc record
        vdc.setLocal(false);

        vdc.setLabel(vdcProp.getProperty(GeoServiceJob.VDC_NAME));
        vdc.setApiEndpoint(vdcProp.getProperty(GeoServiceJob.VDC_API_ENDPOINT));
        vdc.setSecretKey(vdcProp.getProperty(GeoServiceJob.VDC_SECRETE_KEY));
        vdc.setDescription(vdcProp.getProperty(GeoServiceJob.VDC_DESCRIPTION));

        vdc.setGeoCommandEndpoint(vdcProp.getProperty(GeoServiceJob.VDC_GEODATA_ENDPOINT));
        vdc.setGeoDataEndpoint(vdcProp.getProperty(GeoServiceJob.VDC_GEODATA_ENDPOINT));

        vdc.setCertificateChain(vdcProp.getProperty(GeoServiceJob.VDC_CERTIFICATE_CHAIN));
        return vdc;
    }


    public static Properties getVDCInfo(VirtualDataCenter vdc){
        Properties vdcProp = new Properties();
        if( vdc.getLabel()!= null ) {
            vdcProp.setProperty(GeoServiceJob.VDC_NAME, vdc.getLabel());
        }
        if( vdc.getApiEndpoint() != null ) {
            vdcProp.setProperty(GeoServiceJob.VDC_API_ENDPOINT, vdc.getApiEndpoint());
        }
        if( vdc.getSecretKey() != null ) {
            vdcProp.setProperty(GeoServiceJob.VDC_SECRETE_KEY, vdc.getSecretKey());
        }
        if( vdc.getDescription() != null ) {
            vdcProp.setProperty(GeoServiceJob.VDC_DESCRIPTION, vdc.getDescription());
        }
        if( vdc.getGeoCommandEndpoint()!= null ) {
            vdcProp.setProperty(GeoServiceJob.VDC_GEOCOMMAND_ENDPOINT, vdc.getGeoCommandEndpoint());
        }
        if( vdc.getGeoDataEndpoint() != null ) {
            vdcProp.setProperty(GeoServiceJob.VDC_GEODATA_ENDPOINT, vdc.getGeoDataEndpoint());
        }
        if( vdc.getCertificateChain() != null ) {
            vdcProp.setProperty(GeoServiceJob.VDC_CERTIFICATE_CHAIN, vdc.getCertificateChain());
        }
        if( vdc.getShortId() != null ) {
            vdcProp.setProperty(GeoServiceJob.VDC_SHORT_ID, vdc.getShortId());
        }
        vdcProp.setProperty(GeoServiceJob.OPERATED_VDC_ID, vdc.getId().toString());
        return vdcProp;
    }
}
