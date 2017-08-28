/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.exceptions;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ServiceError}s
 * related to Network Devices
 * <p/>
 * Remember to add the English message associated to the method in NetworkErrors.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface NetworkDeviceControllerErrors {

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_DB_ERROR)
    public ServiceError testCommunicationFailed(final String opName,
            final String network);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_DB_ERROR)
    public ServiceError testCommunicationFailedExc(final String opName,
            final String network, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError addSanZonesFailedExc(final String systemType,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError removeSanZonesFailedExc(final String systemtype,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError zoningFailedArgs(final String volUris);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError addRemoveZonesFailed(final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError removeZoneFailed(final String uri,
            final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError removeZoneFailedExc(final String uri);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError activateSanZonesFailed(final String uri,
            final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError updateSanZonesFailedExc(final String systemType,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError activateSanZonesFailedExc(final String systemType,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError deleteNetworkSystemFailed(final String network,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError zoneOperationFailed(final String operation, final String message);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError zoneExportGroupCreateFailed(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError zoneExportAddInitiatorsFailed(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError zoneExportGroupDeleteFailed(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError zoneExportRemoveInitiatorsFailed(final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError zoneRollbackFailedExc(final String exportGroupUri,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError zoneRollbackFailed(final String exportGroupUri);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError doConnectFailedNotMds(final String network);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError doConnectFailed(final String network);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getAliasesFailedExc(final String uri,
            final String date, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError addAliasesFailedExc(final String systemType, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError removeAliasesFailedExc(final String systemType, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError updateAliasesFailedExc(final String systemType, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError batchOperationFailed(final String results);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError zoneExportRemovePathsError(final String message);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError zoneExportRemovePathsFailed(final String message, final Throwable cause);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError zoneExportAddPathsError(final String message);
    
    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public ServiceError zoneExportAddPathsFailed(final String message, final Throwable cause);
}
