/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.migrationcontroller;

import java.util.ArrayList;
import java.util.List;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.util.CustomQueryUtility;

public class ApplicationMigrationController implements MigrationController {
    protected final static String CONTROLLER_SVC = "controllersvc";
    protected final static String CONTROLLER_SVC_VER = "1";

    static final Logger log = LoggerFactory.getLogger(ApplicationMigrationController.class);

    private SmisCommandHelper _helper;
    private DbClient _dbClient;
    private CIMObjectPathFactory _cimPath;

    public void setCimObjectPathFactory(CIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setSmisCommandHelper(SmisCommandHelper helper) {
        _helper = helper;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

	@Override
	public void migrationCreate() {
		log.info("ApplicationMigrationController : Create Migration");
		// TODO Auto-generated method stub
		
	}

    public void migrationCreate1(VolumeGroup volumeGroup, VirtualPool vPool, VirtualArray vArray) throws Exception {
        // Harsha Get the Basic information such as Source Storage System and Storage Group Name
        String[] volumeGroupLabelSplitArray = volumeGroup.getLabel().split(Constants.SMIS_PLUS_REGEX);
        StorageSystem srcStorageSystem = null;
        String nativeGuid = NativeGUIDGenerator.generateNativeGuid(volumeGroupLabelSplitArray[0], volumeGroupLabelSplitArray[1]);
        List<StorageSystem> systems = CustomQueryUtility.getActiveStorageSystemByNativeGuid(_dbClient, nativeGuid);
        if (!systems.isEmpty()) {
            srcStorageSystem = systems.get(0);
        }
        String storageGroup = volumeGroupLabelSplitArray[2];

        // Harsha Get all VolumeGroup associated information such MaskViews, PG Names and HostLists. Not sure what all we will need.
        List<String> maskViewList = new ArrayList<>();
        List<String> portGroupList = new ArrayList<>();
        List<String> initiatorList = new ArrayList<>();

        CloseableIterator<CIMObjectPath> maskViewItr = null;
        maskViewItr = _helper.getAssociatorNames(srcStorageSystem, _cimPath.getStorageGroupObjectPath(storageGroup, srcStorageSystem), null,
                SmisConstants.SYMM_LUN_MASKING_VIEW, null, null);
        while (maskViewItr.hasNext()) {
            CIMObjectPath maskViewPath = maskViewItr.next();
            // MASKVIEWNAME is added to maskViewList
            maskViewList.add(maskViewPath.getKey(Constants.DEVICEID).getValue().toString());
            CloseableIterator<CIMObjectPath> maskViewAssociatorItr = null;
            maskViewAssociatorItr = _helper.getAssociatorNames(srcStorageSystem, maskViewPath, null, null, null, null);
            while (maskViewAssociatorItr.hasNext()) {
                CIMObjectPath associatedInstancePath = maskViewAssociatorItr.next();
                if (associatedInstancePath.toString().contains(SmisConstants.SE_TARGET_MASKING_GROUP)) {
                    // SYMMETRIX+ARRAYID+PGNAME is added to portGroupList
                    portGroupList.add(associatedInstancePath
                            .getKey(Constants.INSTANCEID).getValue().toString()
                            .replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS));
                } else if (associatedInstancePath.toString().contains(SmisConstants.SE_STORAGE_HARDWARE_ID)) {
                    // W+16CHARFCWWN is added to InitiatorList
                    initiatorList.add(associatedInstancePath
                            .getKey(Constants.INSTANCEID).getValue().toString()
                            .replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS));
                }
            }

        }
        // Bharath Get the Storage Port information for Zoning Calls and perform zoning

        // Harsha Perform the NDM Create

        // TODO: Host Rescan.
    }

	@Override
	public void migrationMigrate() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationCommit() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationCancel() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationRefresh() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationRecover() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationRemoveEnv() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationSyncStart() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void migrationSyncStop() {
		// TODO Auto-generated method stub
		
	}
}
