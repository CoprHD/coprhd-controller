/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.runners.Parameterized.Parameters;

import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.exceptions.FatalClientControllerExceptions;
import com.emc.storageos.exceptions.RetryableClientControllerExceptions;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerErrors;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerExceptions;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundleTest;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DeviceDataCollectionErrors;
import com.emc.storageos.volumecontroller.impl.smis.SmisErrors;
import com.emc.storageos.volumecontroller.impl.smis.SmisExceptions;
import com.emc.storageos.volumecontroller.placement.PlacementExceptions;

public class ControllerMessageBundleTest extends MessageBundleTest {
    public ControllerMessageBundleTest(Class<?> baseClass) {
        super(baseClass);
    }

    @Parameters
    public static Collection<Object[]> parameters() {
        List<Object[]> args = new ArrayList<Object[]>();
        args.add(new Object[] { DeviceControllerErrors.class });
        args.add(new Object[] { DeviceControllerExceptions.class });
        args.add(new Object[] { FatalClientControllerExceptions.class });
        args.add(new Object[] { RetryableClientControllerExceptions.class });
        args.add(new Object[] { NetworkDeviceControllerErrors.class });
        args.add(new Object[] { NetworkDeviceControllerExceptions.class });
        args.add(new Object[] { DeviceDataCollectionErrors.class });
        args.add(new Object[] { SmisErrors.class });
        args.add(new Object[] { SmisExceptions.class });
        args.add(new Object[] { PlacementExceptions.class });

        return args;
    }

}
