/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.exceptions;

import java.util.Collection;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link NetworkDeviceControllerException}s
 * <p/>
 * Remember to add the English message associated to the method in NetworkDeviceControllerExceptions.properties and use the annotation
 * {@link DeclareServiceCode} to set the service code associated to this error condition. You may need to create a new service code if there
 * is no an existing one suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface NetworkDeviceControllerExceptions {

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_SESSION_TIMEOUT)
    public NetworkControllerTimeoutException timeoutWaitingOnPrompt(String expectedPrompts);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_SESSION_LOCKED)
    public NetworkControllerSessionLockedException zoneSessionLocked(int attempts);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_SESSION_LOCKED)
    public NetworkControllerSessionLockedException fabricSessionLocked();

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_DB_ERROR)
    public NetworkDeviceControllerException getDeviceObjectFailedNotFound(final String network,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_DB_ERROR)
    public NetworkDeviceControllerException getDeviceObjectFailedNull(final String network);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_DB_ERROR)
    public NetworkDeviceControllerException saveDeviceObjectFailed(final String networkSysId,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_OBJ_ERROR)
    public NetworkDeviceControllerException doConnectFailed(final String network,
            final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getFabricIdsFailedNull(final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getFabricIdsFailedExc(final String uri,
            final String date, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getZonesetsFailedNull(final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getZonesetsFailedExc(final String uri,
            final String date, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException addSanZonesFailedNull(final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException addRemoveZonesFailedNull(final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException addRemoveZonesFailedNoDev(final String devId);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException removeSanZonesFailedNull(final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException updateSanZonesFailedNull(final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException removeZoneFailedNull(final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException discoverNetworkSystemFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException updatePhysicalInventoryFailedNull(final String uri,
            final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException updatePhysicalInventoryFailedExc(final String uri,
            final String date, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException verifyVersionFailed(final String uri,
            final String date, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException versionNotSupported(final String version,
            final String minimumSupportedVersion);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException verifyVersionFailedNull(final String uri);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException reconcileTransportZonesFailedExc(
            final String date, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getDeviceObjectFailed(
            final String network, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getZonesToBeAddedFailedIllegalAddress(
            final String address);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getZonesToBeDeletedFailedIllegalAddress(
            final String address);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getWBEMClientFailed(
            final String ipaddress, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException startZoningSessionFailed();

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException addZonesMemberFailedPath(String zoneName, String memberName);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException addZonesStrategyFailedPath();

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException addZonesStrategyFailedZoneCommit();

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException addZonesStrategyFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException activeZoneWithSameNameExists(String zoneName);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException inactiveZoneWithSameNameExists(String zoneName);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException removeZonesStrategyFailedSvc();

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException removeZonesStrategyFailedCommit();

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException removeZonesStrategyFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException activateZonesStrategyFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException setUpDialogFailed(final String network,
            final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException checkVsanFabricFailedNotFound(final String fabric,
            final String fabricWwn);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException checkVsanFabricFailed(final String fabric,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException addZonesStrategyFailedNotFound(final String vsanid,
            final Throwable ex);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException waitForSessionFailedTimeout(final String vsanid);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getPortConnectionsFailed(final String network,
            final String message, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException zoneEndpointsNotConnected(final String zoneName);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getVersionFailed(final String message,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getUptimeFailed(final String message,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException couldNotGetFabricLock(String fabricId,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException couldNotAcquireFabricLock(String fabricId);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException exceptionAcquiringFabricLock(String fabricId,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException exceptionReleasingFabricLock(String fabricId,
            final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException noActiveZonesetForFabric(String fabricId);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException nameZoneNotEnoughEndPoints();

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException nameZoneLongerThanAllowed(String zoneName, final int zoneNameAllowedLength);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException portStorageDeviceNotFound(final String storageSystemUri, final String portLabel);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException mdsDeviceNotInConfigMode();

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException mdsDeviceInConfigMode();

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException mdsUnexpectedLastPrompt(final String lastPrompt, final String expectedPrompt);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException mdsUnexpectedDeviceState(final String message);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException zoneNotFoundInFabric(String zoneName, String fabricId);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException zonesetActivationFailed(String fabricId, Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException getAliasesFailedNull(final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException addAliasesFailedNull(final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException addAliasesStrategyFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException removeAliasesStrategyFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException removeAliasesFailedNull(final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException updateAliasesStrategyFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException updateAliasesFailedNull(final String systemtype);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException aliasWithSameNameExists(String alias, String currentWwn, String newWwn);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException wwnAssignedToAnotherAlias(String wwn, String alias, String assignedAlias);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException aliasWithDifferentWwnExists(String alias, String currentWwn, String newWwn);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException aliasAlreadyInNetworkSystem(String alias, String networksystem);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException aliasNotFound(String alias);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException zoneSessionCommitFailed(String fabricId);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException operationFailed(String opName, final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_SESSION_LOCKED)
    public NetworkControllerSessionLockedException deviceAliasDatabaseLockedOrBusy(int attempts);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException updateZonesStrategyFailedCommit();

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException updateZonesStrategyFailed(final Throwable cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException updateZonesStrategyFailedNotFound(final String zoneName);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException fabricNotFoundInNetwork(final String fabricId, final String network);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException failedToGetFabricsInNetwork(final String network, final String cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException renameAliasNotSupported(final String alias);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException failedToGetEndpointZones(final Collection<String> endpoints, final String network,
            final String cause);

    @DeclareServiceCode(ServiceCode.CONTROLLER_NETWORK_ERROR)
    public NetworkDeviceControllerException failedToFindNetworkSystem(final Collection<String> endpoints, final String network);

}
