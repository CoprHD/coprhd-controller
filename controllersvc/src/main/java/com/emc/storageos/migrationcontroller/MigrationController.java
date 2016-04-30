package com.emc.storageos.migrationcontroller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.Controller;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.workflow.Workflow;

public interface MigrationController extends Controller {

    public String createWorkflowStepsForBlockVolumeExport(Workflow workFlow,
            List<URI> volumeURIs, URI hostURI, String waitFor)
            throws InternalException;

    public String createWorkflowStepsForMigrateGeneralVolumes(Workflow workflow, URI storageURI,
            URI generalVolumeURI, List<URI> targetVolumeURIs,
            URI newVpoolURI, URI newVarrayURI, Map<URI, URI> migrationsMap, String waitFor)
            throws InternalException;

    public String createWorkflowStepsForCommitMigration(Workflow workflow, URI storageURI,
            URI generalVolumeURI, Map<URI, URI> migrationsMap, String waitFor)
            throws InternalException;

    public String createWorkflowStepsForDeleteMigrationSource(Workflow workflow, URI storageURI,
            URI generalVolumeURI, URI newVpoolURI, URI newVarrayURI, Map<URI, URI> migrationsMap,
            String waitFor) throws InternalException;

    public String createWorkflowStepsForDeleteConsistencyGroup(Workflow workflow, URI cgURI,
            List<URI> localSystemsToRemoveCG, String lastStep) throws InternalException;
}
