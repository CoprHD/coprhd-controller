/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup.exceptions;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create an exception that need to retry
 * <p/>
 * Remember to add the English message associated to the method in RetryableBackupExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/ ErrorHandling#ErrorHandling-DevelopersGuide
 */
@MessageBundle
public interface RetryableBackupExceptions {

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public RetryableBackupException quorumServiceNotReady();

    @DeclareServiceCode(ServiceCode.BACKUP_INTERNAL_ERROR)
    public RetryableBackupException leaderHasBeenChanged();
}
