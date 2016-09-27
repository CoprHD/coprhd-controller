package com.emc.storageos.driver.vmaxv3driver.operation.provisioning.create;

import com.emc.storageos.driver.vmaxv3driver.base.RestRequest;
import com.emc.storageos.driver.vmaxv3driver.base.RestRequestConverter;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

import java.util.List;

/**
 * Created by gang on 9/26/16.
 */
public class CreateStorageGroupRequestConverter implements RestRequestConverter {

    protected List<StorageVolume> volumes;
    protected StorageCapabilities capabilities;

    public CreateStorageGroupRequestConverter(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        this.volumes = volumes;
        this.capabilities = capabilities;
    }

    @Override
    public RestRequest convert() {
        return null;
    }
}
