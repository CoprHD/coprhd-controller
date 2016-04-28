/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.imageservercontroller;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.ComputeImageJob;
import com.emc.storageos.db.client.model.ComputeImageJob.JobStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.Host.ProvisioningJobStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.audit.AuditLogManagerFactory;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

@SuppressWarnings("serial")
public class OsInstallCompleter extends TaskCompleter {

    private static final Logger log = LoggerFactory.getLogger(OsInstallCompleter.class);

    private String serviceType = null;
    private URI jobId = null;

    public OsInstallCompleter(URI id, String opId, URI jobId, String serviceType) {
        super(Host.class, id, opId);
        this.jobId = jobId;
        this.serviceType = serviceType;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        log.info("OsInstallCompleter.complete {} {}", status.name(), coded);

        Host host = dbClient.queryObject(Host.class, getId());
        ComputeImageJob job = dbClient.queryObject(ComputeImageJob.class, jobId);

        AuditLogManager auditMgr = AuditLogManagerFactory.getAuditLogManager();
        if (status == Status.ready && job.getJobStatus().equals(JobStatus.SUCCESS.name())) {
            // set host type based on image type
            ComputeImage image = dbClient.queryObject(ComputeImage.class, job.getComputeImageId());

            if (image.getImageType().equals(ComputeImage.ImageType.esx.name())) {
                host.setType(Host.HostType.Esx.name());
                host.setOsVersion(image.getOsVersion());
            } else if (image.getImageType().equals(ComputeImage.ImageType.linux.name())) {
                host.setType(Host.HostType.Linux.name());
                host.setOsVersion(String.format("%s %s", image.getOsName(), image.getOsVersion()));
            }
            /*
             * Create the IpInterface that represents the IpAddress that's
             * supposed to come on the ESX Management Network (for ESX
             * installations)
             */

            IpInterface ipInterface = new IpInterface();
            ipInterface.setHost(host.getId());
            ipInterface.setId(URIUtil.createId(IpInterface.class));
            ipInterface.setIpAddress(job.getHostIp());
            ipInterface.setLabel(job.getHostName());
            ipInterface.setNetmask(job.getNetmask());
            ipInterface.setProtocol(HostInterface.Protocol.IPV4.toString());
            ipInterface.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.toString());

            dbClient.createObject(ipInterface);

            /*
             * End create IpInterface. Consider making this a seperate method.
             */

            host.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
            host.setProvisioningStatus(ProvisioningJobStatus.COMPLETE.toString());
            dbClient.persistObject(host);

            dbClient.ready(Host.class, getId(), getOpId());
            auditMgr.recordAuditLog(null, null, serviceType, OperationTypeEnum.INSTALL_COMPUTE_IMAGE,
                    System.currentTimeMillis(), AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END,
                    host.getId(), job.getId());
        } else {
            host.setProvisioningStatus(ProvisioningJobStatus.ERROR.toString());
            dbClient.persistObject(host);
            job.setJobStatus(JobStatus.FAILED.name());
            dbClient.persistObject(job);
            dbClient.error(Host.class, getId(), getOpId(), coded);
            auditMgr.recordAuditLog(null, null, serviceType, OperationTypeEnum.INSTALL_COMPUTE_IMAGE,
                    System.currentTimeMillis(), AuditLogManager.AUDITLOG_FAILURE, AuditLogManager.AUDITOP_END,
                    host.getId(), job.getId());
        }
    }
}
