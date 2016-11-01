/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.migrationcontroller;

import java.net.URI;
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
import com.emc.storageos.networkcontroller.NetworkController;

public class ApplicationMigrationController implements MigrationController {
    protected final static String CONTROLLER_SVC = "controllersvc";
    protected final static String CONTROLLER_SVC_VER = "1";

    static final Logger logger = LoggerFactory.getLogger(ApplicationMigrationController.class);

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
    public void migrationCreate(URI volumeGroupURI) throws Exception {
        logger.info("ApplicationMigrationController : Create Migration");
        // Harsha Get the Basic information such as Source Storage System and Storage Group Name
        VolumeGroup volumeGroup = _dbClient.queryObject(VolumeGroup.class, volumeGroupURI);

        //TODO : volumeGroup's label is SYMMETRIX+{}. Later on when we try to build the nativeGUID, it will build one with null+{} 
        //since the key to the map keys off of vmax instead of SYMMETRIX. need to fix that. 
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
        //TODO: for now, migrationOptions constants are being filled randomly, for ex. used ALLOCATED_PORTS string as key for allocated ports.
        //Need to refine that and make it into a class that contains all the other constants for migration options.
        //TODO: Also, we are allocating ports in the API layer. evauluate if it makes sense to keep it that way or bring that code here.
        logger.info("VolumeGroup MigrationOptions");
        logger.info(volumeGroup.getMigrationOptions().toString());
        
        //TODO: zoning work
        // Need to decide on how to find out the information about fabricId and fabricWWN. Should this be part of migrationOptions?
        // Not really sure if migrationOptions is the right container for these information. VirtualArray and connected fabric based on 
        //allocated ports is another option to consider. 
        //Steps
        //1. extract the list of initiators from the initiatorList
        //2. extract all the allocated ports from the migration options.
        //3. build SAN Zones with list of zones and its members.
        //4. call 
        //NetworkController controller = getNetworkController(device.getSystemType());
        //controller.addSanZones(device.getId(), fabricId, fabricWwn, zones, false, task);
        
       
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
