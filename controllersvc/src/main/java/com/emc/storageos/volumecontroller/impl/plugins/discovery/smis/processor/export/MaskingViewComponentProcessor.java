/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.MigrationStatus;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.PermissionsKey;

/**
 * This processor is responsible for processing associated Storage Group information for masking views created on array.
 */
public class MaskingViewComponentProcessor extends Processor {
    private Logger logger = LoggerFactory.getLogger(MaskingViewComponentProcessor.class);
    private List<Object> args;
    private DbClient dbClient;
    private static final String ROOT = "root";
    private static final String LABEL = "label";
    private static final String MIGRATION_PROJECT = "Migration_Project";

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        String maskingViewName = null;
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            URI systemId = profile.getSystemId();
            // Storage group names for book-keeping
            @SuppressWarnings("unchecked")
            Set<String> storageGroupNames = (Set<String>) keyMap.get(Constants.MIGRATION_STORAGE_GROUPS);
            Project project = (Project) keyMap.get(MIGRATION_PROJECT);
            if (project == null) {
                project = getMigrationProject();
                keyMap.put(MIGRATION_PROJECT, project);
            }

            CIMObjectPath lunMaskingView = getObjectPathfromCIMArgument(args);
            maskingViewName = lunMaskingView.getKey(Constants.DEVICEID).getValue().toString();
            logger.info("Processing Masking View {} to get associated storage group information", maskingViewName);
            while (it.hasNext()) {
                CIMObjectPath deviceMaskingGroup = it.next();
                String instanceID = deviceMaskingGroup.getKey(Constants.INSTANCEID).getValue().toString();
                instanceID = instanceID.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
                BlockConsistencyGroup storageGroup = checkStorageGroupExistsInDB(instanceID, dbClient);
                if (storageGroup == null) {
                    storageGroup = new BlockConsistencyGroup();
                    storageGroup.setId(URIUtil.createId(BlockConsistencyGroup.class));
                    storageGroup.setLabel(instanceID);
                    // storageGroup.setAlternateLabel(instanceID);
                    storageGroup.addConsistencyGroupTypes(Types.MIGRATION.name());
                    storageGroup.setStorageController(systemId);
                    storageGroup.setMigrationStatus(MigrationStatus.NONE.toString());
                    // storageGroup.addSystemConsistencyGroup(systemId.toString(), instanceID);
                    storageGroup.setProject(new NamedURI(project.getId(), project.getLabel()));
                    storageGroup.setTenant(project.getTenantOrg());
                    dbClient.createObject(storageGroup);
                } else {
                    // storageGroup.setMigrationStatus(MigrationStatus.NONE.toString()); // TODO see how to get latest migration status
                    dbClient.updateObject(storageGroup);
                }
                storageGroupNames.add(instanceID);
            }
        } catch (Exception e) {
            logger.error(
                    String.format("Processing associated storage group information for Masking View %s failed: ", maskingViewName), e);
        }
    }

    /**
     * Create a project under root tenant for all the storage groups that are eligible for migration.
     *
     * @return the migration project
     */
    private synchronized Project getMigrationProject() {
        Project project = null;
        List<Project> objectList = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, Project.class,
                PrefixConstraint.Factory.getFullMatchConstraint(Project.class, LABEL, MIGRATION_PROJECT));
        Iterator<Project> projItr = objectList.iterator();
        if (!projItr.hasNext()) {
            logger.info("Creating migration project..");
            // Find the root tenant
            List<URI> tenantOrgList = dbClient.queryByType(TenantOrg.class, true);
            Iterator<TenantOrg> itr = dbClient.queryIterativeObjects(TenantOrg.class, tenantOrgList);
            TenantOrg rootTenant = null;
            while (itr.hasNext()) {
                TenantOrg tenantOrg = itr.next();
                if (TenantOrg.isRootTenant(tenantOrg)) {
                    rootTenant = tenantOrg;
                    break;
                }
            }

            project = new Project();
            project.setId(URIUtil.createId(Project.class));
            project.setLabel(MIGRATION_PROJECT);
            project.setTenantOrg(new NamedURI(rootTenant.getId(), MIGRATION_PROJECT));
            project.setOwner(ROOT);

            // set owner acl
            project.addAcl(
                    new PermissionsKey(PermissionsKey.Type.SID, ROOT, rootTenant.getId().toString()).toString(),
                    ACL.OWN.toString());
            dbClient.createObject(project);
        } else {
            project = projItr.next();
            logger.info("Found migration project {}", project.getId());
        }
        return project;
    }

    /**
     * Check if Storage Group exists in DB.
     *
     * @param instanceID the label
     * @param dbClient the db client
     * @return BlockConsistencyGroup
     */
    protected BlockConsistencyGroup checkStorageGroupExistsInDB(String instanceID, DbClient dbClient) {
        BlockConsistencyGroup storageGroup = null;
        URIQueryResultList storageGroupResults = new URIQueryResultList();
        dbClient.queryByConstraint(PrefixConstraint.Factory.
                getFullMatchConstraint(BlockConsistencyGroup.class, "label", instanceID),
                storageGroupResults);
        if (storageGroupResults.iterator().hasNext()) {
            storageGroup = dbClient.queryObject(
                    BlockConsistencyGroup.class, storageGroupResults.iterator().next());
        }
        return storageGroup;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        args = inputArgs;
    }
}
