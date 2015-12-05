package com.emc.storageos.api.service.impl.placement;

import java.util.List;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.emc.storageos.api.service.impl.placement.FileStorageScheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class FileRPSchedular implements Scheduler {
	public final Logger _log = LoggerFactory
            .getLogger(FileRPSchedular.class);

    private DbClient _dbClient;
    private StorageScheduler _storageScheduler;
    private FileStorageScheduler _fileScheduler;
    
    public void setStorageScheduler(final StorageScheduler storageScheduler) {
        _storageScheduler = storageScheduler;
    }

    public void setDbClient(final DbClient dbClient) {
        _dbClient = dbClient;
    }
    
    public void setFileScheduler(FileStorageScheduler fileScheduler) {
        _fileScheduler = fileScheduler;
    }
    
    @Autowired
    protected PermissionsHelper _permissionsHelper = null;
    
	@Override
	public List getRecommendationsForResources(VirtualArray vArray,
										Project project, VirtualPool vPool,
			VirtualPoolCapabilityValuesWrapper capabilities) {
		// TODO Auto-generated method stub
		return null;
	}

}
