/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.base;

/**
 * This interface is used to parse the operation result into the target format
 * needed by the SDK method call result.
 *
 * For an example, for the "createVolumes" operation, the operation result is
 * a list of objects of the "Volume" response type. However, the type needed
 * by the SDK method call is the passed-in "List<StorageVolume>" type. The
 * interface here is used to do such parsing.
 *
 * Created by gang on 9/28/16.
 */
public interface OperationResultParser {

    /**
     * Parse the given operation result into the needed type by the SDK method call.
     *
     * @return
     */
    public Object parse();
}
