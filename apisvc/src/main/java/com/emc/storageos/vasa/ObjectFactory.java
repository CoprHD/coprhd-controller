
package com.emc.storageos.vasa;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.emc.storageos.vasa package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _SetContextResponseReturn_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "return");
    private final static QName _IncompatibleVolume2IncompatibleVolume_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "IncompatibleVolume");
    private final static QName _CreateVirtualVolumeStorageProfile_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "storageProfile");
    private final static QName _CreateVirtualVolumeContainerCookie_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "containerCookie");
    private final static QName _NameValuePairParameterValue_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "parameterValue");
    private final static QName _NameValuePairParameterName_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "parameterName");
    private final static QName _MessageCatalogModuleName_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "moduleName");
    private final static QName _MessageCatalogLocale_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "locale");
    private final static QName _MessageCatalogLastModified_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "lastModified");
    private final static QName _MessageCatalogCatalogName_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "catalogName");
    private final static QName _MessageCatalogCatalogVersion_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "catalogVersion");
    private final static QName _MessageCatalogCatalogUri_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "catalogUri");
    private final static QName _ComplianceResultOperationalStatus_QNAME = new QName("http://compliance.policy.data.vasa.vim.vmware.com/xsd", "operationalStatus");
    private final static QName _ComplianceResultProfileId_QNAME = new QName("http://compliance.policy.data.vasa.vim.vmware.com/xsd", "profileId");
    private final static QName _GetCurrentTaskArrayId_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "arrayId");
    private final static QName _LocalizableMessageMessage_QNAME = new QName("http://policy.data.vasa.vim.vmware.com/xsd", "message");
    private final static QName _NotFound2NotFound_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "NotFound");
    private final static QName _StorageProfileConstraints_QNAME = new QName("http://profile.policy.data.vasa.vim.vmware.com/xsd", "constraints");
    private final static QName _StorageProfileDescription_QNAME = new QName("http://profile.policy.data.vasa.vim.vmware.com/xsd", "description");
    private final static QName _StorageProfileLastUpdatedBy_QNAME = new QName("http://profile.policy.data.vasa.vim.vmware.com/xsd", "lastUpdatedBy");
    private final static QName _StorageProfileLastUpdatedTime_QNAME = new QName("http://profile.policy.data.vasa.vim.vmware.com/xsd", "lastUpdatedTime");
    private final static QName _Timeout2Timeout_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "Timeout");
    private final static QName _InvalidLogin2InvalidLogin_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "InvalidLogin");
    private final static QName _StorageCapabilityCapabilityName_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "capabilityName");
    private final static QName _StorageCapabilityCapabilityDetail_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "capabilityDetail");
    private final static QName _ActivateProviderFailed2ActivateProviderFailed_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "ActivateProviderFailed");
    private final static QName _BaseStorageEntityUniqueIdentifier_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "uniqueIdentifier");
    private final static QName _FastCloneVirtualVolumeNewProfile_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "newProfile");
    private final static QName _QueryDRSMigrationCapabilityForPerformanceDstUniqueId_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "dstUniqueId");
    private final static QName _QueryDRSMigrationCapabilityForPerformanceSrcUniqueId_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "srcUniqueId");
    private final static QName _QueryDRSMigrationCapabilityForPerformanceEntityType_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "entityType");
    private final static QName _SetContextUsageContext_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "usageContext");
    private final static QName _TooMany2TooMany_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "TooMany");
    private final static QName _BatchVirtualVolumeHandleResultVvolInfo_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "vvolInfo");
    private final static QName _BatchVirtualVolumeHandleResultVvolHandle_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "vvolHandle");
    private final static QName _BatchVirtualVolumeHandleResultFault_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "fault");
    private final static QName _ProtocolEndpointAuthType_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "authType");
    private final static QName _MountInfoFilePath_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "filePath");
    private final static QName _MountInfoServerName_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "serverName");
    private final static QName _QueryUniqueIdentifiersForLunsArrayUniqueId_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "arrayUniqueId");
    private final static QName _QueryUniqueIdentifiersForFileSystemsFsUniqueId_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "fsUniqueId");
    private final static QName _InvalidStatisticsContext2InvalidStatisticsContext_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "InvalidStatisticsContext");
    private final static QName _TaskInfoEstimatedTimeToComplete_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "estimatedTimeToComplete");
    private final static QName _TaskInfoError_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "error");
    private final static QName _TaskInfoArrayId_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "arrayId");
    private final static QName _TaskInfoResult_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "result");
    private final static QName _FileSystemInfoFileSystemPath_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "fileSystemPath");
    private final static QName _FileSystemInfoIpAddress_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "ipAddress");
    private final static QName _FileSystemInfoFileServerName_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "fileServerName");
    private final static QName _StorageLunBackingConfig_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "backingConfig");
    private final static QName _StorageLunThinProvisioningStatus_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "thinProvisioningStatus");
    private final static QName _StorageLunEsxLunIdentifier_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "esxLunIdentifier");
    private final static QName _StorageLunDisplayName_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "displayName");
    private final static QName _UnregisterVASACertificateExistingCertificate_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "existingCertificate");
    private final static QName _StorageFaultFaultMessageId_QNAME = new QName("http://fault.vasa.vim.vmware.com/xsd", "faultMessageId");
    private final static QName _InvalidProfile2InvalidProfile_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "InvalidProfile");
    private final static QName _InactiveProviderEntityType_QNAME = new QName("http://fault.vasa.vim.vmware.com/xsd", "entityType");
    private final static QName _InactiveProviderEntityId_QNAME = new QName("http://fault.vasa.vim.vmware.com/xsd", "entityId");
    private final static QName _InvalidSession2InvalidSession_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "InvalidSession");
    private final static QName _VendorModelModelId_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "modelId");
    private final static QName _VendorModelVendorId_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "vendorId");
    private final static QName _InvalidCertificate2InvalidCertificate_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "InvalidCertificate");
    private final static QName _PropertyMetadataDefaultValue_QNAME = new QName("http://capability.policy.data.vasa.vim.vmware.com/xsd", "defaultValue");
    private final static QName _PropertyMetadataRequirementsTypeHint_QNAME = new QName("http://capability.policy.data.vasa.vim.vmware.com/xsd", "requirementsTypeHint");
    private final static QName _CloneVirtualVolumeNewContainerId_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "newContainerId");
    private final static QName _UnsharedChunksVirtualVolumeBaseVvolId_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "baseVvolId");
    private final static QName _StorageContainerProtocolEndPointType_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "protocolEndPointType");
    private final static QName _StorageArrayFirmware_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "firmware");
    private final static QName _StorageArrayArrayName_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "arrayName");
    private final static QName _RegisterVASACertificateNewCertificate_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "newCertificate");
    private final static QName _RegisterVASACertificateUserName_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "userName");
    private final static QName _RegisterVASACertificatePassword_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "password");
    private final static QName _NotImplemented2NotImplemented_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "NotImplemented");
    private final static QName _BatchReturnStatusErrorResult_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "errorResult");
    private final static QName _LostAlarm2LostAlarm_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "LostAlarm");
    private final static QName _HostInitiatorInfoPortWwn_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "portWwn");
    private final static QName _HostInitiatorInfoNodeWwn_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "nodeWwn");
    private final static QName _HostInitiatorInfoIscsiIdentifier_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "iscsiIdentifier");
    private final static QName _VasaProviderInfoName_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "name");
    private final static QName _VasaProviderInfoSessionId_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "sessionId");
    private final static QName _VasaProviderInfoVasaProviderVersion_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "vasaProviderVersion");
    private final static QName _VasaProviderInfoDefaultNamespace_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "defaultNamespace");
    private final static QName _VasaProviderInfoVasaApiVersion_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "vasaApiVersion");
    private final static QName _OperationalStatusOperationETA_QNAME = new QName("http://compliance.policy.data.vasa.vim.vmware.com/xsd", "operationETA");
    private final static QName _ProtocolEndpointInbandIdLunId_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "lunId");
    private final static QName _ProtocolEndpointInbandIdServerMount_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "serverMount");
    private final static QName _ProtocolEndpointInbandIdServerMajor_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "serverMajor");
    private final static QName _ProtocolEndpointInbandIdServerScope_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "serverScope");
    private final static QName _ProtocolEndpointInbandIdIpAddress_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "ipAddress");
    private final static QName _ProtocolEndpointInbandIdServerMinor_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "serverMinor");
    private final static QName _ComplianceAlarmOperationalStatus_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "operationalStatus");
    private final static QName _VasaProviderBusy2VasaProviderBusy_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "VasaProviderBusy");
    private final static QName _UsageContextVcGuid_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "vcGuid");
    private final static QName _UsageContextHostGuid_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "hostGuid");
    private final static QName _ProvisioningSubjectObjectId_QNAME = new QName("http://placement.policy.data.vasa.vim.vmware.com/xsd", "objectId");
    private final static QName _OutOfResource2OutOfResource_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "OutOfResource");
    private final static QName _PermissionDenied2PermissionDenied_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "PermissionDenied");
    private final static QName _ResourceInUse2ResourceInUse_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "ResourceInUse");
    private final static QName _ActivationSpecCurrentActiveProviderUrl_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "currentActiveProviderUrl");
    private final static QName _UnbindAllVirtualVolumesFromHostHostUuid_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "hostUuid");
    private final static QName _InvalidArgument2InvalidArgument_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "InvalidArgument");
    private final static QName _StorageAlarmObjectId_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "objectId");
    private final static QName _StorageAlarmStatus_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "status");
    private final static QName _StorageAlarmAlarmTimeStamp_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "alarmTimeStamp");
    private final static QName _StorageAlarmMessageId_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "messageId");
    private final static QName _StorageAlarmAlarmType_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "alarmType");
    private final static QName _StorageAlarmObjectType_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "objectType");
    private final static QName _StorageAlarmContainerId_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "containerId");
    private final static QName _NotCancellable2NotCancellable_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "NotCancellable");
    private final static QName _NotSupported2NotSupported_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "NotSupported");
    private final static QName _SnapshotTooMany2SnapshotTooMany_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "SnapshotTooMany");
    private final static QName _StorageEventArrayId_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "arrayId");
    private final static QName _StorageEventEventObjType_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "eventObjType");
    private final static QName _StorageEventEventConfigType_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "eventConfigType");
    private final static QName _StorageEventEventType_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "eventType");
    private final static QName _StorageEventEventTimeStamp_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "eventTimeStamp");
    private final static QName _StorageFileSystemFileSystemVersion_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "fileSystemVersion");
    private final static QName _StorageFileSystemFileSystem_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "fileSystem");
    private final static QName _QueryConstraintValue_QNAME = new QName("http://vvol.data.vasa.vim.vmware.com/xsd", "value");
    private final static QName _BackingConfigThinProvisionBackingIdentifier_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "thinProvisionBackingIdentifier");
    private final static QName _BackingConfigDeduplicationBackingIdentifier_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "deduplicationBackingIdentifier");
    private final static QName _StoragePortPortType_QNAME = new QName("http://data.vasa.vim.vmware.com/xsd", "portType");
    private final static QName _CapabilityMetadataKeyId_QNAME = new QName("http://capability.policy.data.vasa.vim.vmware.com/xsd", "keyId");
    private final static QName _ExceptionException_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "Exception");
    private final static QName _StorageFault2StorageFault_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "StorageFault");
    private final static QName _LostEvent2LostEvent_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "LostEvent");
    private final static QName _InactiveProvider2InactiveProvider_QNAME = new QName("http://com.vmware.vim.vasa/2.0/xsd", "InactiveProvider");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.emc.storageos.vasa
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link Resource }
     * 
     */
    public Resource createResource() {
        return new Resource();
    }

    /**
     * Create an instance of {@link LocalizableMessage }
     * 
     */
    public LocalizableMessage createLocalizableMessage() {
        return new LocalizableMessage();
    }

    /**
     * Create an instance of {@link ResourceAssociation }
     * 
     */
    public ResourceAssociation createResourceAssociation() {
        return new ResourceAssociation();
    }

    /**
     * Create an instance of {@link KeyAnyValue }
     * 
     */
    public KeyAnyValue createKeyAnyValue() {
        return new KeyAnyValue();
    }

    /**
     * Create an instance of {@link ExtendedElementDescription }
     * 
     */
    public ExtendedElementDescription createExtendedElementDescription() {
        return new ExtendedElementDescription();
    }

    /**
     * Create an instance of {@link VendorInfo }
     * 
     */
    public VendorInfo createVendorInfo() {
        return new VendorInfo();
    }

    /**
     * Create an instance of {@link CapabilityMetadataPerCategory }
     * 
     */
    public CapabilityMetadataPerCategory createCapabilityMetadataPerCategory() {
        return new CapabilityMetadataPerCategory();
    }

    /**
     * Create an instance of {@link NamespaceInfo }
     * 
     */
    public NamespaceInfo createNamespaceInfo() {
        return new NamespaceInfo();
    }

    /**
     * Create an instance of {@link CapabilitySchema }
     * 
     */
    public CapabilitySchema createCapabilitySchema() {
        return new CapabilitySchema();
    }

    /**
     * Create an instance of {@link ProtocolEndpointInbandId }
     * 
     */
    public ProtocolEndpointInbandId createProtocolEndpointInbandId() {
        return new ProtocolEndpointInbandId();
    }

    /**
     * Create an instance of {@link QueryConstraint }
     * 
     */
    public QueryConstraint createQueryConstraint() {
        return new QueryConstraint();
    }

    /**
     * Create an instance of {@link RebindEvent }
     * 
     */
    public RebindEvent createRebindEvent() {
        return new RebindEvent();
    }

    /**
     * Create an instance of {@link BatchReturnStatusArray }
     * 
     */
    public BatchReturnStatusArray createBatchReturnStatusArray() {
        return new BatchReturnStatusArray();
    }

    /**
     * Create an instance of {@link PrepareSnapshotResult }
     * 
     */
    public PrepareSnapshotResult createPrepareSnapshotResult() {
        return new PrepareSnapshotResult();
    }

    /**
     * Create an instance of {@link BatchErrorResult }
     * 
     */
    public BatchErrorResult createBatchErrorResult() {
        return new BatchErrorResult();
    }

    /**
     * Create an instance of {@link ContainerSpaceStats }
     * 
     */
    public ContainerSpaceStats createContainerSpaceStats() {
        return new ContainerSpaceStats();
    }

    /**
     * Create an instance of {@link VirtualVolumeBitmapResult }
     * 
     */
    public VirtualVolumeBitmapResult createVirtualVolumeBitmapResult() {
        return new VirtualVolumeBitmapResult();
    }

    /**
     * Create an instance of {@link ProtocolEndpoint }
     * 
     */
    public ProtocolEndpoint createProtocolEndpoint() {
        return new ProtocolEndpoint();
    }

    /**
     * Create an instance of {@link BatchReturnStatus }
     * 
     */
    public BatchReturnStatus createBatchReturnStatus() {
        return new BatchReturnStatus();
    }

    /**
     * Create an instance of {@link SpaceStats }
     * 
     */
    public SpaceStats createSpaceStats() {
        return new SpaceStats();
    }

    /**
     * Create an instance of {@link VirtualVolumeHandle }
     * 
     */
    public VirtualVolumeHandle createVirtualVolumeHandle() {
        return new VirtualVolumeHandle();
    }

    /**
     * Create an instance of {@link VirtualVolumeUnsharedChunksResult }
     * 
     */
    public VirtualVolumeUnsharedChunksResult createVirtualVolumeUnsharedChunksResult() {
        return new VirtualVolumeUnsharedChunksResult();
    }

    /**
     * Create an instance of {@link VirtualVolumeInfo }
     * 
     */
    public VirtualVolumeInfo createVirtualVolumeInfo() {
        return new VirtualVolumeInfo();
    }

    /**
     * Create an instance of {@link BatchVirtualVolumeHandleResult }
     * 
     */
    public BatchVirtualVolumeHandleResult createBatchVirtualVolumeHandleResult() {
        return new BatchVirtualVolumeHandleResult();
    }

    /**
     * Create an instance of {@link TaskInfo }
     * 
     */
    public TaskInfo createTaskInfo() {
        return new TaskInfo();
    }

    /**
     * Create an instance of {@link StorageContainer }
     * 
     */
    public StorageContainer createStorageContainer() {
        return new StorageContainer();
    }

    /**
     * Create an instance of {@link StorageDrsMigrationCapabilityResult }
     * 
     */
    public StorageDrsMigrationCapabilityResult createStorageDrsMigrationCapabilityResult() {
        return new StorageDrsMigrationCapabilityResult();
    }

    /**
     * Create an instance of {@link VasaAssociationObject }
     * 
     */
    public VasaAssociationObject createVasaAssociationObject() {
        return new VasaAssociationObject();
    }

    /**
     * Create an instance of {@link StorageFileSystem }
     * 
     */
    public StorageFileSystem createStorageFileSystem() {
        return new StorageFileSystem();
    }

    /**
     * Create an instance of {@link BaseStorageEntity }
     * 
     */
    public BaseStorageEntity createBaseStorageEntity() {
        return new BaseStorageEntity();
    }

    /**
     * Create an instance of {@link BackingConfig }
     * 
     */
    public BackingConfig createBackingConfig() {
        return new BackingConfig();
    }

    /**
     * Create an instance of {@link VendorModel }
     * 
     */
    public VendorModel createVendorModel() {
        return new VendorModel();
    }

    /**
     * Create an instance of {@link StorageArray }
     * 
     */
    public StorageArray createStorageArray() {
        return new StorageArray();
    }

    /**
     * Create an instance of {@link StorageLun }
     * 
     */
    public StorageLun createStorageLun() {
        return new StorageLun();
    }

    /**
     * Create an instance of {@link UsageContext }
     * 
     */
    public UsageContext createUsageContext() {
        return new UsageContext();
    }

    /**
     * Create an instance of {@link BackingStoragePool }
     * 
     */
    public BackingStoragePool createBackingStoragePool() {
        return new BackingStoragePool();
    }

    /**
     * Create an instance of {@link StorageEvent }
     * 
     */
    public StorageEvent createStorageEvent() {
        return new StorageEvent();
    }

    /**
     * Create an instance of {@link FileSystemInfo }
     * 
     */
    public FileSystemInfo createFileSystemInfo() {
        return new FileSystemInfo();
    }

    /**
     * Create an instance of {@link HostInitiatorInfo }
     * 
     */
    public HostInitiatorInfo createHostInitiatorInfo() {
        return new HostInitiatorInfo();
    }

    /**
     * Create an instance of {@link EntitySet }
     * 
     */
    public EntitySet createEntitySet() {
        return new EntitySet();
    }

    /**
     * Create an instance of {@link StorageCapability }
     * 
     */
    public StorageCapability createStorageCapability() {
        return new StorageCapability();
    }

    /**
     * Create an instance of {@link NameValuePair }
     * 
     */
    public NameValuePair createNameValuePair() {
        return new NameValuePair();
    }

    /**
     * Create an instance of {@link MountInfo }
     * 
     */
    public MountInfo createMountInfo() {
        return new MountInfo();
    }

    /**
     * Create an instance of {@link ComplianceAlarm }
     * 
     */
    public ComplianceAlarm createComplianceAlarm() {
        return new ComplianceAlarm();
    }

    /**
     * Create an instance of {@link StorageProcessor }
     * 
     */
    public StorageProcessor createStorageProcessor() {
        return new StorageProcessor();
    }

    /**
     * Create an instance of {@link ActivationSpec }
     * 
     */
    public ActivationSpec createActivationSpec() {
        return new ActivationSpec();
    }

    /**
     * Create an instance of {@link VasaProviderInfo }
     * 
     */
    public VasaProviderInfo createVasaProviderInfo() {
        return new VasaProviderInfo();
    }

    /**
     * Create an instance of {@link StoragePort }
     * 
     */
    public StoragePort createStoragePort() {
        return new StoragePort();
    }

    /**
     * Create an instance of {@link MessageCatalog }
     * 
     */
    public MessageCatalog createMessageCatalog() {
        return new MessageCatalog();
    }

    /**
     * Create an instance of {@link StorageAlarm }
     * 
     */
    public StorageAlarm createStorageAlarm() {
        return new StorageAlarm();
    }

    /**
     * Create an instance of {@link OperationalStatus }
     * 
     */
    public OperationalStatus createOperationalStatus() {
        return new OperationalStatus();
    }

    /**
     * Create an instance of {@link ComplianceResult }
     * 
     */
    public ComplianceResult createComplianceResult() {
        return new ComplianceResult();
    }

    /**
     * Create an instance of {@link ComplianceSubject }
     * 
     */
    public ComplianceSubject createComplianceSubject() {
        return new ComplianceSubject();
    }

    /**
     * Create an instance of {@link PolicyStatus }
     * 
     */
    public PolicyStatus createPolicyStatus() {
        return new PolicyStatus();
    }

    /**
     * Create an instance of {@link TypeInfo }
     * 
     */
    public TypeInfo createTypeInfo() {
        return new TypeInfo();
    }

    /**
     * Create an instance of {@link CapabilityId }
     * 
     */
    public CapabilityId createCapabilityId() {
        return new CapabilityId();
    }

    /**
     * Create an instance of {@link ConstraintInstance }
     * 
     */
    public ConstraintInstance createConstraintInstance() {
        return new ConstraintInstance();
    }

    /**
     * Create an instance of {@link CapabilityInstance }
     * 
     */
    public CapabilityInstance createCapabilityInstance() {
        return new CapabilityInstance();
    }

    /**
     * Create an instance of {@link CapabilityMetadata }
     * 
     */
    public CapabilityMetadata createCapabilityMetadata() {
        return new CapabilityMetadata();
    }

    /**
     * Create an instance of {@link PropertyMetadata }
     * 
     */
    public PropertyMetadata createPropertyMetadata() {
        return new PropertyMetadata();
    }

    /**
     * Create an instance of {@link GenericTypeInfo }
     * 
     */
    public GenericTypeInfo createGenericTypeInfo() {
        return new GenericTypeInfo();
    }

    /**
     * Create an instance of {@link PropertyInstance }
     * 
     */
    public PropertyInstance createPropertyInstance() {
        return new PropertyInstance();
    }

    /**
     * Create an instance of {@link LostEvent }
     * 
     */
    public LostEvent createLostEvent() {
        return new LostEvent();
    }

    /**
     * Create an instance of {@link NotImplemented }
     * 
     */
    public NotImplemented createNotImplemented() {
        return new NotImplemented();
    }

    /**
     * Create an instance of {@link SnapshotTooMany }
     * 
     */
    public SnapshotTooMany createSnapshotTooMany() {
        return new SnapshotTooMany();
    }

    /**
     * Create an instance of {@link Timeout }
     * 
     */
    public Timeout createTimeout() {
        return new Timeout();
    }

    /**
     * Create an instance of {@link PermissionDenied }
     * 
     */
    public PermissionDenied createPermissionDenied() {
        return new PermissionDenied();
    }

    /**
     * Create an instance of {@link NotSupported }
     * 
     */
    public NotSupported createNotSupported() {
        return new NotSupported();
    }

    /**
     * Create an instance of {@link TooMany }
     * 
     */
    public TooMany createTooMany() {
        return new TooMany();
    }

    /**
     * Create an instance of {@link VasaProviderBusy }
     * 
     */
    public VasaProviderBusy createVasaProviderBusy() {
        return new VasaProviderBusy();
    }

    /**
     * Create an instance of {@link InvalidCertificate }
     * 
     */
    public InvalidCertificate createInvalidCertificate() {
        return new InvalidCertificate();
    }

    /**
     * Create an instance of {@link InvalidLogin }
     * 
     */
    public InvalidLogin createInvalidLogin() {
        return new InvalidLogin();
    }

    /**
     * Create an instance of {@link NotFound }
     * 
     */
    public NotFound createNotFound() {
        return new NotFound();
    }

    /**
     * Create an instance of {@link InvalidSession }
     * 
     */
    public InvalidSession createInvalidSession() {
        return new InvalidSession();
    }

    /**
     * Create an instance of {@link OutOfResource }
     * 
     */
    public OutOfResource createOutOfResource() {
        return new OutOfResource();
    }

    /**
     * Create an instance of {@link ResourceInUse }
     * 
     */
    public ResourceInUse createResourceInUse() {
        return new ResourceInUse();
    }

    /**
     * Create an instance of {@link InvalidProfile }
     * 
     */
    public InvalidProfile createInvalidProfile() {
        return new InvalidProfile();
    }

    /**
     * Create an instance of {@link LostAlarm }
     * 
     */
    public LostAlarm createLostAlarm() {
        return new LostAlarm();
    }

    /**
     * Create an instance of {@link NotCancellable }
     * 
     */
    public NotCancellable createNotCancellable() {
        return new NotCancellable();
    }

    /**
     * Create an instance of {@link InvalidArgument }
     * 
     */
    public InvalidArgument createInvalidArgument() {
        return new InvalidArgument();
    }

    /**
     * Create an instance of {@link InvalidStatisticsContext }
     * 
     */
    public InvalidStatisticsContext createInvalidStatisticsContext() {
        return new InvalidStatisticsContext();
    }

    /**
     * Create an instance of {@link ActivateProviderFailed }
     * 
     */
    public ActivateProviderFailed createActivateProviderFailed() {
        return new ActivateProviderFailed();
    }

    /**
     * Create an instance of {@link StorageFault }
     * 
     */
    public StorageFault createStorageFault() {
        return new StorageFault();
    }

    /**
     * Create an instance of {@link IncompatibleVolume }
     * 
     */
    public IncompatibleVolume createIncompatibleVolume() {
        return new IncompatibleVolume();
    }

    /**
     * Create an instance of {@link InactiveProvider }
     * 
     */
    public InactiveProvider createInactiveProvider() {
        return new InactiveProvider();
    }

    /**
     * Create an instance of {@link LostEvent2 }
     * 
     */
    public LostEvent2 createLostEvent2() {
        return new LostEvent2();
    }

    /**
     * Create an instance of {@link QueryProtocolEndpointResponse }
     * 
     */
    public QueryProtocolEndpointResponse createQueryProtocolEndpointResponse() {
        return new QueryProtocolEndpointResponse();
    }

    /**
     * Create an instance of {@link QueryCapabilityMetadata }
     * 
     */
    public QueryCapabilityMetadata createQueryCapabilityMetadata() {
        return new QueryCapabilityMetadata();
    }

    /**
     * Create an instance of {@link NotSupported2 }
     * 
     */
    public NotSupported2 createNotSupported2() {
        return new NotSupported2();
    }

    /**
     * Create an instance of {@link QueryDefaultProfileForStorageContainer }
     * 
     */
    public QueryDefaultProfileForStorageContainer createQueryDefaultProfileForStorageContainer() {
        return new QueryDefaultProfileForStorageContainer();
    }

    /**
     * Create an instance of {@link NotFound2 }
     * 
     */
    public NotFound2 createNotFound2() {
        return new NotFound2();
    }

    /**
     * Create an instance of {@link GetEventsResponse }
     * 
     */
    public GetEventsResponse createGetEventsResponse() {
        return new GetEventsResponse();
    }

    /**
     * Create an instance of {@link PrepareForBindingChangeResponse }
     * 
     */
    public PrepareForBindingChangeResponse createPrepareForBindingChangeResponse() {
        return new PrepareForBindingChangeResponse();
    }

    /**
     * Create an instance of {@link ActivateProviderExResponse }
     * 
     */
    public ActivateProviderExResponse createActivateProviderExResponse() {
        return new ActivateProviderExResponse();
    }

    /**
     * Create an instance of {@link QueryStorageContainerForArrayResponse }
     * 
     */
    public QueryStorageContainerForArrayResponse createQueryStorageContainerForArrayResponse() {
        return new QueryStorageContainerForArrayResponse();
    }

    /**
     * Create an instance of {@link QueryUniqueIdentifiersForEntity }
     * 
     */
    public QueryUniqueIdentifiersForEntity createQueryUniqueIdentifiersForEntity() {
        return new QueryUniqueIdentifiersForEntity();
    }

    /**
     * Create an instance of {@link QueryAssociatedProcessorsForArrayResponse }
     * 
     */
    public QueryAssociatedProcessorsForArrayResponse createQueryAssociatedProcessorsForArrayResponse() {
        return new QueryAssociatedProcessorsForArrayResponse();
    }

    /**
     * Create an instance of {@link RevertVirtualVolume }
     * 
     */
    public RevertVirtualVolume createRevertVirtualVolume() {
        return new RevertVirtualVolume();
    }

    /**
     * Create an instance of {@link ActivateProviderEx }
     * 
     */
    public ActivateProviderEx createActivateProviderEx() {
        return new ActivateProviderEx();
    }

    /**
     * Create an instance of {@link QueryDefaultProfileForStorageContainerResponse }
     * 
     */
    public QueryDefaultProfileForStorageContainerResponse createQueryDefaultProfileForStorageContainerResponse() {
        return new QueryDefaultProfileForStorageContainerResponse();
    }

    /**
     * Create an instance of {@link DefaultProfile }
     * 
     */
    public DefaultProfile createDefaultProfile() {
        return new DefaultProfile();
    }

    /**
     * Create an instance of {@link InvalidStatisticsContext2 }
     * 
     */
    public InvalidStatisticsContext2 createInvalidStatisticsContext2() {
        return new InvalidStatisticsContext2();
    }

    /**
     * Create an instance of {@link QueryAssociatedPortsForProcessorResponse }
     * 
     */
    public QueryAssociatedPortsForProcessorResponse createQueryAssociatedPortsForProcessorResponse() {
        return new QueryAssociatedPortsForProcessorResponse();
    }

    /**
     * Create an instance of {@link QueryUniqueIdentifiersForFileSystems }
     * 
     */
    public QueryUniqueIdentifiersForFileSystems createQueryUniqueIdentifiersForFileSystems() {
        return new QueryUniqueIdentifiersForFileSystems();
    }

    /**
     * Create an instance of {@link IncompatibleVolume2 }
     * 
     */
    public IncompatibleVolume2 createIncompatibleVolume2() {
        return new IncompatibleVolume2();
    }

    /**
     * Create an instance of {@link GetCurrentTaskResponse }
     * 
     */
    public GetCurrentTaskResponse createGetCurrentTaskResponse() {
        return new GetCurrentTaskResponse();
    }

    /**
     * Create an instance of {@link SpaceStatsForStorageContainerResponse }
     * 
     */
    public SpaceStatsForStorageContainerResponse createSpaceStatsForStorageContainerResponse() {
        return new SpaceStatsForStorageContainerResponse();
    }

    /**
     * Create an instance of {@link RegisterVASACertificateResponse }
     * 
     */
    public RegisterVASACertificateResponse createRegisterVASACertificateResponse() {
        return new RegisterVASACertificateResponse();
    }

    /**
     * Create an instance of {@link QueryCapabilityProfileForResource }
     * 
     */
    public QueryCapabilityProfileForResource createQueryCapabilityProfileForResource() {
        return new QueryCapabilityProfileForResource();
    }

    /**
     * Create an instance of {@link QueryUniqueIdentifiersForLunsResponse }
     * 
     */
    public QueryUniqueIdentifiersForLunsResponse createQueryUniqueIdentifiersForLunsResponse() {
        return new QueryUniqueIdentifiersForLunsResponse();
    }

    /**
     * Create an instance of {@link QueryDRSMigrationCapabilityForPerformance }
     * 
     */
    public QueryDRSMigrationCapabilityForPerformance createQueryDRSMigrationCapabilityForPerformance() {
        return new QueryDRSMigrationCapabilityForPerformance();
    }

    /**
     * Create an instance of {@link QueryVirtualVolumeInfoResponse }
     * 
     */
    public QueryVirtualVolumeInfoResponse createQueryVirtualVolumeInfoResponse() {
        return new QueryVirtualVolumeInfoResponse();
    }

    /**
     * Create an instance of {@link QueryUniqueIdentifiersForFileSystemsResponse }
     * 
     */
    public QueryUniqueIdentifiersForFileSystemsResponse createQueryUniqueIdentifiersForFileSystemsResponse() {
        return new QueryUniqueIdentifiersForFileSystemsResponse();
    }

    /**
     * Create an instance of {@link SetContextResponse }
     * 
     */
    public SetContextResponse createSetContextResponse() {
        return new SetContextResponse();
    }

    /**
     * Create an instance of {@link UnbindVirtualVolumeResponse }
     * 
     */
    public UnbindVirtualVolumeResponse createUnbindVirtualVolumeResponse() {
        return new UnbindVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link QueryCACertificatesResponse }
     * 
     */
    public QueryCACertificatesResponse createQueryCACertificatesResponse() {
        return new QueryCACertificatesResponse();
    }

    /**
     * Create an instance of {@link UnbindVirtualVolume }
     * 
     */
    public UnbindVirtualVolume createUnbindVirtualVolume() {
        return new UnbindVirtualVolume();
    }

    /**
     * Create an instance of {@link GetTaskUpdate }
     * 
     */
    public GetTaskUpdate createGetTaskUpdate() {
        return new GetTaskUpdate();
    }

    /**
     * Create an instance of {@link QueryAssociatedCapabilityForLunResponse }
     * 
     */
    public QueryAssociatedCapabilityForLunResponse createQueryAssociatedCapabilityForLunResponse() {
        return new QueryAssociatedCapabilityForLunResponse();
    }

    /**
     * Create an instance of {@link GetEvents }
     * 
     */
    public GetEvents createGetEvents() {
        return new GetEvents();
    }

    /**
     * Create an instance of {@link QueryProtocolEndpointForArrayResponse }
     * 
     */
    public QueryProtocolEndpointForArrayResponse createQueryProtocolEndpointForArrayResponse() {
        return new QueryProtocolEndpointForArrayResponse();
    }

    /**
     * Create an instance of {@link GetAlarmsResponse }
     * 
     */
    public GetAlarmsResponse createGetAlarmsResponse() {
        return new GetAlarmsResponse();
    }

    /**
     * Create an instance of {@link SnapshotVirtualVolumeResponse }
     * 
     */
    public SnapshotVirtualVolumeResponse createSnapshotVirtualVolumeResponse() {
        return new SnapshotVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link QueryStorageFileSystems }
     * 
     */
    public QueryStorageFileSystems createQueryStorageFileSystems() {
        return new QueryStorageFileSystems();
    }

    /**
     * Create an instance of {@link QueryAssociatedLunsForPortResponse }
     * 
     */
    public QueryAssociatedLunsForPortResponse createQueryAssociatedLunsForPortResponse() {
        return new QueryAssociatedLunsForPortResponse();
    }

    /**
     * Create an instance of {@link RequestCSRResponse }
     * 
     */
    public RequestCSRResponse createRequestCSRResponse() {
        return new RequestCSRResponse();
    }

    /**
     * Create an instance of {@link InvalidArgument2 }
     * 
     */
    public InvalidArgument2 createInvalidArgument2() {
        return new InvalidArgument2();
    }

    /**
     * Create an instance of {@link ActivateProviderFailed2 }
     * 
     */
    public ActivateProviderFailed2 createActivateProviderFailed2() {
        return new ActivateProviderFailed2();
    }

    /**
     * Create an instance of {@link CopyDiffsToVirtualVolume }
     * 
     */
    public CopyDiffsToVirtualVolume createCopyDiffsToVirtualVolume() {
        return new CopyDiffsToVirtualVolume();
    }

    /**
     * Create an instance of {@link AllocatedBitmapVirtualVolumeResponse }
     * 
     */
    public AllocatedBitmapVirtualVolumeResponse createAllocatedBitmapVirtualVolumeResponse() {
        return new AllocatedBitmapVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link QueryProtocolEndpoint }
     * 
     */
    public QueryProtocolEndpoint createQueryProtocolEndpoint() {
        return new QueryProtocolEndpoint();
    }

    /**
     * Create an instance of {@link UnregisterVASACertificate }
     * 
     */
    public UnregisterVASACertificate createUnregisterVASACertificate() {
        return new UnregisterVASACertificate();
    }

    /**
     * Create an instance of {@link QueryAssociatedLunsForPort }
     * 
     */
    public QueryAssociatedLunsForPort createQueryAssociatedLunsForPort() {
        return new QueryAssociatedLunsForPort();
    }

    /**
     * Create an instance of {@link UnbindVirtualVolumeFromAllHost }
     * 
     */
    public UnbindVirtualVolumeFromAllHost createUnbindVirtualVolumeFromAllHost() {
        return new UnbindVirtualVolumeFromAllHost();
    }

    /**
     * Create an instance of {@link BindingChangeComplete }
     * 
     */
    public BindingChangeComplete createBindingChangeComplete() {
        return new BindingChangeComplete();
    }

    /**
     * Create an instance of {@link QueryCapabilityMetadataResponse }
     * 
     */
    public QueryCapabilityMetadataResponse createQueryCapabilityMetadataResponse() {
        return new QueryCapabilityMetadataResponse();
    }

    /**
     * Create an instance of {@link QueryStorageContainerResponse }
     * 
     */
    public QueryStorageContainerResponse createQueryStorageContainerResponse() {
        return new QueryStorageContainerResponse();
    }

    /**
     * Create an instance of {@link QueryCapabilityProfileResponse }
     * 
     */
    public QueryCapabilityProfileResponse createQueryCapabilityProfileResponse() {
        return new QueryCapabilityProfileResponse();
    }

    /**
     * Create an instance of {@link StorageProfile }
     * 
     */
    public StorageProfile createStorageProfile() {
        return new StorageProfile();
    }

    /**
     * Create an instance of {@link CreateVirtualVolume }
     * 
     */
    public CreateVirtualVolume createCreateVirtualVolume() {
        return new CreateVirtualVolume();
    }

    /**
     * Create an instance of {@link QueryComplianceResult }
     * 
     */
    public QueryComplianceResult createQueryComplianceResult() {
        return new QueryComplianceResult();
    }

    /**
     * Create an instance of {@link UnbindVirtualVolumeFromAllHostResponse }
     * 
     */
    public UnbindVirtualVolumeFromAllHostResponse createUnbindVirtualVolumeFromAllHostResponse() {
        return new UnbindVirtualVolumeFromAllHostResponse();
    }

    /**
     * Create an instance of {@link FastCloneVirtualVolumeResponse }
     * 
     */
    public FastCloneVirtualVolumeResponse createFastCloneVirtualVolumeResponse() {
        return new FastCloneVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link GetNumberOfEntities }
     * 
     */
    public GetNumberOfEntities createGetNumberOfEntities() {
        return new GetNumberOfEntities();
    }

    /**
     * Create an instance of {@link SpaceStatsForVirtualVolumeResponse }
     * 
     */
    public SpaceStatsForVirtualVolumeResponse createSpaceStatsForVirtualVolumeResponse() {
        return new SpaceStatsForVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link QueryDrsMigrationCapabilityForPerformanceEx }
     * 
     */
    public QueryDrsMigrationCapabilityForPerformanceEx createQueryDrsMigrationCapabilityForPerformanceEx() {
        return new QueryDrsMigrationCapabilityForPerformanceEx();
    }

    /**
     * Create an instance of {@link BindVirtualVolumeResponse }
     * 
     */
    public BindVirtualVolumeResponse createBindVirtualVolumeResponse() {
        return new BindVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link SnapshotVirtualVolume }
     * 
     */
    public SnapshotVirtualVolume createSnapshotVirtualVolume() {
        return new SnapshotVirtualVolume();
    }

    /**
     * Create an instance of {@link StorageFault2 }
     * 
     */
    public StorageFault2 createStorageFault2() {
        return new StorageFault2();
    }

    /**
     * Create an instance of {@link TooMany2 }
     * 
     */
    public TooMany2 createTooMany2() {
        return new TooMany2();
    }

    /**
     * Create an instance of {@link QueryUniqueIdentifiersForLuns }
     * 
     */
    public QueryUniqueIdentifiersForLuns createQueryUniqueIdentifiersForLuns() {
        return new QueryUniqueIdentifiersForLuns();
    }

    /**
     * Create an instance of {@link QueryCatalogResponse }
     * 
     */
    public QueryCatalogResponse createQueryCatalogResponse() {
        return new QueryCatalogResponse();
    }

    /**
     * Create an instance of {@link PrepareForBindingChange }
     * 
     */
    public PrepareForBindingChange createPrepareForBindingChange() {
        return new PrepareForBindingChange();
    }

    /**
     * Create an instance of {@link QueryCACertificateRevocationListsResponse }
     * 
     */
    public QueryCACertificateRevocationListsResponse createQueryCACertificateRevocationListsResponse() {
        return new QueryCACertificateRevocationListsResponse();
    }

    /**
     * Create an instance of {@link CancelBindingChange }
     * 
     */
    public CancelBindingChange createCancelBindingChange() {
        return new CancelBindingChange();
    }

    /**
     * Create an instance of {@link CancelTask }
     * 
     */
    public CancelTask createCancelTask() {
        return new CancelTask();
    }

    /**
     * Create an instance of {@link UpdateStorageProfileForVirtualVolume }
     * 
     */
    public UpdateStorageProfileForVirtualVolume createUpdateStorageProfileForVirtualVolume() {
        return new UpdateStorageProfileForVirtualVolume();
    }

    /**
     * Create an instance of {@link CloneVirtualVolumeResponse }
     * 
     */
    public CloneVirtualVolumeResponse createCloneVirtualVolumeResponse() {
        return new CloneVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link RegisterCASignedProviderCertificate }
     * 
     */
    public RegisterCASignedProviderCertificate createRegisterCASignedProviderCertificate() {
        return new RegisterCASignedProviderCertificate();
    }

    /**
     * Create an instance of {@link UnsharedBitmapVirtualVolumeResponse }
     * 
     */
    public UnsharedBitmapVirtualVolumeResponse createUnsharedBitmapVirtualVolumeResponse() {
        return new UnsharedBitmapVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link PrepareToSnapshotVirtualVolume }
     * 
     */
    public PrepareToSnapshotVirtualVolume createPrepareToSnapshotVirtualVolume() {
        return new PrepareToSnapshotVirtualVolume();
    }

    /**
     * Create an instance of {@link InvalidProfile2 }
     * 
     */
    public InvalidProfile2 createInvalidProfile2() {
        return new InvalidProfile2();
    }

    /**
     * Create an instance of {@link QueryBackingStoragePools }
     * 
     */
    public QueryBackingStoragePools createQueryBackingStoragePools() {
        return new QueryBackingStoragePools();
    }

    /**
     * Create an instance of {@link QueryStoragePorts }
     * 
     */
    public QueryStoragePorts createQueryStoragePorts() {
        return new QueryStoragePorts();
    }

    /**
     * Create an instance of {@link QueryComplianceResultResponse }
     * 
     */
    public QueryComplianceResultResponse createQueryComplianceResultResponse() {
        return new QueryComplianceResultResponse();
    }

    /**
     * Create an instance of {@link QueryAssociatedStatisticsForEntity }
     * 
     */
    public QueryAssociatedStatisticsForEntity createQueryAssociatedStatisticsForEntity() {
        return new QueryAssociatedStatisticsForEntity();
    }

    /**
     * Create an instance of {@link QueryStorageCapabilitiesResponse }
     * 
     */
    public QueryStorageCapabilitiesResponse createQueryStorageCapabilitiesResponse() {
        return new QueryStorageCapabilitiesResponse();
    }

    /**
     * Create an instance of {@link RevertVirtualVolumeResponse }
     * 
     */
    public RevertVirtualVolumeResponse createRevertVirtualVolumeResponse() {
        return new RevertVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link SnapshotTooMany2 }
     * 
     */
    public SnapshotTooMany2 createSnapshotTooMany2() {
        return new SnapshotTooMany2();
    }

    /**
     * Create an instance of {@link RegisterCACertificatesAndCRLs }
     * 
     */
    public RegisterCACertificatesAndCRLs createRegisterCACertificatesAndCRLs() {
        return new RegisterCACertificatesAndCRLs();
    }

    /**
     * Create an instance of {@link QueryStorageContainer }
     * 
     */
    public QueryStorageContainer createQueryStorageContainer() {
        return new QueryStorageContainer();
    }

    /**
     * Create an instance of {@link Timeout2 }
     * 
     */
    public Timeout2 createTimeout2() {
        return new Timeout2();
    }

    /**
     * Create an instance of {@link SpaceStatsForStorageContainer }
     * 
     */
    public SpaceStatsForStorageContainer createSpaceStatsForStorageContainer() {
        return new SpaceStatsForStorageContainer();
    }

    /**
     * Create an instance of {@link ResizeVirtualVolumeResponse }
     * 
     */
    public ResizeVirtualVolumeResponse createResizeVirtualVolumeResponse() {
        return new ResizeVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link VasaProviderBusy2 }
     * 
     */
    public VasaProviderBusy2 createVasaProviderBusy2() {
        return new VasaProviderBusy2();
    }

    /**
     * Create an instance of {@link QueryAssociatedCapabilityForFileSystem }
     * 
     */
    public QueryAssociatedCapabilityForFileSystem createQueryAssociatedCapabilityForFileSystem() {
        return new QueryAssociatedCapabilityForFileSystem();
    }

    /**
     * Create an instance of {@link SetContext }
     * 
     */
    public SetContext createSetContext() {
        return new SetContext();
    }

    /**
     * Create an instance of {@link QueryCapabilityProfile }
     * 
     */
    public QueryCapabilityProfile createQueryCapabilityProfile() {
        return new QueryCapabilityProfile();
    }

    /**
     * Create an instance of {@link QueryStoragePortsResponse }
     * 
     */
    public QueryStoragePortsResponse createQueryStoragePortsResponse() {
        return new QueryStoragePortsResponse();
    }

    /**
     * Create an instance of {@link CancelBindingChangeResponse }
     * 
     */
    public CancelBindingChangeResponse createCancelBindingChangeResponse() {
        return new CancelBindingChangeResponse();
    }

    /**
     * Create an instance of {@link QueryStorageLunsResponse }
     * 
     */
    public QueryStorageLunsResponse createQueryStorageLunsResponse() {
        return new QueryStorageLunsResponse();
    }

    /**
     * Create an instance of {@link GetCurrentTask }
     * 
     */
    public GetCurrentTask createGetCurrentTask() {
        return new GetCurrentTask();
    }

    /**
     * Create an instance of {@link DeleteVirtualVolume }
     * 
     */
    public DeleteVirtualVolume createDeleteVirtualVolume() {
        return new DeleteVirtualVolume();
    }

    /**
     * Create an instance of {@link QueryStorageCapabilities }
     * 
     */
    public QueryStorageCapabilities createQueryStorageCapabilities() {
        return new QueryStorageCapabilities();
    }

    /**
     * Create an instance of {@link PrepareToSnapshotVirtualVolumeResponse }
     * 
     */
    public PrepareToSnapshotVirtualVolumeResponse createPrepareToSnapshotVirtualVolumeResponse() {
        return new PrepareToSnapshotVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link QueryArraysResponse }
     * 
     */
    public QueryArraysResponse createQueryArraysResponse() {
        return new QueryArraysResponse();
    }

    /**
     * Create an instance of {@link QueryAssociatedPortsForProcessor }
     * 
     */
    public QueryAssociatedPortsForProcessor createQueryAssociatedPortsForProcessor() {
        return new QueryAssociatedPortsForProcessor();
    }

    /**
     * Create an instance of {@link GetAlarms }
     * 
     */
    public GetAlarms createGetAlarms() {
        return new GetAlarms();
    }

    /**
     * Create an instance of {@link QueryVirtualVolume }
     * 
     */
    public QueryVirtualVolume createQueryVirtualVolume() {
        return new QueryVirtualVolume();
    }

    /**
     * Create an instance of {@link QueryStorageFileSystemsResponse }
     * 
     */
    public QueryStorageFileSystemsResponse createQueryStorageFileSystemsResponse() {
        return new QueryStorageFileSystemsResponse();
    }

    /**
     * Create an instance of {@link CopyDiffsToVirtualVolumeResponse }
     * 
     */
    public CopyDiffsToVirtualVolumeResponse createCopyDiffsToVirtualVolumeResponse() {
        return new CopyDiffsToVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link QueryAssociatedCapabilityForFileSystemResponse }
     * 
     */
    public QueryAssociatedCapabilityForFileSystemResponse createQueryAssociatedCapabilityForFileSystemResponse() {
        return new QueryAssociatedCapabilityForFileSystemResponse();
    }

    /**
     * Create an instance of {@link QueryUniqueIdentifiersForEntityResponse }
     * 
     */
    public QueryUniqueIdentifiersForEntityResponse createQueryUniqueIdentifiersForEntityResponse() {
        return new QueryUniqueIdentifiersForEntityResponse();
    }

    /**
     * Create an instance of {@link QueryDrsMigrationCapabilityForPerformanceExResponse }
     * 
     */
    public QueryDrsMigrationCapabilityForPerformanceExResponse createQueryDrsMigrationCapabilityForPerformanceExResponse() {
        return new QueryDrsMigrationCapabilityForPerformanceExResponse();
    }

    /**
     * Create an instance of {@link CloneVirtualVolume }
     * 
     */
    public CloneVirtualVolume createCloneVirtualVolume() {
        return new CloneVirtualVolume();
    }

    /**
     * Create an instance of {@link QueryAssociatedCapabilityForLun }
     * 
     */
    public QueryAssociatedCapabilityForLun createQueryAssociatedCapabilityForLun() {
        return new QueryAssociatedCapabilityForLun();
    }

    /**
     * Create an instance of {@link QueryBackingStoragePoolsResponse }
     * 
     */
    public QueryBackingStoragePoolsResponse createQueryBackingStoragePoolsResponse() {
        return new QueryBackingStoragePoolsResponse();
    }

    /**
     * Create an instance of {@link UpdateStorageProfileForVirtualVolumeResponse }
     * 
     */
    public UpdateStorageProfileForVirtualVolumeResponse createUpdateStorageProfileForVirtualVolumeResponse() {
        return new UpdateStorageProfileForVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link SetPEContext }
     * 
     */
    public SetPEContext createSetPEContext() {
        return new SetPEContext();
    }

    /**
     * Create an instance of {@link InvalidCertificate2 }
     * 
     */
    public InvalidCertificate2 createInvalidCertificate2() {
        return new InvalidCertificate2();
    }

    /**
     * Create an instance of {@link SetStorageContainerContextResponse }
     * 
     */
    public SetStorageContainerContextResponse createSetStorageContainerContextResponse() {
        return new SetStorageContainerContextResponse();
    }

    /**
     * Create an instance of {@link QueryStorageContainerForArray }
     * 
     */
    public QueryStorageContainerForArray createQueryStorageContainerForArray() {
        return new QueryStorageContainerForArray();
    }

    /**
     * Create an instance of {@link CreateVirtualVolumeResponse }
     * 
     */
    public CreateVirtualVolumeResponse createCreateVirtualVolumeResponse() {
        return new CreateVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link SetStatisticsContext }
     * 
     */
    public SetStatisticsContext createSetStatisticsContext() {
        return new SetStatisticsContext();
    }

    /**
     * Create an instance of {@link ArrayStatisticsManifest }
     * 
     */
    public ArrayStatisticsManifest createArrayStatisticsManifest() {
        return new ArrayStatisticsManifest();
    }

    /**
     * Create an instance of {@link UpdateVirtualVolumeMetaData }
     * 
     */
    public UpdateVirtualVolumeMetaData createUpdateVirtualVolumeMetaData() {
        return new UpdateVirtualVolumeMetaData();
    }

    /**
     * Create an instance of {@link GetNumberOfEntitiesResponse }
     * 
     */
    public GetNumberOfEntitiesResponse createGetNumberOfEntitiesResponse() {
        return new GetNumberOfEntitiesResponse();
    }

    /**
     * Create an instance of {@link QueryVirtualVolumeResponse }
     * 
     */
    public QueryVirtualVolumeResponse createQueryVirtualVolumeResponse() {
        return new QueryVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link BindingChangeCompleteResponse }
     * 
     */
    public BindingChangeCompleteResponse createBindingChangeCompleteResponse() {
        return new BindingChangeCompleteResponse();
    }

    /**
     * Create an instance of {@link QueryCapabilityProfileForResourceResponse }
     * 
     */
    public QueryCapabilityProfileForResourceResponse createQueryCapabilityProfileForResourceResponse() {
        return new QueryCapabilityProfileForResourceResponse();
    }

    /**
     * Create an instance of {@link BindVirtualVolume }
     * 
     */
    public BindVirtualVolume createBindVirtualVolume() {
        return new BindVirtualVolume();
    }

    /**
     * Create an instance of {@link QueryAssociatedStatisticsManifestForArray }
     * 
     */
    public QueryAssociatedStatisticsManifestForArray createQueryAssociatedStatisticsManifestForArray() {
        return new QueryAssociatedStatisticsManifestForArray();
    }

    /**
     * Create an instance of {@link UnsharedBitmapVirtualVolume }
     * 
     */
    public UnsharedBitmapVirtualVolume createUnsharedBitmapVirtualVolume() {
        return new UnsharedBitmapVirtualVolume();
    }

    /**
     * Create an instance of {@link AllocatedBitmapVirtualVolume }
     * 
     */
    public AllocatedBitmapVirtualVolume createAllocatedBitmapVirtualVolume() {
        return new AllocatedBitmapVirtualVolume();
    }

    /**
     * Create an instance of {@link InactiveProvider2 }
     * 
     */
    public InactiveProvider2 createInactiveProvider2() {
        return new InactiveProvider2();
    }

    /**
     * Create an instance of {@link PermissionDenied2 }
     * 
     */
    public PermissionDenied2 createPermissionDenied2() {
        return new PermissionDenied2();
    }

    /**
     * Create an instance of {@link QueryVirtualVolumeInfo }
     * 
     */
    public QueryVirtualVolumeInfo createQueryVirtualVolumeInfo() {
        return new QueryVirtualVolumeInfo();
    }

    /**
     * Create an instance of {@link InvalidLogin2 }
     * 
     */
    public InvalidLogin2 createInvalidLogin2() {
        return new InvalidLogin2();
    }

    /**
     * Create an instance of {@link RegisterCACertificatesAndCRLsResponse }
     * 
     */
    public RegisterCACertificatesAndCRLsResponse createRegisterCACertificatesAndCRLsResponse() {
        return new RegisterCACertificatesAndCRLsResponse();
    }

    /**
     * Create an instance of {@link QueryStorageProcessorsResponse }
     * 
     */
    public QueryStorageProcessorsResponse createQueryStorageProcessorsResponse() {
        return new QueryStorageProcessorsResponse();
    }

    /**
     * Create an instance of {@link OutOfResource2 }
     * 
     */
    public OutOfResource2 createOutOfResource2() {
        return new OutOfResource2();
    }

    /**
     * Create an instance of {@link QueryStorageLuns }
     * 
     */
    public QueryStorageLuns createQueryStorageLuns() {
        return new QueryStorageLuns();
    }

    /**
     * Create an instance of {@link RegisterVASACertificate }
     * 
     */
    public RegisterVASACertificate createRegisterVASACertificate() {
        return new RegisterVASACertificate();
    }

    /**
     * Create an instance of {@link QueryProtocolEndpointForArray }
     * 
     */
    public QueryProtocolEndpointForArray createQueryProtocolEndpointForArray() {
        return new QueryProtocolEndpointForArray();
    }

    /**
     * Create an instance of {@link ResourceInUse2 }
     * 
     */
    public ResourceInUse2 createResourceInUse2() {
        return new ResourceInUse2();
    }

    /**
     * Create an instance of {@link LostAlarm2 }
     * 
     */
    public LostAlarm2 createLostAlarm2() {
        return new LostAlarm2();
    }

    /**
     * Create an instance of {@link GetTaskUpdateResponse }
     * 
     */
    public GetTaskUpdateResponse createGetTaskUpdateResponse() {
        return new GetTaskUpdateResponse();
    }

    /**
     * Create an instance of {@link QueryAssociatedStatisticsManifestForArrayResponse }
     * 
     */
    public QueryAssociatedStatisticsManifestForArrayResponse createQueryAssociatedStatisticsManifestForArrayResponse() {
        return new QueryAssociatedStatisticsManifestForArrayResponse();
    }

    /**
     * Create an instance of {@link FastCloneVirtualVolume }
     * 
     */
    public FastCloneVirtualVolume createFastCloneVirtualVolume() {
        return new FastCloneVirtualVolume();
    }

    /**
     * Create an instance of {@link RefreshCACertificatesAndCRLs }
     * 
     */
    public RefreshCACertificatesAndCRLs createRefreshCACertificatesAndCRLs() {
        return new RefreshCACertificatesAndCRLs();
    }

    /**
     * Create an instance of {@link NotImplemented2 }
     * 
     */
    public NotImplemented2 createNotImplemented2() {
        return new NotImplemented2();
    }

    /**
     * Create an instance of {@link QueryAssociatedStatisticsForEntityResponse }
     * 
     */
    public QueryAssociatedStatisticsForEntityResponse createQueryAssociatedStatisticsForEntityResponse() {
        return new QueryAssociatedStatisticsForEntityResponse();
    }

    /**
     * Create an instance of {@link AssociatedStatistics }
     * 
     */
    public AssociatedStatistics createAssociatedStatistics() {
        return new AssociatedStatistics();
    }

    /**
     * Create an instance of {@link UnsharedChunksVirtualVolume }
     * 
     */
    public UnsharedChunksVirtualVolume createUnsharedChunksVirtualVolume() {
        return new UnsharedChunksVirtualVolume();
    }

    /**
     * Create an instance of {@link UnsharedChunksVirtualVolumeResponse }
     * 
     */
    public UnsharedChunksVirtualVolumeResponse createUnsharedChunksVirtualVolumeResponse() {
        return new UnsharedChunksVirtualVolumeResponse();
    }

    /**
     * Create an instance of {@link InvalidSession2 }
     * 
     */
    public InvalidSession2 createInvalidSession2() {
        return new InvalidSession2();
    }

    /**
     * Create an instance of {@link ResizeVirtualVolume }
     * 
     */
    public ResizeVirtualVolume createResizeVirtualVolume() {
        return new ResizeVirtualVolume();
    }

    /**
     * Create an instance of {@link SetStorageContainerContext }
     * 
     */
    public SetStorageContainerContext createSetStorageContainerContext() {
        return new SetStorageContainerContext();
    }

    /**
     * Create an instance of {@link QueryAssociatedProcessorsForArray }
     * 
     */
    public QueryAssociatedProcessorsForArray createQueryAssociatedProcessorsForArray() {
        return new QueryAssociatedProcessorsForArray();
    }

    /**
     * Create an instance of {@link SpaceStatsForVirtualVolume }
     * 
     */
    public SpaceStatsForVirtualVolume createSpaceStatsForVirtualVolume() {
        return new SpaceStatsForVirtualVolume();
    }

    /**
     * Create an instance of {@link QueryStorageProcessors }
     * 
     */
    public QueryStorageProcessors createQueryStorageProcessors() {
        return new QueryStorageProcessors();
    }

    /**
     * Create an instance of {@link QueryArrays }
     * 
     */
    public QueryArrays createQueryArrays() {
        return new QueryArrays();
    }

    /**
     * Create an instance of {@link ActivateProvider }
     * 
     */
    public ActivateProvider createActivateProvider() {
        return new ActivateProvider();
    }

    /**
     * Create an instance of {@link NotCancellable2 }
     * 
     */
    public NotCancellable2 createNotCancellable2() {
        return new NotCancellable2();
    }

    /**
     * Create an instance of {@link UnbindAllVirtualVolumesFromHost }
     * 
     */
    public UnbindAllVirtualVolumesFromHost createUnbindAllVirtualVolumesFromHost() {
        return new UnbindAllVirtualVolumesFromHost();
    }

    /**
     * Create an instance of {@link QueryDRSMigrationCapabilityForPerformanceResponse }
     * 
     */
    public QueryDRSMigrationCapabilityForPerformanceResponse createQueryDRSMigrationCapabilityForPerformanceResponse() {
        return new QueryDRSMigrationCapabilityForPerformanceResponse();
    }

    /**
     * Create an instance of {@link Exception }
     * 
     */
    public Exception createException() {
        return new Exception();
    }

    /**
     * Create an instance of {@link SubProfile }
     * 
     */
    public SubProfile createSubProfile() {
        return new SubProfile();
    }

    /**
     * Create an instance of {@link CapabilityConstraints }
     * 
     */
    public CapabilityConstraints createCapabilityConstraints() {
        return new CapabilityConstraints();
    }

    /**
     * Create an instance of {@link SubProfileWithCandidates }
     * 
     */
    public SubProfileWithCandidates createSubProfileWithCandidates() {
        return new SubProfileWithCandidates();
    }

    /**
     * Create an instance of {@link EntityStatisticsManifest }
     * 
     */
    public EntityStatisticsManifest createEntityStatisticsManifest() {
        return new EntityStatisticsManifest();
    }

    /**
     * Create an instance of {@link CounterInfo }
     * 
     */
    public CounterInfo createCounterInfo() {
        return new CounterInfo();
    }

    /**
     * Create an instance of {@link VasaService }
     * 
     */
    public VasaService createVasaService() {
        return new VasaService();
    }

    /**
     * Create an instance of {@link Range }
     * 
     */
    public Range createRange() {
        return new Range();
    }

    /**
     * Create an instance of {@link DiscreteSet }
     * 
     */
    public DiscreteSet createDiscreteSet() {
        return new DiscreteSet();
    }

    /**
     * Create an instance of {@link TimeSpan }
     * 
     */
    public TimeSpan createTimeSpan() {
        return new TimeSpan();
    }

    /**
     * Create an instance of {@link ContainerUsage }
     * 
     */
    public ContainerUsage createContainerUsage() {
        return new ContainerUsage();
    }

    /**
     * Create an instance of {@link CapacityMetric }
     * 
     */
    public CapacityMetric createCapacityMetric() {
        return new CapacityMetric();
    }

    /**
     * Create an instance of {@link ProvisioningSubject }
     * 
     */
    public ProvisioningSubject createProvisioningSubject() {
        return new ProvisioningSubject();
    }

    /**
     * Create an instance of {@link SubjectAssignment }
     * 
     */
    public SubjectAssignment createSubjectAssignment() {
        return new SubjectAssignment();
    }

    /**
     * Create an instance of {@link PlacementSolution }
     * 
     */
    public PlacementSolution createPlacementSolution() {
        return new PlacementSolution();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VasaProviderInfo }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "return", scope = SetContextResponse.class)
    public JAXBElement<VasaProviderInfo> createSetContextResponseReturn(VasaProviderInfo value) {
        return new JAXBElement<VasaProviderInfo>(_SetContextResponseReturn_QNAME, VasaProviderInfo.class, SetContextResponse.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link IncompatibleVolume }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "IncompatibleVolume", scope = IncompatibleVolume2 .class)
    public JAXBElement<IncompatibleVolume> createIncompatibleVolume2IncompatibleVolume(IncompatibleVolume value) {
        return new JAXBElement<IncompatibleVolume>(_IncompatibleVolume2IncompatibleVolume_QNAME, IncompatibleVolume.class, IncompatibleVolume2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageProfile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "storageProfile", scope = CreateVirtualVolume.class)
    public JAXBElement<StorageProfile> createCreateVirtualVolumeStorageProfile(StorageProfile value) {
        return new JAXBElement<StorageProfile>(_CreateVirtualVolumeStorageProfile_QNAME, StorageProfile.class, CreateVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "containerCookie", scope = CreateVirtualVolume.class)
    public JAXBElement<String> createCreateVirtualVolumeContainerCookie(String value) {
        return new JAXBElement<String>(_CreateVirtualVolumeContainerCookie_QNAME, String.class, CreateVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "parameterValue", scope = NameValuePair.class)
    public JAXBElement<String> createNameValuePairParameterValue(String value) {
        return new JAXBElement<String>(_NameValuePairParameterValue_QNAME, String.class, NameValuePair.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "parameterName", scope = NameValuePair.class)
    public JAXBElement<String> createNameValuePairParameterName(String value) {
        return new JAXBElement<String>(_NameValuePairParameterName_QNAME, String.class, NameValuePair.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "moduleName", scope = MessageCatalog.class)
    public JAXBElement<String> createMessageCatalogModuleName(String value) {
        return new JAXBElement<String>(_MessageCatalogModuleName_QNAME, String.class, MessageCatalog.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "locale", scope = MessageCatalog.class)
    public JAXBElement<String> createMessageCatalogLocale(String value) {
        return new JAXBElement<String>(_MessageCatalogLocale_QNAME, String.class, MessageCatalog.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "lastModified", scope = MessageCatalog.class)
    public JAXBElement<XMLGregorianCalendar> createMessageCatalogLastModified(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_MessageCatalogLastModified_QNAME, XMLGregorianCalendar.class, MessageCatalog.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "catalogName", scope = MessageCatalog.class)
    public JAXBElement<String> createMessageCatalogCatalogName(String value) {
        return new JAXBElement<String>(_MessageCatalogCatalogName_QNAME, String.class, MessageCatalog.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "catalogVersion", scope = MessageCatalog.class)
    public JAXBElement<String> createMessageCatalogCatalogVersion(String value) {
        return new JAXBElement<String>(_MessageCatalogCatalogVersion_QNAME, String.class, MessageCatalog.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "catalogUri", scope = MessageCatalog.class)
    public JAXBElement<String> createMessageCatalogCatalogUri(String value) {
        return new JAXBElement<String>(_MessageCatalogCatalogUri_QNAME, String.class, MessageCatalog.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OperationalStatus }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://compliance.policy.data.vasa.vim.vmware.com/xsd", name = "operationalStatus", scope = ComplianceResult.class)
    public JAXBElement<OperationalStatus> createComplianceResultOperationalStatus(OperationalStatus value) {
        return new JAXBElement<OperationalStatus>(_ComplianceResultOperationalStatus_QNAME, OperationalStatus.class, ComplianceResult.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://compliance.policy.data.vasa.vim.vmware.com/xsd", name = "profileId", scope = ComplianceResult.class)
    public JAXBElement<String> createComplianceResultProfileId(String value) {
        return new JAXBElement<String>(_ComplianceResultProfileId_QNAME, String.class, ComplianceResult.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "containerCookie", scope = ResizeVirtualVolume.class)
    public JAXBElement<String> createResizeVirtualVolumeContainerCookie(String value) {
        return new JAXBElement<String>(_CreateVirtualVolumeContainerCookie_QNAME, String.class, ResizeVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "arrayId", scope = GetCurrentTask.class)
    public JAXBElement<String> createGetCurrentTaskArrayId(String value) {
        return new JAXBElement<String>(_GetCurrentTaskArrayId_QNAME, String.class, GetCurrentTask.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://policy.data.vasa.vim.vmware.com/xsd", name = "message", scope = LocalizableMessage.class)
    public JAXBElement<String> createLocalizableMessageMessage(String value) {
        return new JAXBElement<String>(_LocalizableMessageMessage_QNAME, String.class, LocalizableMessage.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotFound }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "NotFound", scope = NotFound2 .class)
    public JAXBElement<NotFound> createNotFound2NotFound(NotFound value) {
        return new JAXBElement<NotFound>(_NotFound2NotFound_QNAME, NotFound.class, NotFound2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "containerCookie", scope = SnapshotVirtualVolume.class)
    public JAXBElement<String> createSnapshotVirtualVolumeContainerCookie(String value) {
        return new JAXBElement<String>(_CreateVirtualVolumeContainerCookie_QNAME, String.class, SnapshotVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link CapabilityConstraints }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://profile.policy.data.vasa.vim.vmware.com/xsd", name = "constraints", scope = StorageProfile.class)
    public JAXBElement<CapabilityConstraints> createStorageProfileConstraints(CapabilityConstraints value) {
        return new JAXBElement<CapabilityConstraints>(_StorageProfileConstraints_QNAME, CapabilityConstraints.class, StorageProfile.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://profile.policy.data.vasa.vim.vmware.com/xsd", name = "description", scope = StorageProfile.class)
    public JAXBElement<String> createStorageProfileDescription(String value) {
        return new JAXBElement<String>(_StorageProfileDescription_QNAME, String.class, StorageProfile.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://profile.policy.data.vasa.vim.vmware.com/xsd", name = "lastUpdatedBy", scope = StorageProfile.class)
    public JAXBElement<String> createStorageProfileLastUpdatedBy(String value) {
        return new JAXBElement<String>(_StorageProfileLastUpdatedBy_QNAME, String.class, StorageProfile.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://profile.policy.data.vasa.vim.vmware.com/xsd", name = "lastUpdatedTime", scope = StorageProfile.class)
    public JAXBElement<XMLGregorianCalendar> createStorageProfileLastUpdatedTime(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_StorageProfileLastUpdatedTime_QNAME, XMLGregorianCalendar.class, StorageProfile.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Timeout }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "Timeout", scope = Timeout2 .class)
    public JAXBElement<Timeout> createTimeout2Timeout(Timeout value) {
        return new JAXBElement<Timeout>(_Timeout2Timeout_QNAME, Timeout.class, Timeout2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidLogin }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "InvalidLogin", scope = InvalidLogin2 .class)
    public JAXBElement<InvalidLogin> createInvalidLogin2InvalidLogin(InvalidLogin value) {
        return new JAXBElement<InvalidLogin>(_InvalidLogin2InvalidLogin_QNAME, InvalidLogin.class, InvalidLogin2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "capabilityName", scope = StorageCapability.class)
    public JAXBElement<String> createStorageCapabilityCapabilityName(String value) {
        return new JAXBElement<String>(_StorageCapabilityCapabilityName_QNAME, String.class, StorageCapability.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "capabilityDetail", scope = StorageCapability.class)
    public JAXBElement<String> createStorageCapabilityCapabilityDetail(String value) {
        return new JAXBElement<String>(_StorageCapabilityCapabilityDetail_QNAME, String.class, StorageCapability.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ActivateProviderFailed }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "ActivateProviderFailed", scope = ActivateProviderFailed2 .class)
    public JAXBElement<ActivateProviderFailed> createActivateProviderFailed2ActivateProviderFailed(ActivateProviderFailed value) {
        return new JAXBElement<ActivateProviderFailed>(_ActivateProviderFailed2ActivateProviderFailed_QNAME, ActivateProviderFailed.class, ActivateProviderFailed2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "uniqueIdentifier", scope = BaseStorageEntity.class)
    public JAXBElement<String> createBaseStorageEntityUniqueIdentifier(String value) {
        return new JAXBElement<String>(_BaseStorageEntityUniqueIdentifier_QNAME, String.class, BaseStorageEntity.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageProfile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "newProfile", scope = FastCloneVirtualVolume.class)
    public JAXBElement<StorageProfile> createFastCloneVirtualVolumeNewProfile(StorageProfile value) {
        return new JAXBElement<StorageProfile>(_FastCloneVirtualVolumeNewProfile_QNAME, StorageProfile.class, FastCloneVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "containerCookie", scope = FastCloneVirtualVolume.class)
    public JAXBElement<String> createFastCloneVirtualVolumeContainerCookie(String value) {
        return new JAXBElement<String>(_CreateVirtualVolumeContainerCookie_QNAME, String.class, FastCloneVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "dstUniqueId", scope = QueryDRSMigrationCapabilityForPerformance.class)
    public JAXBElement<String> createQueryDRSMigrationCapabilityForPerformanceDstUniqueId(String value) {
        return new JAXBElement<String>(_QueryDRSMigrationCapabilityForPerformanceDstUniqueId_QNAME, String.class, QueryDRSMigrationCapabilityForPerformance.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "srcUniqueId", scope = QueryDRSMigrationCapabilityForPerformance.class)
    public JAXBElement<String> createQueryDRSMigrationCapabilityForPerformanceSrcUniqueId(String value) {
        return new JAXBElement<String>(_QueryDRSMigrationCapabilityForPerformanceSrcUniqueId_QNAME, String.class, QueryDRSMigrationCapabilityForPerformance.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "entityType", scope = QueryDRSMigrationCapabilityForPerformance.class)
    public JAXBElement<String> createQueryDRSMigrationCapabilityForPerformanceEntityType(String value) {
        return new JAXBElement<String>(_QueryDRSMigrationCapabilityForPerformanceEntityType_QNAME, String.class, QueryDRSMigrationCapabilityForPerformance.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link UsageContext }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "usageContext", scope = SetContext.class)
    public JAXBElement<UsageContext> createSetContextUsageContext(UsageContext value) {
        return new JAXBElement<UsageContext>(_SetContextUsageContext_QNAME, UsageContext.class, SetContext.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TooMany }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "TooMany", scope = TooMany2 .class)
    public JAXBElement<TooMany> createTooMany2TooMany(TooMany value) {
        return new JAXBElement<TooMany>(_TooMany2TooMany_QNAME, TooMany.class, TooMany2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VirtualVolumeInfo }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "vvolInfo", scope = BatchVirtualVolumeHandleResult.class)
    public JAXBElement<VirtualVolumeInfo> createBatchVirtualVolumeHandleResultVvolInfo(VirtualVolumeInfo value) {
        return new JAXBElement<VirtualVolumeInfo>(_BatchVirtualVolumeHandleResultVvolInfo_QNAME, VirtualVolumeInfo.class, BatchVirtualVolumeHandleResult.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VirtualVolumeHandle }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "vvolHandle", scope = BatchVirtualVolumeHandleResult.class)
    public JAXBElement<VirtualVolumeHandle> createBatchVirtualVolumeHandleResultVvolHandle(VirtualVolumeHandle value) {
        return new JAXBElement<VirtualVolumeHandle>(_BatchVirtualVolumeHandleResultVvolHandle_QNAME, VirtualVolumeHandle.class, BatchVirtualVolumeHandleResult.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Object }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "fault", scope = BatchVirtualVolumeHandleResult.class)
    public JAXBElement<Object> createBatchVirtualVolumeHandleResultFault(Object value) {
        return new JAXBElement<Object>(_BatchVirtualVolumeHandleResultFault_QNAME, Object.class, BatchVirtualVolumeHandleResult.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "authType", scope = ProtocolEndpoint.class)
    public JAXBElement<String> createProtocolEndpointAuthType(String value) {
        return new JAXBElement<String>(_ProtocolEndpointAuthType_QNAME, String.class, ProtocolEndpoint.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "filePath", scope = MountInfo.class)
    public JAXBElement<String> createMountInfoFilePath(String value) {
        return new JAXBElement<String>(_MountInfoFilePath_QNAME, String.class, MountInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "serverName", scope = MountInfo.class)
    public JAXBElement<String> createMountInfoServerName(String value) {
        return new JAXBElement<String>(_MountInfoServerName_QNAME, String.class, MountInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "arrayUniqueId", scope = QueryUniqueIdentifiersForLuns.class)
    public JAXBElement<String> createQueryUniqueIdentifiersForLunsArrayUniqueId(String value) {
        return new JAXBElement<String>(_QueryUniqueIdentifiersForLunsArrayUniqueId_QNAME, String.class, QueryUniqueIdentifiersForLuns.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "fsUniqueId", scope = QueryUniqueIdentifiersForFileSystems.class)
    public JAXBElement<String> createQueryUniqueIdentifiersForFileSystemsFsUniqueId(String value) {
        return new JAXBElement<String>(_QueryUniqueIdentifiersForFileSystemsFsUniqueId_QNAME, String.class, QueryUniqueIdentifiersForFileSystems.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "entityType", scope = QueryUniqueIdentifiersForEntity.class)
    public JAXBElement<String> createQueryUniqueIdentifiersForEntityEntityType(String value) {
        return new JAXBElement<String>(_QueryDRSMigrationCapabilityForPerformanceEntityType_QNAME, String.class, QueryUniqueIdentifiersForEntity.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "containerCookie", scope = UpdateVirtualVolumeMetaData.class)
    public JAXBElement<String> createUpdateVirtualVolumeMetaDataContainerCookie(String value) {
        return new JAXBElement<String>(_CreateVirtualVolumeContainerCookie_QNAME, String.class, UpdateVirtualVolumeMetaData.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidStatisticsContext }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "InvalidStatisticsContext", scope = InvalidStatisticsContext2 .class)
    public JAXBElement<InvalidStatisticsContext> createInvalidStatisticsContext2InvalidStatisticsContext(InvalidStatisticsContext value) {
        return new JAXBElement<InvalidStatisticsContext>(_InvalidStatisticsContext2InvalidStatisticsContext_QNAME, InvalidStatisticsContext.class, InvalidStatisticsContext2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "estimatedTimeToComplete", scope = TaskInfo.class)
    public JAXBElement<XMLGregorianCalendar> createTaskInfoEstimatedTimeToComplete(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_TaskInfoEstimatedTimeToComplete_QNAME, XMLGregorianCalendar.class, TaskInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Object }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "error", scope = TaskInfo.class)
    public JAXBElement<Object> createTaskInfoError(Object value) {
        return new JAXBElement<Object>(_TaskInfoError_QNAME, Object.class, TaskInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "arrayId", scope = TaskInfo.class)
    public JAXBElement<String> createTaskInfoArrayId(String value) {
        return new JAXBElement<String>(_TaskInfoArrayId_QNAME, String.class, TaskInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Object }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "result", scope = TaskInfo.class)
    public JAXBElement<Object> createTaskInfoResult(Object value) {
        return new JAXBElement<Object>(_TaskInfoResult_QNAME, Object.class, TaskInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "fileSystemPath", scope = FileSystemInfo.class)
    public JAXBElement<String> createFileSystemInfoFileSystemPath(String value) {
        return new JAXBElement<String>(_FileSystemInfoFileSystemPath_QNAME, String.class, FileSystemInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "ipAddress", scope = FileSystemInfo.class)
    public JAXBElement<String> createFileSystemInfoIpAddress(String value) {
        return new JAXBElement<String>(_FileSystemInfoIpAddress_QNAME, String.class, FileSystemInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "fileServerName", scope = FileSystemInfo.class)
    public JAXBElement<String> createFileSystemInfoFileServerName(String value) {
        return new JAXBElement<String>(_FileSystemInfoFileServerName_QNAME, String.class, FileSystemInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "containerCookie", scope = RevertVirtualVolume.class)
    public JAXBElement<String> createRevertVirtualVolumeContainerCookie(String value) {
        return new JAXBElement<String>(_CreateVirtualVolumeContainerCookie_QNAME, String.class, RevertVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link BackingConfig }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "backingConfig", scope = StorageLun.class)
    public JAXBElement<BackingConfig> createStorageLunBackingConfig(BackingConfig value) {
        return new JAXBElement<BackingConfig>(_StorageLunBackingConfig_QNAME, BackingConfig.class, StorageLun.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "thinProvisioningStatus", scope = StorageLun.class)
    public JAXBElement<String> createStorageLunThinProvisioningStatus(String value) {
        return new JAXBElement<String>(_StorageLunThinProvisioningStatus_QNAME, String.class, StorageLun.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "esxLunIdentifier", scope = StorageLun.class)
    public JAXBElement<String> createStorageLunEsxLunIdentifier(String value) {
        return new JAXBElement<String>(_StorageLunEsxLunIdentifier_QNAME, String.class, StorageLun.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "displayName", scope = StorageLun.class)
    public JAXBElement<String> createStorageLunDisplayName(String value) {
        return new JAXBElement<String>(_StorageLunDisplayName_QNAME, String.class, StorageLun.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "existingCertificate", scope = UnregisterVASACertificate.class)
    public JAXBElement<String> createUnregisterVASACertificateExistingCertificate(String value) {
        return new JAXBElement<String>(_UnregisterVASACertificateExistingCertificate_QNAME, String.class, UnregisterVASACertificate.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://fault.vasa.vim.vmware.com/xsd", name = "faultMessageId", scope = StorageFault.class)
    public JAXBElement<String> createStorageFaultFaultMessageId(String value) {
        return new JAXBElement<String>(_StorageFaultFaultMessageId_QNAME, String.class, StorageFault.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidProfile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "InvalidProfile", scope = InvalidProfile2 .class)
    public JAXBElement<InvalidProfile> createInvalidProfile2InvalidProfile(InvalidProfile value) {
        return new JAXBElement<InvalidProfile>(_InvalidProfile2InvalidProfile_QNAME, InvalidProfile.class, InvalidProfile2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://fault.vasa.vim.vmware.com/xsd", name = "entityType", scope = InactiveProvider.class)
    public JAXBElement<String> createInactiveProviderEntityType(String value) {
        return new JAXBElement<String>(_InactiveProviderEntityType_QNAME, String.class, InactiveProvider.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://fault.vasa.vim.vmware.com/xsd", name = "entityId", scope = InactiveProvider.class)
    public JAXBElement<String> createInactiveProviderEntityId(String value) {
        return new JAXBElement<String>(_InactiveProviderEntityId_QNAME, String.class, InactiveProvider.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidSession }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "InvalidSession", scope = InvalidSession2 .class)
    public JAXBElement<InvalidSession> createInvalidSession2InvalidSession(InvalidSession value) {
        return new JAXBElement<InvalidSession>(_InvalidSession2InvalidSession_QNAME, InvalidSession.class, InvalidSession2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "modelId", scope = VendorModel.class)
    public JAXBElement<String> createVendorModelModelId(String value) {
        return new JAXBElement<String>(_VendorModelModelId_QNAME, String.class, VendorModel.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "vendorId", scope = VendorModel.class)
    public JAXBElement<String> createVendorModelVendorId(String value) {
        return new JAXBElement<String>(_VendorModelVendorId_QNAME, String.class, VendorModel.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidCertificate }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "InvalidCertificate", scope = InvalidCertificate2 .class)
    public JAXBElement<InvalidCertificate> createInvalidCertificate2InvalidCertificate(InvalidCertificate value) {
        return new JAXBElement<InvalidCertificate>(_InvalidCertificate2InvalidCertificate_QNAME, InvalidCertificate.class, InvalidCertificate2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "containerCookie", scope = BindVirtualVolume.class)
    public JAXBElement<String> createBindVirtualVolumeContainerCookie(String value) {
        return new JAXBElement<String>(_CreateVirtualVolumeContainerCookie_QNAME, String.class, BindVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Object }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://capability.policy.data.vasa.vim.vmware.com/xsd", name = "defaultValue", scope = PropertyMetadata.class)
    public JAXBElement<Object> createPropertyMetadataDefaultValue(Object value) {
        return new JAXBElement<Object>(_PropertyMetadataDefaultValue_QNAME, Object.class, PropertyMetadata.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://capability.policy.data.vasa.vim.vmware.com/xsd", name = "requirementsTypeHint", scope = PropertyMetadata.class)
    public JAXBElement<String> createPropertyMetadataRequirementsTypeHint(String value) {
        return new JAXBElement<String>(_PropertyMetadataRequirementsTypeHint_QNAME, String.class, PropertyMetadata.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "entityType", scope = GetNumberOfEntities.class)
    public JAXBElement<String> createGetNumberOfEntitiesEntityType(String value) {
        return new JAXBElement<String>(_QueryDRSMigrationCapabilityForPerformanceEntityType_QNAME, String.class, GetNumberOfEntities.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "newContainerId", scope = CloneVirtualVolume.class)
    public JAXBElement<String> createCloneVirtualVolumeNewContainerId(String value) {
        return new JAXBElement<String>(_CloneVirtualVolumeNewContainerId_QNAME, String.class, CloneVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageProfile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "newProfile", scope = CloneVirtualVolume.class)
    public JAXBElement<StorageProfile> createCloneVirtualVolumeNewProfile(StorageProfile value) {
        return new JAXBElement<StorageProfile>(_FastCloneVirtualVolumeNewProfile_QNAME, StorageProfile.class, CloneVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "containerCookie", scope = CloneVirtualVolume.class)
    public JAXBElement<String> createCloneVirtualVolumeContainerCookie(String value) {
        return new JAXBElement<String>(_CreateVirtualVolumeContainerCookie_QNAME, String.class, CloneVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "baseVvolId", scope = UnsharedChunksVirtualVolume.class)
    public JAXBElement<String> createUnsharedChunksVirtualVolumeBaseVvolId(String value) {
        return new JAXBElement<String>(_UnsharedChunksVirtualVolumeBaseVvolId_QNAME, String.class, UnsharedChunksVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "protocolEndPointType", scope = StorageContainer.class)
    public JAXBElement<String> createStorageContainerProtocolEndPointType(String value) {
        return new JAXBElement<String>(_StorageContainerProtocolEndPointType_QNAME, String.class, StorageContainer.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageProfile }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "storageProfile", scope = PrepareToSnapshotVirtualVolume.class)
    public JAXBElement<StorageProfile> createPrepareToSnapshotVirtualVolumeStorageProfile(StorageProfile value) {
        return new JAXBElement<StorageProfile>(_CreateVirtualVolumeStorageProfile_QNAME, StorageProfile.class, PrepareToSnapshotVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "containerCookie", scope = PrepareToSnapshotVirtualVolume.class)
    public JAXBElement<String> createPrepareToSnapshotVirtualVolumeContainerCookie(String value) {
        return new JAXBElement<String>(_CreateVirtualVolumeContainerCookie_QNAME, String.class, PrepareToSnapshotVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VasaProviderInfo }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "return", scope = RegisterVASACertificateResponse.class)
    public JAXBElement<VasaProviderInfo> createRegisterVASACertificateResponseReturn(VasaProviderInfo value) {
        return new JAXBElement<VasaProviderInfo>(_SetContextResponseReturn_QNAME, VasaProviderInfo.class, RegisterVASACertificateResponse.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "modelId", scope = StorageArray.class)
    public JAXBElement<String> createStorageArrayModelId(String value) {
        return new JAXBElement<String>(_VendorModelModelId_QNAME, String.class, StorageArray.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "firmware", scope = StorageArray.class)
    public JAXBElement<String> createStorageArrayFirmware(String value) {
        return new JAXBElement<String>(_StorageArrayFirmware_QNAME, String.class, StorageArray.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "vendorId", scope = StorageArray.class)
    public JAXBElement<String> createStorageArrayVendorId(String value) {
        return new JAXBElement<String>(_VendorModelVendorId_QNAME, String.class, StorageArray.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "arrayName", scope = StorageArray.class)
    public JAXBElement<String> createStorageArrayArrayName(String value) {
        return new JAXBElement<String>(_StorageArrayArrayName_QNAME, String.class, StorageArray.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "newCertificate", scope = RegisterVASACertificate.class)
    public JAXBElement<String> createRegisterVASACertificateNewCertificate(String value) {
        return new JAXBElement<String>(_RegisterVASACertificateNewCertificate_QNAME, String.class, RegisterVASACertificate.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "userName", scope = RegisterVASACertificate.class)
    public JAXBElement<String> createRegisterVASACertificateUserName(String value) {
        return new JAXBElement<String>(_RegisterVASACertificateUserName_QNAME, String.class, RegisterVASACertificate.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "password", scope = RegisterVASACertificate.class)
    public JAXBElement<String> createRegisterVASACertificatePassword(String value) {
        return new JAXBElement<String>(_RegisterVASACertificatePassword_QNAME, String.class, RegisterVASACertificate.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotImplemented }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "NotImplemented", scope = NotImplemented2 .class)
    public JAXBElement<NotImplemented> createNotImplemented2NotImplemented(NotImplemented value) {
        return new JAXBElement<NotImplemented>(_NotImplemented2NotImplemented_QNAME, NotImplemented.class, NotImplemented2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link BatchErrorResult }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "errorResult", scope = BatchReturnStatus.class)
    public JAXBElement<BatchErrorResult> createBatchReturnStatusErrorResult(BatchErrorResult value) {
        return new JAXBElement<BatchErrorResult>(_BatchReturnStatusErrorResult_QNAME, BatchErrorResult.class, BatchReturnStatus.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LostAlarm }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "LostAlarm", scope = LostAlarm2 .class)
    public JAXBElement<LostAlarm> createLostAlarm2LostAlarm(LostAlarm value) {
        return new JAXBElement<LostAlarm>(_LostAlarm2LostAlarm_QNAME, LostAlarm.class, LostAlarm2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "portWwn", scope = HostInitiatorInfo.class)
    public JAXBElement<String> createHostInitiatorInfoPortWwn(String value) {
        return new JAXBElement<String>(_HostInitiatorInfoPortWwn_QNAME, String.class, HostInitiatorInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "nodeWwn", scope = HostInitiatorInfo.class)
    public JAXBElement<String> createHostInitiatorInfoNodeWwn(String value) {
        return new JAXBElement<String>(_HostInitiatorInfoNodeWwn_QNAME, String.class, HostInitiatorInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "iscsiIdentifier", scope = HostInitiatorInfo.class)
    public JAXBElement<String> createHostInitiatorInfoIscsiIdentifier(String value) {
        return new JAXBElement<String>(_HostInitiatorInfoIscsiIdentifier_QNAME, String.class, HostInitiatorInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "name", scope = VasaProviderInfo.class)
    public JAXBElement<String> createVasaProviderInfoName(String value) {
        return new JAXBElement<String>(_VasaProviderInfoName_QNAME, String.class, VasaProviderInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "sessionId", scope = VasaProviderInfo.class)
    public JAXBElement<String> createVasaProviderInfoSessionId(String value) {
        return new JAXBElement<String>(_VasaProviderInfoSessionId_QNAME, String.class, VasaProviderInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "vasaProviderVersion", scope = VasaProviderInfo.class)
    public JAXBElement<String> createVasaProviderInfoVasaProviderVersion(String value) {
        return new JAXBElement<String>(_VasaProviderInfoVasaProviderVersion_QNAME, String.class, VasaProviderInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "defaultNamespace", scope = VasaProviderInfo.class)
    public JAXBElement<String> createVasaProviderInfoDefaultNamespace(String value) {
        return new JAXBElement<String>(_VasaProviderInfoDefaultNamespace_QNAME, String.class, VasaProviderInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "vasaApiVersion", scope = VasaProviderInfo.class)
    public JAXBElement<String> createVasaProviderInfoVasaApiVersion(String value) {
        return new JAXBElement<String>(_VasaProviderInfoVasaApiVersion_QNAME, String.class, VasaProviderInfo.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://compliance.policy.data.vasa.vim.vmware.com/xsd", name = "profileId", scope = ComplianceSubject.class)
    public JAXBElement<String> createComplianceSubjectProfileId(String value) {
        return new JAXBElement<String>(_ComplianceResultProfileId_QNAME, String.class, ComplianceSubject.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://compliance.policy.data.vasa.vim.vmware.com/xsd", name = "operationETA", scope = OperationalStatus.class)
    public JAXBElement<XMLGregorianCalendar> createOperationalStatusOperationETA(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_OperationalStatusOperationETA_QNAME, XMLGregorianCalendar.class, OperationalStatus.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "lunId", scope = ProtocolEndpointInbandId.class)
    public JAXBElement<String> createProtocolEndpointInbandIdLunId(String value) {
        return new JAXBElement<String>(_ProtocolEndpointInbandIdLunId_QNAME, String.class, ProtocolEndpointInbandId.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "serverMount", scope = ProtocolEndpointInbandId.class)
    public JAXBElement<String> createProtocolEndpointInbandIdServerMount(String value) {
        return new JAXBElement<String>(_ProtocolEndpointInbandIdServerMount_QNAME, String.class, ProtocolEndpointInbandId.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "serverMajor", scope = ProtocolEndpointInbandId.class)
    public JAXBElement<String> createProtocolEndpointInbandIdServerMajor(String value) {
        return new JAXBElement<String>(_ProtocolEndpointInbandIdServerMajor_QNAME, String.class, ProtocolEndpointInbandId.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "serverScope", scope = ProtocolEndpointInbandId.class)
    public JAXBElement<String> createProtocolEndpointInbandIdServerScope(String value) {
        return new JAXBElement<String>(_ProtocolEndpointInbandIdServerScope_QNAME, String.class, ProtocolEndpointInbandId.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "ipAddress", scope = ProtocolEndpointInbandId.class)
    public JAXBElement<String> createProtocolEndpointInbandIdIpAddress(String value) {
        return new JAXBElement<String>(_ProtocolEndpointInbandIdIpAddress_QNAME, String.class, ProtocolEndpointInbandId.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "serverMinor", scope = ProtocolEndpointInbandId.class)
    public JAXBElement<String> createProtocolEndpointInbandIdServerMinor(String value) {
        return new JAXBElement<String>(_ProtocolEndpointInbandIdServerMinor_QNAME, String.class, ProtocolEndpointInbandId.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OperationalStatus }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "operationalStatus", scope = ComplianceAlarm.class)
    public JAXBElement<OperationalStatus> createComplianceAlarmOperationalStatus(OperationalStatus value) {
        return new JAXBElement<OperationalStatus>(_ComplianceAlarmOperationalStatus_QNAME, OperationalStatus.class, ComplianceAlarm.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link VasaProviderBusy }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "VasaProviderBusy", scope = VasaProviderBusy2 .class)
    public JAXBElement<VasaProviderBusy> createVasaProviderBusy2VasaProviderBusy(VasaProviderBusy value) {
        return new JAXBElement<VasaProviderBusy>(_VasaProviderBusy2VasaProviderBusy_QNAME, VasaProviderBusy.class, VasaProviderBusy2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "vcGuid", scope = UsageContext.class)
    public JAXBElement<String> createUsageContextVcGuid(String value) {
        return new JAXBElement<String>(_UsageContextVcGuid_QNAME, String.class, UsageContext.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "hostGuid", scope = UsageContext.class)
    public JAXBElement<String> createUsageContextHostGuid(String value) {
        return new JAXBElement<String>(_UsageContextHostGuid_QNAME, String.class, UsageContext.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://placement.policy.data.vasa.vim.vmware.com/xsd", name = "objectId", scope = ProvisioningSubject.class)
    public JAXBElement<String> createProvisioningSubjectObjectId(String value) {
        return new JAXBElement<String>(_ProvisioningSubjectObjectId_QNAME, String.class, ProvisioningSubject.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link OutOfResource }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "OutOfResource", scope = OutOfResource2 .class)
    public JAXBElement<OutOfResource> createOutOfResource2OutOfResource(OutOfResource value) {
        return new JAXBElement<OutOfResource>(_OutOfResource2OutOfResource_QNAME, OutOfResource.class, OutOfResource2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link PermissionDenied }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "PermissionDenied", scope = PermissionDenied2 .class)
    public JAXBElement<PermissionDenied> createPermissionDenied2PermissionDenied(PermissionDenied value) {
        return new JAXBElement<PermissionDenied>(_PermissionDenied2PermissionDenied_QNAME, PermissionDenied.class, PermissionDenied2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ResourceInUse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "ResourceInUse", scope = ResourceInUse2 .class)
    public JAXBElement<ResourceInUse> createResourceInUse2ResourceInUse(ResourceInUse value) {
        return new JAXBElement<ResourceInUse>(_ResourceInUse2ResourceInUse_QNAME, ResourceInUse.class, ResourceInUse2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "currentActiveProviderUrl", scope = ActivationSpec.class)
    public JAXBElement<String> createActivationSpecCurrentActiveProviderUrl(String value) {
        return new JAXBElement<String>(_ActivationSpecCurrentActiveProviderUrl_QNAME, String.class, ActivationSpec.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "hostUuid", scope = UnbindAllVirtualVolumesFromHost.class)
    public JAXBElement<String> createUnbindAllVirtualVolumesFromHostHostUuid(String value) {
        return new JAXBElement<String>(_UnbindAllVirtualVolumesFromHostHostUuid_QNAME, String.class, UnbindAllVirtualVolumesFromHost.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "containerCookie", scope = CopyDiffsToVirtualVolume.class)
    public JAXBElement<String> createCopyDiffsToVirtualVolumeContainerCookie(String value) {
        return new JAXBElement<String>(_CreateVirtualVolumeContainerCookie_QNAME, String.class, CopyDiffsToVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InvalidArgument }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "InvalidArgument", scope = InvalidArgument2 .class)
    public JAXBElement<InvalidArgument> createInvalidArgument2InvalidArgument(InvalidArgument value) {
        return new JAXBElement<InvalidArgument>(_InvalidArgument2InvalidArgument_QNAME, InvalidArgument.class, InvalidArgument2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "objectId", scope = StorageAlarm.class)
    public JAXBElement<String> createStorageAlarmObjectId(String value) {
        return new JAXBElement<String>(_StorageAlarmObjectId_QNAME, String.class, StorageAlarm.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "status", scope = StorageAlarm.class)
    public JAXBElement<String> createStorageAlarmStatus(String value) {
        return new JAXBElement<String>(_StorageAlarmStatus_QNAME, String.class, StorageAlarm.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "alarmTimeStamp", scope = StorageAlarm.class)
    public JAXBElement<XMLGregorianCalendar> createStorageAlarmAlarmTimeStamp(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_StorageAlarmAlarmTimeStamp_QNAME, XMLGregorianCalendar.class, StorageAlarm.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "messageId", scope = StorageAlarm.class)
    public JAXBElement<String> createStorageAlarmMessageId(String value) {
        return new JAXBElement<String>(_StorageAlarmMessageId_QNAME, String.class, StorageAlarm.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "alarmType", scope = StorageAlarm.class)
    public JAXBElement<String> createStorageAlarmAlarmType(String value) {
        return new JAXBElement<String>(_StorageAlarmAlarmType_QNAME, String.class, StorageAlarm.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "objectType", scope = StorageAlarm.class)
    public JAXBElement<String> createStorageAlarmObjectType(String value) {
        return new JAXBElement<String>(_StorageAlarmObjectType_QNAME, String.class, StorageAlarm.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "containerId", scope = StorageAlarm.class)
    public JAXBElement<String> createStorageAlarmContainerId(String value) {
        return new JAXBElement<String>(_StorageAlarmContainerId_QNAME, String.class, StorageAlarm.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotCancellable }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "NotCancellable", scope = NotCancellable2 .class)
    public JAXBElement<NotCancellable> createNotCancellable2NotCancellable(NotCancellable value) {
        return new JAXBElement<NotCancellable>(_NotCancellable2NotCancellable_QNAME, NotCancellable.class, NotCancellable2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotSupported }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "NotSupported", scope = NotSupported2 .class)
    public JAXBElement<NotSupported> createNotSupported2NotSupported(NotSupported value) {
        return new JAXBElement<NotSupported>(_NotSupported2NotSupported_QNAME, NotSupported.class, NotSupported2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SnapshotTooMany }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "SnapshotTooMany", scope = SnapshotTooMany2 .class)
    public JAXBElement<SnapshotTooMany> createSnapshotTooMany2SnapshotTooMany(SnapshotTooMany value) {
        return new JAXBElement<SnapshotTooMany>(_SnapshotTooMany2SnapshotTooMany_QNAME, SnapshotTooMany.class, SnapshotTooMany2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "objectId", scope = StorageEvent.class)
    public JAXBElement<String> createStorageEventObjectId(String value) {
        return new JAXBElement<String>(_StorageAlarmObjectId_QNAME, String.class, StorageEvent.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "arrayId", scope = StorageEvent.class)
    public JAXBElement<String> createStorageEventArrayId(String value) {
        return new JAXBElement<String>(_StorageEventArrayId_QNAME, String.class, StorageEvent.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "eventObjType", scope = StorageEvent.class)
    public JAXBElement<String> createStorageEventEventObjType(String value) {
        return new JAXBElement<String>(_StorageEventEventObjType_QNAME, String.class, StorageEvent.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "messageId", scope = StorageEvent.class)
    public JAXBElement<String> createStorageEventMessageId(String value) {
        return new JAXBElement<String>(_StorageAlarmMessageId_QNAME, String.class, StorageEvent.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "eventConfigType", scope = StorageEvent.class)
    public JAXBElement<String> createStorageEventEventConfigType(String value) {
        return new JAXBElement<String>(_StorageEventEventConfigType_QNAME, String.class, StorageEvent.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "eventType", scope = StorageEvent.class)
    public JAXBElement<String> createStorageEventEventType(String value) {
        return new JAXBElement<String>(_StorageEventEventType_QNAME, String.class, StorageEvent.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "eventTimeStamp", scope = StorageEvent.class)
    public JAXBElement<XMLGregorianCalendar> createStorageEventEventTimeStamp(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_StorageEventEventTimeStamp_QNAME, XMLGregorianCalendar.class, StorageEvent.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "fileSystemVersion", scope = StorageFileSystem.class)
    public JAXBElement<String> createStorageFileSystemFileSystemVersion(String value) {
        return new JAXBElement<String>(_StorageFileSystemFileSystemVersion_QNAME, String.class, StorageFileSystem.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link BackingConfig }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "backingConfig", scope = StorageFileSystem.class)
    public JAXBElement<BackingConfig> createStorageFileSystemBackingConfig(BackingConfig value) {
        return new JAXBElement<BackingConfig>(_StorageLunBackingConfig_QNAME, BackingConfig.class, StorageFileSystem.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "thinProvisioningStatus", scope = StorageFileSystem.class)
    public JAXBElement<String> createStorageFileSystemThinProvisioningStatus(String value) {
        return new JAXBElement<String>(_StorageLunThinProvisioningStatus_QNAME, String.class, StorageFileSystem.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "fileSystem", scope = StorageFileSystem.class)
    public JAXBElement<String> createStorageFileSystemFileSystem(String value) {
        return new JAXBElement<String>(_StorageFileSystemFileSystem_QNAME, String.class, StorageFileSystem.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://vvol.data.vasa.vim.vmware.com/xsd", name = "value", scope = QueryConstraint.class)
    public JAXBElement<String> createQueryConstraintValue(String value) {
        return new JAXBElement<String>(_QueryConstraintValue_QNAME, String.class, QueryConstraint.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "thinProvisionBackingIdentifier", scope = BackingConfig.class)
    public JAXBElement<String> createBackingConfigThinProvisionBackingIdentifier(String value) {
        return new JAXBElement<String>(_BackingConfigThinProvisionBackingIdentifier_QNAME, String.class, BackingConfig.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "deduplicationBackingIdentifier", scope = BackingConfig.class)
    public JAXBElement<String> createBackingConfigDeduplicationBackingIdentifier(String value) {
        return new JAXBElement<String>(_BackingConfigDeduplicationBackingIdentifier_QNAME, String.class, BackingConfig.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "containerCookie", scope = UpdateStorageProfileForVirtualVolume.class)
    public JAXBElement<String> createUpdateStorageProfileForVirtualVolumeContainerCookie(String value) {
        return new JAXBElement<String>(_CreateVirtualVolumeContainerCookie_QNAME, String.class, UpdateStorageProfileForVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "portWwn", scope = StoragePort.class)
    public JAXBElement<String> createStoragePortPortWwn(String value) {
        return new JAXBElement<String>(_HostInitiatorInfoPortWwn_QNAME, String.class, StoragePort.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "nodeWwn", scope = StoragePort.class)
    public JAXBElement<String> createStoragePortNodeWwn(String value) {
        return new JAXBElement<String>(_HostInitiatorInfoNodeWwn_QNAME, String.class, StoragePort.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "iscsiIdentifier", scope = StoragePort.class)
    public JAXBElement<String> createStoragePortIscsiIdentifier(String value) {
        return new JAXBElement<String>(_HostInitiatorInfoIscsiIdentifier_QNAME, String.class, StoragePort.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://data.vasa.vim.vmware.com/xsd", name = "portType", scope = StoragePort.class)
    public JAXBElement<String> createStoragePortPortType(String value) {
        return new JAXBElement<String>(_StoragePortPortType_QNAME, String.class, StoragePort.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "containerCookie", scope = DeleteVirtualVolume.class)
    public JAXBElement<String> createDeleteVirtualVolumeContainerCookie(String value) {
        return new JAXBElement<String>(_CreateVirtualVolumeContainerCookie_QNAME, String.class, DeleteVirtualVolume.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://capability.policy.data.vasa.vim.vmware.com/xsd", name = "keyId", scope = CapabilityMetadata.class)
    public JAXBElement<String> createCapabilityMetadataKeyId(String value) {
        return new JAXBElement<String>(_CapabilityMetadataKeyId_QNAME, String.class, CapabilityMetadata.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Object }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "Exception", scope = Exception.class)
    public JAXBElement<Object> createExceptionException(Object value) {
        return new JAXBElement<Object>(_ExceptionException_QNAME, Object.class, Exception.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StorageFault }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "StorageFault", scope = StorageFault2 .class)
    public JAXBElement<StorageFault> createStorageFault2StorageFault(StorageFault value) {
        return new JAXBElement<StorageFault>(_StorageFault2StorageFault_QNAME, StorageFault.class, StorageFault2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link LostEvent }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "LostEvent", scope = LostEvent2 .class)
    public JAXBElement<LostEvent> createLostEvent2LostEvent(LostEvent value) {
        return new JAXBElement<LostEvent>(_LostEvent2LostEvent_QNAME, LostEvent.class, LostEvent2 .class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link InactiveProvider }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://com.vmware.vim.vasa/2.0/xsd", name = "InactiveProvider", scope = InactiveProvider2 .class)
    public JAXBElement<InactiveProvider> createInactiveProvider2InactiveProvider(InactiveProvider value) {
        return new JAXBElement<InactiveProvider>(_InactiveProvider2InactiveProvider_QNAME, InactiveProvider.class, InactiveProvider2 .class, value);
    }

}
