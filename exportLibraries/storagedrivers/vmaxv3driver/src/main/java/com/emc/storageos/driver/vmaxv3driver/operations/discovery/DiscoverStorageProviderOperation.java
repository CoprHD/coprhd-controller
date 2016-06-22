package com.emc.storageos.driver.vmaxv3driver.operations.discovery;

import com.emc.storageos.driver.vmaxv3driver.base.OperationImpl;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by gang on 6/22/16.
 */
public class DiscoverStorageProviderOperation extends OperationImpl {

    private static final Logger logger = LoggerFactory.getLogger(DiscoverStorageSystemOperation.class);

    private StorageProvider storageProvider;
    private List<StorageSystem> storageSystems;

    private String sloprovisioning_symmetrix = "/univmax/restapi/sloprovisioning/symmetrix";

    @Override
    public boolean isMatch(String name, Object... parameters) {
        if("discoverStorageSystem".equals(name)) {
            this.storageProvider = (StorageProvider)parameters[0];
            this.storageSystems = (List<StorageSystem>)parameters[1];
            this.setClient(this.storageProvider);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Perform the storage provider discovery operation. All the discovery information
     * will be set into the "storageProvider" and "storageSystems" instances.
     *
     * @return A map indicates if the operation succeeds or fails.
     */
    @Override
    public Map<String, Object> execute() {
        String path = sloprovisioning_symmetrix;
        Map<String, Object> result = new HashMap<>();
        try {
            String responseBody = this.getClient().request(path);





            result.put("success", true);
        } catch(Exception e) {
            logger.error(e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
