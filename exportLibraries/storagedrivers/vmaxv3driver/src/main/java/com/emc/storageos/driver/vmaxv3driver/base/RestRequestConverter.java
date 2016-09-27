/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.base;

/**
 * Interface definition for converting the SBSDK argument(s) to the RestRequest bean.
 *
 * Created by gang on 9/26/16.
 */
public interface RestRequestConverter {

    /**
     * Convert the SBSDK argument(s) to the RestRequest bean
     * @return
     */
    public RestRequest convert();
}
