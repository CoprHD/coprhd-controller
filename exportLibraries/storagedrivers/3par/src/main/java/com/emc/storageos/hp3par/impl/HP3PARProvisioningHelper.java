/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hp3par.command.VolumeDetailsCommandResult;
import com.emc.storageos.hp3par.utils.CompleteError;
import com.emc.storageos.hp3par.utils.HP3PARConstants;
import com.emc.storageos.hp3par.utils.HP3PARUtil;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.CommonStorageCapabilities;
import com.emc.storageos.storagedriver.storagecapabilities.DataStorageServiceOption;
import com.emc.storageos.storagedriver.storagecapabilities.DeduplicationCapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

public class HP3PARProvisioningHelper {
    /**
     * Implements volume provisioning activities
     */
    
    private static final Logger _log = LoggerFactory.getLogger(HP3PARProvisioningHelper.class);
    private HP3PARUtil hp3parUtil;

    
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities, 
            DriverTask task, Registry driverRegistry) {

        int volumesCreated = 0;
        boolean IsDeDupEnabled = false;
        
        // get deduplicationCapability
        CommonStorageCapabilities commonCapabilities= capabilities.getCommonCapabilitis();
		if (commonCapabilities != null) {
			List<DataStorageServiceOption> dataService = commonCapabilities.getDataStorage();
			if (dataService != null) {
				for (DataStorageServiceOption dataServiceOption : dataService) {
					List<CapabilityInstance> capabilityList = dataServiceOption.getCapabilities();
					if (capabilityList != null) {
						for (CapabilityInstance ci : capabilityList) {
							String provTypeValue = ci
									.getPropertyValue(DeduplicationCapabilityDefinition.PROPERTY_NAME.ENABLED.name());
							if (provTypeValue !=null && provTypeValue.equalsIgnoreCase(Boolean.TRUE.toString())) {
								IsDeDupEnabled = true;
							}
						}
					}

				}
			}
		}

        // For each requested volume
        for (StorageVolume volume : volumes) {

            try {
                _log.info("3PARDriver:createVolumes for storage system native id {}, volume name {} - start",
                        volume.getStorageSystemId(), volume.getDisplayName());

                // get Api client
                HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(volume.getStorageSystemId(),
                        driverRegistry);

                // Create volume
                VolumeDetailsCommandResult volResult = null;
                Boolean isThin = volume.getThinlyProvisioned();
                if (IsDeDupEnabled) {
                	isThin = false;
                }
                hp3parApi.createVolume(volume.getDisplayName(), volume.getStoragePoolId(),
                		isThin, IsDeDupEnabled, volume.getRequestedCapacity() / HP3PARConstants.MEGA_BYTE);

                volResult = hp3parApi.getVolumeDetails(volume.getDisplayName());

                // Attributes of the volume in array
                volume.setProvisionedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
                volume.setAllocatedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
                volume.setWwn(volResult.getWwn());
                volume.setNativeId(volume.getDisplayName()); // required for volume delete
                volume.setDeviceLabel(volume.getDisplayName());
                volume.setAccessStatus(AccessStatus.READ_WRITE);

                // Update Consistency Group
                String volumeCGName = volume.getConsistencyGroup();
                if (volumeCGName != null && !volumeCGName.isEmpty()) {
                    _log.info("3PARDriver:createVolumes Adding volume {} to consistency group {} ",
                            volume.getDisplayName(), volumeCGName);
                    int addMember = 1;
                    hp3parApi.updateVVset(volumeCGName, volume.getNativeId(), addMember);
                }

                volumesCreated++;
                _log.info("3PARDriver:createVolumes for storage system native id {}, volume name {} - end",
                        volume.getStorageSystemId(), volume.getDisplayName());
            } catch (Exception e) {
                String msg = String.format(
                        "3PARDriver: Unable to create volume name %s with pool id %s for storage system native id %s; Error: %s.\n",
                        volume.getDisplayName(), volume.getStoragePoolId(), volume.getStorageSystemId(), e);
                _log.error(msg);
                _log.error(CompleteError.getStackTrace(e));
                task.setMessage(msg);
                e.printStackTrace();
            }
        } // end for each volume

        if (volumes.size() != 0) {
            if (volumesCreated == volumes.size()) {
            	task.setMessage("Successful");
                task.setStatus(DriverTask.TaskStatus.READY);            
            } else if (volumesCreated == 0) {
                task.setStatus(DriverTask.TaskStatus.FAILED);
            } else {
                task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
            }
        }
        return task;
    }

    public DriverTask expandVolume(StorageVolume volume, long newCapacity, 
            DriverTask task, Registry driverRegistry) {

        // For this volume
        try {
            _log.info("3PARDriver:expandVolume for storage system native id {}, volume name {} - start",
                    volume.getStorageSystemId(), volume.getDisplayName());
            
            if (newCapacity < volume.getProvisionedCapacity()) {
                throw new HP3PARException("New capacity is less than original capcity");
            }

            // get Api client
            HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(volume.getStorageSystemId(),
                    driverRegistry);

            // expand volume
            Long additionalSize = newCapacity - volume.getProvisionedCapacity();
            hp3parApi.expandVolume(volume.getDisplayName(), additionalSize / HP3PARConstants.MEGA_BYTE);

            volume.setRequestedCapacity(newCapacity);

            // actual size of the volume in array
            VolumeDetailsCommandResult volResult = hp3parApi.getVolumeDetails(volume.getDisplayName());
            volume.setProvisionedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);
            volume.setAllocatedCapacity(volResult.getSizeMiB() * HP3PARConstants.MEGA_BYTE);

            task.setStatus(DriverTask.TaskStatus.READY);
            _log.info("3PARDriver:expandVolumes for storage system native id {}, volume name {} - end",
                    volume.getStorageSystemId(), volume.getDisplayName());
        } catch (Exception e) {
            String msg = String.format(
                    "3PARDriver: Unable to expand volume name %s with pool id %s for storage system native id %s; Error: %s.\n",
                    volume.getDisplayName(), volume.getStoragePoolId(), volume.getStorageSystemId(), e);
            _log.error(msg);
            _log.error(CompleteError.getStackTrace(e));
            task.setMessage(msg);
            task.setStatus(DriverTask.TaskStatus.FAILED);
            e.printStackTrace();
        }

        return task;
    }

    public DriverTask deleteVolumes(StorageVolume volume, DriverTask task, Registry driverRegistry) {

            try {
                _log.info("3PARDriver:deleteVolumes for storage system native id {}, volume name {} - start",
                        volume.getStorageSystemId(), volume.getDisplayName());

                // get Api client
                HP3PARApi hp3parApi = hp3parUtil.getHP3PARDeviceFromNativeId(volume.getStorageSystemId(),
                        driverRegistry);

                // Remove from Consistency Group
                String volumeCGName = volume.getConsistencyGroup();
                if (volumeCGName != null && !volumeCGName.isEmpty()) {
                    _log.info("3PARDriver:deleteVolumes Removing volume {} from consistency group {} ",
                            volume.getDisplayName(), volumeCGName);
                    int removeMember = 2;
                    hp3parApi.updateVVset(volumeCGName, volume.getNativeId(), removeMember);
                }

                // Delete volume
                hp3parApi.deleteVolume(volume.getNativeId());

                task.setStatus(DriverTask.TaskStatus.READY);
                _log.info("3PARDriver:deleteVolumes for storage system native id {}, volume name {} - end",
                        volume.getStorageSystemId(), volume.getDisplayName());
            } catch (Exception e) {
                String msg = String.format(
                        "3PARDriver: Unable to delete volume name %s for storage system native id %s; Error: %s.\n",
                        volume.getNativeId(), volume.getStorageSystemId(), e);
                _log.error(msg);
                _log.error(CompleteError.getStackTrace(e));
                task.setMessage(msg);
                task.setStatus(DriverTask.TaskStatus.PARTIALLY_FAILED);
                e.printStackTrace();
            }
        return task;
    }


    public HP3PARUtil getHp3parUtil() {
        return hp3parUtil;
    }


    public void setHp3parUtil(HP3PARUtil hp3parUtil) {
        this.hp3parUtil = hp3parUtil;
    }    

}
