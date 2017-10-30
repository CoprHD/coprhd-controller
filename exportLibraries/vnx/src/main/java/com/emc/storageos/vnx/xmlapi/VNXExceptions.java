/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vnx.xmlapi;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link VNXException}s
 * <p/>
 * Remember to add the English message associated to the method in VNXExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface VNXExceptions {

    @DeclareServiceCode(ServiceCode.VNXFILE_COMM_ERROR)
    public VNXException communicationFailed(final String msg);

    @DeclareServiceCode(ServiceCode.VNXFILE_EXPORT_ERROR)
    public VNXException createExportFailed(final String msg, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VNXFILE_FILESYSTEM_ERROR)
    public VNXException createFileSystemFailed(final String msg);

    @DeclareServiceCode(ServiceCode.VNXFILE_SNAPSHOT_ERROR)
    public VNXException getFileSystemSnapshotsFailed(final String message);
}
