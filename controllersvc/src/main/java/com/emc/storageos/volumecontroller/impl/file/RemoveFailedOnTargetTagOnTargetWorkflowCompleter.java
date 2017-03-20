package com.emc.storageos.volumecontroller.impl.file;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.NFSShareACL;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class RemoveFailedOnTargetTagOnTargetWorkflowCompleter extends FileWorkflowCompleter {

    private static final long serialVersionUID = 8900789883362531347L;

    public RemoveFailedOnTargetTagOnTargetWorkflowCompleter(List<URI> fsUris, String task) {
        super(fsUris, task);
    }

    public RemoveFailedOnTargetTagOnTargetWorkflowCompleter(URI id, String taskId) {
        super(id, taskId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded serviceCoded) {
        if (Status.ready == status) {
            removeNFSACLTags(dbClient, getId());
        }
        super.complete(dbClient, status, serviceCoded);
    }

    private void removeNFSACLTags(DbClient dbClient, URI id) {

        ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory
                .getFileNfsAclsConstraint(id);

        List<NFSShareACL> nfsAclList = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient,
                        NFSShareACL.class, containmentConstraint);

        if (nfsAclList != null && !nfsAclList.isEmpty()) {
            for (Iterator<NFSShareACL> iterator = nfsAclList.iterator(); iterator.hasNext();) {
                NFSShareACL nfsShareACL = iterator.next();
                nfsShareACL.setTag(new ScopedLabelSet());
            }
            dbClient.updateObject(nfsAclList);
        }
    }
}
