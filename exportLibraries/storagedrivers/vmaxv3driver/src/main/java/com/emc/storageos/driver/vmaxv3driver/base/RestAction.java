package com.emc.storageos.driver.vmaxv3driver.base;

import com.emc.storageos.driver.vmaxv3driver.utils.rest.HttpRestClient;

/**
 * Created by gang on 6/23/16.
 */
public interface RestAction {
    public Object execute(HttpRestClient client);
}
