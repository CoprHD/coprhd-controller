package com.emc.storageos.api.service.impl.resource;

import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.file.FilePolicyParam;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

@Path("/file/filePolicies")
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.TENANT_ADMIN })
public class FilePolicyService extends TaskResourceService {
    private static final Logger _log = LoggerFactory.getLogger(FilePolicyService.class);

    protected static final String EVENT_SERVICE_SOURCE = "FilePolicyService";

    private static final String EVENT_SERVICE_TYPE = "FilePolicy";

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private NetworkService networkSvc;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Override
    protected DataObject queryResource(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        // TODO Auto-generated method stub
        return null;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public FilePolicyParam createFilePolicy(FilePolicyParam policyParam) {
        // Some validations..
        _log.info("file policy creation started -- ");
        if (policyParam.getPolicyType().equals(FilePolicyParam.PolicyType.file_replication.name())) {
            FilePolicy filePolicy = new FilePolicy();
            filePolicy.setId(URIUtil.createId(FilePolicy.class));
            filePolicy.setFilePolicyName(policyParam.getPolicyName());
            filePolicy.setFileReplicationType(policyParam.getReplicationSettingParam().getReplicationType());
            filePolicy.setFileReplicationCopyType(policyParam.getReplicationSettingParam().getReplicationCopyType());
            _dbClient.createObject(filePolicy);
            _log.info("Policy {} created successfully", filePolicy);

        } else if (policyParam.getPolicyType().equals(FilePolicyParam.PolicyType.file_snapshot.name())) {

        } else if (policyParam.getPolicyType().equals(FilePolicyParam.PolicyType.file_quota.name())) {

        }
        return policyParam;
    }
}
