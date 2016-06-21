package com.emc.storageos.driver.vmaxv3driver.base;

import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;

import java.util.Map;

/**
 * Created by gang on 6/21/16.
 */
public interface Operation {

    public boolean isMatch(String name, Object... parameters);

    public Map<String, Object> execute();

    public void setRegistry(Registry registry);

    public void setLockManager(LockManager lockManager);
}
