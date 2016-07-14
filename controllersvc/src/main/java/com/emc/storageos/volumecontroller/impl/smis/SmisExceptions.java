/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link SmisException}s
 * <p/>
 * Remember to add the English message associated to the method in SmisExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface SmisExceptions {

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    SmisException queryExistingMasksFailure(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    SmisException refreshExistingMaskFailure(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    SmisException createFullCopyFailure(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    SmisException noStoragePoolInstances(String storagePool, Throwable cause);

    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    SmisException unableToFindStoragePoolSetting();
    
    @DeclareServiceCode(ServiceCode.SMIS_COMMAND_ERROR)
    public void hluRetrivalfailed(final String message, final Throwable cause);

}
