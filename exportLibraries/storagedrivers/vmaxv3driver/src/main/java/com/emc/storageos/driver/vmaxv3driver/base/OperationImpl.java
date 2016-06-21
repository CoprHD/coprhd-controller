package com.emc.storageos.driver.vmaxv3driver.base;

import com.emc.storageos.driver.vmaxv3driver.rest.HttpRestClient;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.StorageSystem;

/**
 * Created by gang on 6/21/16.
 */
public abstract class OperationImpl implements Operation {

    private Registry registry;
    private LockManager lockManager;
    private HttpRestClient client;

    /**
     * This method is used to create HttpRestClient instance according to the given
     * StorageSystem input instance. Currently since Southbound SDK does not support
     * StorageProvider discovery, the StorageSystem native ID has to be set in the
     * IpAddress(hostName) field such as "lglw7150.lss.emc.com$000196801612". After
     * the StorageProvider discovery is ready, this class will be updated by removing
     * the native ID parsing logic.
     *
     * @param storageSystemInput The given StorageSystem instance passed by SB SDK.
     */
    protected void setClient(StorageSystem storageSystemInput) {
        String[] tokens = storageSystemInput.getIpAddress().split("\\$");
        String hostName = tokens[0];
        if(storageSystemInput.getNativeId() == null && tokens.length >= 2) {
            storageSystemInput.setNativeId(tokens[1]);
        }
        int port = storageSystemInput.getPortNumber();
        String userName = storageSystemInput.getUsername();
        String password = storageSystemInput.getPassword();
        HttpRestClient client = new HttpRestClient(hostName, port, userName, password);
        this.setClient(client);
    }

    public Registry getRegistry() {
        return registry;
    }

    @Override
    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public LockManager getLockManager() {
        return lockManager;
    }

    @Override
    public void setLockManager(LockManager lockManager) {
        this.lockManager = lockManager;
    }

    public HttpRestClient getClient() {
        return client;
    }

    public void setClient(HttpRestClient client) {
        this.client = client;
    }
}
