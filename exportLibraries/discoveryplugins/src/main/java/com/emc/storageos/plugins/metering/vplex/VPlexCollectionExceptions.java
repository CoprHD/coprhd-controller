/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.metering.vplex;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface VPlexCollectionExceptions {

    @DeclareServiceCode(ServiceCode.VPLEX_DATA_COLLECTION_EXCEPTION)
    public VPlexCollectionException failedVerifyingManagementServerVersion(
            final String mgmntServerIP, final Throwable t);

    @DeclareServiceCode(ServiceCode.VPLEX_DATA_COLLECTION_EXCEPTION)
    public VPlexCollectionException unsupportedManagementServerVersion(
            final String version, final String mgmntServerIP, final String minVersion);

    @DeclareServiceCode(ServiceCode.VPLEX_DATA_COLLECTION_EXCEPTION)
    public VPlexCollectionException failedScanningManagedSystems(
            final String mgmntServerIP, final String message, final Throwable tn);

    @DeclareServiceCode(ServiceCode.VPLEX_DATA_COLLECTION_EXCEPTION)
    public VPlexCollectionException failedScan(
            final String mgmntServerIP, final String reason);

    @DeclareServiceCode(ServiceCode.VPLEX_DATA_COLLECTION_EXCEPTION)
    public VPlexCollectionException failedDiscovery(
            final String storageSystemId, final String reason);

    @DeclareServiceCode(ServiceCode.VPLEX_DATA_COLLECTION_EXCEPTION)
    public VPlexCollectionException failedPortsDiscovery(
            final String storageSystemId, final String reason, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VPLEX_UNMANAGED_VOLUME_DISCOVERY_EXCEPTION)
    public VPlexCollectionException vplexUnmanagedVolumeDiscoveryFailed(
            final String vplexUri, final String message);

    @DeclareServiceCode(ServiceCode.VPLEX_UNMANAGED_VOLUME_INGEST_EXCEPTION)
    public VPlexCollectionException vplexUnmanagedVolumeIngestFailed(
            final String vplexUri, final String message);

    @DeclareServiceCode(ServiceCode.VPLEX_DATA_COLLECTION_EXCEPTION)
    public VPlexCollectionException failedScanningManagedSystemsNullAssemblyId(
            final String mgmntServerIP, final String clusterId);

    @DeclareServiceCode(ServiceCode.VPLEX_UNMANAGED_EXPORT_MASK_EXCEPTION)
    public VPlexCollectionException vplexUnmanagedExportMaskDiscoveryFailed(
            final String vplexUri, final String message);

}
