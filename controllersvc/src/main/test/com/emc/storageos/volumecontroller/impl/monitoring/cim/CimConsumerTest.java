/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.monitoring.cim;

import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.volumecontroller.impl.monitoring.cim.indicationProcessor.CIMIndicationProcessor;

/**
 * A test program to verify the heart beat. This component is not directly
 * Executable for now. This should be moved into to the actual package out of
 * test to get the instance created as part spring context and to start auto
 * testing for every 2 minutes
 * */
public class CimConsumerTest {
    private static final Logger _logger = LoggerFactory
            .getLogger(CimConsumerTest.class);

    @Autowired
    private CIMIndicationProcessor processor;

    Hashtable<String, String> indicationData;
    String rowId;

    /*
     * Heart beat Test with Dummy values for Alert and Instance
     */
    public synchronized void pushHeartBeatIndication() {
        try {
            _logger.info("Pushing Heartbeat Indication....");

            if (processor == null) {
                _logger.error("No processor found to process the CIMIndication, Check Spring Configuration");
                return;
            }
            testBlockAlert();
            testFileSystemAlert();
            testUnKnownEvent();
            testVMAXBlockAlert();
            testClariionStorageVolumeActiveAndInActiveEvents();
            testClariionFileShareActiveAndInActiveEvents();
        } catch (Exception e) {
            _logger.error("Exception adding connection.", e);
        }
    }

    private void testBlockAlert() {

        // Block Alert
        _logger.info("Unit Testing BlockEvent ");
        indicationData = getBlockAlertIndication();
        processor.processIndication(indicationData);
    }

    private void testFileSystemAlert() {

        // FileSystem Alert
        _logger.info("Unit Testing FileSystem Alert");
        indicationData = getFileSystemAlertIndication();
        processor.processIndication(indicationData);
    }

    private void testUnKnownEvent() {

        // Unknown Alert
        _logger.info("Unit Testing Unknown Indication");
        indicationData = getUnknownEventIndication();
        processor.processIndication(indicationData);
    }

    private void testVMAXBlockAlert() {

        // VMAX Volume Alert
        _logger.info(" ** Unit Testing VMAX Volume Alert ** ");
        indicationData = getVMAXAlertIndication();
        processor.processIndication(indicationData);
    }

    private synchronized void testClariionStorageVolumeActiveAndInActiveEvents() {

        _logger.info(" ** Unit Testing Clariion Volume ACTIVE event** ");
        indicationData = getClariionStorageVolumeEvent();
        processor.processIndication(indicationData);

        _logger.info(" ** Unit Testing Clariion Volume INACTIVE event** ");
        indicationData = getClariionStorageVolumeInActiveEvent();
        processor.processIndication(indicationData);

        _logger.info(" ** Unit Testing Clariion Volume UNKNOWN event** ");
        indicationData = getUnknownClariionStorageVolumeEvent();
        processor.processIndication(indicationData);
    }

    private synchronized void testClariionFileShareActiveAndInActiveEvents() {

        _logger.info(" ** Unit Testing Clariion FileShare ACTIVE event** ");
        indicationData = getClariionFileShareEvent();
        processor.processIndication(indicationData);

        _logger.info(" ** Unit Testing Clariion FileShare INACTIVE event** ");
        indicationData = getClariionFileShareInActiveEvent();
        processor.processIndication(indicationData);

        _logger.info(" ** Unit Testing Clariion FileShare UNKNOWN event** ");
        indicationData = getUnknownClariionFileShareEvent();
        processor.processIndication(indicationData);

    }

    /**
     * return a simulated indication for Block alert
     * 
     * @return
     */
    public Hashtable<String, String> getFileSystemAlertIndication() {
        Hashtable<String, String> alert = new Hashtable<String, String>();
        alert.put("OtherAlertTypeFacility", "CFS");
        alert.put("IndicationSource", "10.247.66.249");
        alert.put("AlertTypeTag", "Other");
        alert.put("ProbableCause", "0");
        alert.put("CimIndicationType", "ALERT_INDICATION");
        alert.put("SystemCreationClassName", "Celerra_CelerraComputerSystem");
        alert.put("IndicationClassName", "CIM_AlertIndication");
        alert.put("OtherAlertTypeFacilityTag", "CFS");
        alert.put("OtherSeverity", "WARNING");
        alert.put("IndicationIdentifier", "1326322660816415134");
        alert.put("ProbableCauseDescription",
                "The file system size (fs /htest) dropped below the threshold of (90%)");
        alert.put("AlertingElementFormat", "0");
        alert.put("ProbableCauseDescriptionMD",
                "e9050e694ce4922f64247e871df67b84");
        alert.put("AlertingManagedElement", "0");
        alert.put("Trending", "0");
        alert.put("OtherAlertTypeComponentTag", "DART");
        alert.put("AlertType", "1");
        alert.put("OtherAlertTypeComponent", "DART");
        alert.put("OtherAlertTypeEventID", "FSBlockThresholdDropped");
        alert.put("IndicationClassTag", "CIM.AlertIndication");
        alert.put("ProbableCauseTag", "Other");
        alert.put("IndicationTime", "20120509220537");
        alert.put("OtherAlertTypeEventIdTag", "FSBlockThresholdDropped");
        alert.put("PerceivedSeverity", "0");

        return alert;
    }

    /**
     * return a simulated indication for FileSystem alert
     * 
     * @return
     */
    public Hashtable<String, String> getBlockAlertIndication() {
        Hashtable<String, String> alert = new Hashtable<String, String>();
        alert.put("IndicationSource", "10.247.87.240");
        alert.put("AlertTypeTag", "CommunicationsAlert");
        alert.put("ProbableCause", "123");
        alert.put("CimIndicationType", "ALERT_INDICATION");
        alert.put("SystemCreationClassName", "Symm_StorageSystem");
        alert.put("IndicationClassName", "OSLS_AlertIndication");
        alert.put("EventTime", "20120413143752");
        alert.put("AlertingManagedElementClassSuffixTag", "storagevolume");
        alert.put("IndicationIdentifier", "12615607002616120166");
        alert.put("AlertingManagedElementClassName", "Symm_StorageSystem");
        alert.put("ProbableCauseDescription",
                "Array Synchronization Operation Succeeded");
        alert.put("AlertingManagedElementCompositeID", "SYMMETRIX+000195900241");
        alert.put("AlertingElementFormat", "2");
        alert.put(
                "AlertingManagedElement",
                "//192.168.101.58/root/emc:symm_storagesystem.CreationClassName=\"Symm_StorageSystem\",Name=\"SYMMETRIX+000195900241\"");
        alert.put("CorrelatedIndications",
                "12615607002616120164,12615607002616120165");
        alert.put("AlertingManagedElementCreationClassName",
                "Symm_StorageSystem");
        alert.put("AlertType", "2");
        alert.put("ProviderName",
                "com.emc.cmp.osls.se.array.Session:OSLSProvider");
        alert.put("IndicationClassTag", "CIM.InstCreation");
        alert.put("EventID", "1");
        alert.put("ProbableCauseTag", "CommunicationsAlert");
        alert.put("AlertingManagedElementName", "SYMMETRIX+000195900241");
        alert.put("IndicationTime", "20120413143752");
        alert.put("AlertingManagedElementClassPrefixTag", "symm");
        alert.put("SystemName", "SYMMETRIX+000195900241");
        alert.put(
                "Description",
                "Array Synchronization operation succeeded for array <Array type=\"SYMMETRIX\" encoding=\"String\" value=\"000195900241\" />");
        alert.put("PerceivedSeverity", "2");

        return alert;
    }

    public Hashtable<String, String> getVolumeCreatedIndication() {
        Hashtable<String, String> event = new Hashtable<String, String>();
        event.put("SourceInstanceEMCSVVolumeAttributesDescription",
                "NO_PATH,SCSI3_PERSIST,THIN");
        event.put("SourceInstanceSVCreationClassName", "Symm_StorageVolume");
        event.put("SourceInstanceEMCSPTotalRawCapacity", "24586784738880");
        event.put(
                "SourceInstanceModelPath",
                "//192.168.101.58/root/emc:Symm_VolumeView.SPInstanceID=\"SYMMETRIX+000195900241+C+0001\",SVCreationClassName=\"Symm_StorageVolume\",SVDeviceID=\"001A2\",SVSystemCreationClassName=\"Symm_StorageSystem\",SVSystemName=\"SYMMETRIX+000195900241\"");
        event.put("SourceInstanceSVDataRedundancy", "0");
        event.put("SourceInstanceModelPathSVDeviceID", "001A2");
        event.put("SourceInstanceEMCSVAlignmentOffset", "0");
        event.put("SourceInstanceSSChangeableType", "0");
        event.put("SourceInstanceSPCreationClassName", "Symm_DeviceStoragePool");
        event.put("SourceInstanceEMCSVVolumeAttributes2", "0");
        event.put("SourceInstanceSVDeviceID", "001A2");
        event.put("SourceInstanceEMCSPRemainingManagedSpace", "2949207111760");
        event.put("SourceInstanceModelPathClassName", "Symm_VolumeView");
        event.put("CimIndicationType", "INST_INDICATION");
        event.put("SourceInstanceSPInstanceID", "SYMMETRIX+000195900241+C+0001");
        event.put("SourceInstanceEMCSPTotalManagedSpace", "21370186053712");
        event.put("SourceInstanceSSExtentStripeLength", "0");
        event.put("SourceInstanceSVNoSinglePointOfFailure", "false");
        event.put("SourceInstanceSSExtentStripeLengthMax", "0");
        event.put("SourceInstanceSVIsBasedOnUnderlyingRedundancy", "true");
        event.put("SourceInstanceSVName", "001A2");
        event.put("SourceInstanceSVPackageRedundancy", "0");
        event.put("SourceInstanceAFSPSpaceConsumed", "0");
        event.put("SourceInstanceSSDataRedundancyMin", "0");
        event.put("SourceInstanceSSPackageRedundancyMax", "0");
        event.put("SourceInstanceSVSystemName", "SYMMETRIX+000195900241");
        event.put("SourceInstanceEMCSVIsMapped", "false");
        event.put("SourceInstanceEMCSPRemainingRawCapacity", "0");
        event.put("SourceInstanceSSDataRedundancyGoal", "0");
        event.put("IndicationIdentifier", "12615607002616120165");
        event.put("SourceInstanceSSInstanceID", "SYMMETRIX+000195900241+001A2");
        event.put("SourceInstanceEMCSVVerifyingPriority", "0");
        event.put("SourceInstanceModelPathClassSuffixTag", "VolumeView");
        event.put("IndicationSource", "10.247.87.240");
        event.put("SourceInstanceSVElementName", "Volume 001A2");
        event.put("SourceInstanceSVNumberOfBlocks", "10487040");
        event.put("IndicationTime", "20120413143752");
        event.put("SourceInstanceSVIdentifyingDescriptions", "NAA;VPD83Type3");
        event.put("SourceInstanceEMCSVDataFormat", "FBA");
        event.put("SourceInstanceModelPathCompositeID",
                "SYMMETRIX+000195900241/001A2/SYMMETRIX+000195900241+C+0001");
        event.put("SourceInstanceClassName", "Symm_VolumeView");
        event.put("SourceInstanceSSDataRedundancyMax", "0");
        event.put("SourceInstanceSVExtentStatus", "1");
        event.put("SourceInstanceSVExtentDiscriminator", "SNIA:Allocated");
        event.put("SourceInstanceEMCSVThinlyProvisioned", "true");
        event.put("SourceInstanceSVConsumableBlocks", "10487040");
        event.put("SourceInstanceSVNameFormat", "7");
        event.put("SourceInstanceSVDeltaReservation", "30");
        event.put("IndicationClassTag", "OSLS.InstCreation");
        event.put("SourceInstanceEMCSVRebuildingPriority", "0");
        event.put("SourceInstanceModelPathSPInstanceID",
                "SYMMETRIX+000195900241+C+0001");
        event.put("SourceInstanceSVNameNamespace", "7");
        event.put("SourceInstanceEMCSPUsage", "2");
        event.put("SourceInstanceSSCreationClassName",
                "Symm_StorageVolumeSetting");
        event.put("SourceInstanceEMCSVIsBound", "false");
        event.put("SourceInstanceSSElementName", "Other RAID Level");
        event.put("SourceInstanceSVBlockSize", "512");
        event.put("SourceInstanceEMCSPPrimordial", "false");
        event.put("SourceInstanceModelPathClassPrefixTag", "Symm");
        event.put("SourceInstanceModelPathSVSystemName",
                "SYMMETRIX+000195900241");
        event.put("SourceInstanceEMCSVWWN", "60000970000195900241533030314132");
        event.put("SourceInstanceEMCSVIsComposite", "false");
        event.put("SourceInstanceModelPathSVSystemCreationClassName",
                "Symm_StorageSystem");
        event.put("SourceInstanceEMCSVVolumeAttributesDescription2", "NONE");
        event.put("SourceInstanceOperationalStatus", "2,15,32776");
        event.put("IndicationClassName", "OSLS_InstCreation");
        event.put("SourceInstanceSVPrimordial", "false");
        event.put("SourceInstanceModelPathSVCreationClassName",
                "Symm_StorageVolume");
        event.put("SourceInstanceSPPoolID", "0001");
        event.put("SourceInstanceSSExtentStripeLengthMin", "0");
        event.put("SourceInstanceEMCSVAddressOffset", "0");
        event.put("SourceInstanceSVOtherIdentifyingInfo",
                "60000970000195900241533030314132");
        event.put("SourceInstanceSVUsage", "2");
        event.put("SourceInstanceSSPackageRedundancyGoal", "0");
        event.put("SourceInstanceSSNoSinglePointOfFailure", "false");
        event.put("SourceInstanceSVClientSettableUsage", "8");
        event.put("SourceInstanceSVSystemCreationClassName",
                "Symm_StorageSystem");
        event.put("SourceInstanceEMCSVVolumeAttributes", "1,131072,268435456");
        event.put("SourceInstanceSSPackageRedundancyMin", "0");

        return event;
    }

    public Hashtable<String, String> getVMAXVolumeViewIndication() {
        Hashtable<String, String> event = new Hashtable<String, String>();
        event.put("SourceInstanceEMCSVVolumeAttributesDescription",
                "NO_PATH,SCSI3_PERSIST,THIN");
        event.put("SourceInstanceSVCreationClassName", "Symm_StorageVolume");
        event.put("SourceInstanceEMCSPTotalRawCapacity", "24586784738880");
        event.put(
                "SourceInstanceModelPath",
                "//192.168.101.58/root/emc:Symm_VolumeView.SPInstanceID=\"SYMMETRIX+000195900241+C+0001\",SVCreationClassName=\"Symm_StorageVolume\",SVDeviceID=\"001A2\",SVSystemCreationClassName=\"Symm_StorageSystem\",SVSystemName=\"SYMMETRIX+000195900241\"");
        event.put("SourceInstanceSVDataRedundancy", "0");
        event.put("SourceInstanceModelPathSVDeviceID", "001A2");
        event.put("SourceInstanceEMCSVAlignmentOffset", "0");
        event.put("SourceInstanceSSChangeableType", "0");
        event.put("SourceInstanceSPCreationClassName", "Symm_DeviceStoragePool");
        event.put("SourceInstanceEMCSVVolumeAttributes2", "0");
        event.put("SourceInstanceSVDeviceID", "001A2");
        event.put("SourceInstanceEMCSPRemainingManagedSpace", "2949207111760");
        event.put("SourceInstanceModelPathClassName", "Symm_VolumeView");
        event.put("CimIndicationType", "INST_INDICATION");
        event.put("SourceInstanceSPInstanceID", "SYMMETRIX+000195900241+C+0001");
        event.put("SourceInstanceEMCSPTotalManagedSpace", "21370186053712");
        event.put("SourceInstanceSSExtentStripeLength", "0");
        event.put("SourceInstanceSVNoSinglePointOfFailure", "false");
        event.put("SourceInstanceSSExtentStripeLengthMax", "0");
        event.put("SourceInstanceSVIsBasedOnUnderlyingRedundancy", "true");
        event.put("SourceInstanceSVName", "001A2");
        event.put("SourceInstanceSVPackageRedundancy", "0");
        event.put("SourceInstanceAFSPSpaceConsumed", "0");
        event.put("SourceInstanceSSDataRedundancyMin", "0");
        event.put("SourceInstanceSSPackageRedundancyMax", "0");
        event.put("SourceInstanceSVSystemName", "SYMMETRIX+000195900241");
        event.put("SourceInstanceEMCSVIsMapped", "false");
        event.put("SourceInstanceEMCSPRemainingRawCapacity", "0");
        event.put("SourceInstanceSSDataRedundancyGoal", "0");
        event.put("IndicationIdentifier", "12615607002616120165");
        event.put("SourceInstanceSSInstanceID", "SYMMETRIX+000195900241+001A2");
        event.put("SourceInstanceEMCSVVerifyingPriority", "0");
        event.put("SourceInstanceModelPathClassSuffixTag", "VolumeView");
        event.put("IndicationSource", "10.247.87.240");
        event.put("SourceInstanceSVElementName", "Volume 001A2");
        event.put("SourceInstanceSVNumberOfBlocks", "10487040");
        event.put("IndicationTime", "20120413143752");
        event.put("SourceInstanceSVIdentifyingDescriptions", "NAA;VPD83Type3");
        event.put("SourceInstanceEMCSVDataFormat", "FBA");
        event.put("SourceInstanceModelPathCompositeID",
                "SYMMETRIX+000195900241/001A2/SYMMETRIX+000195900241+C+0001");
        event.put("SourceInstanceClassName", "Symm_VolumeView");
        event.put("SourceInstanceSSDataRedundancyMax", "0");
        event.put("SourceInstanceSVExtentStatus", "1");
        event.put("SourceInstanceSVExtentDiscriminator", "SNIA:Allocated");
        event.put("SourceInstanceEMCSVThinlyProvisioned", "true");
        event.put("SourceInstanceSVConsumableBlocks", "10487040");
        event.put("SourceInstanceSVNameFormat", "7");
        event.put("SourceInstanceSVDeltaReservation", "30");
        event.put("IndicationClassTag", "OSLS.InstDeletion");
        event.put("SourceInstanceEMCSVRebuildingPriority", "0");
        event.put("SourceInstanceModelPathSPInstanceID",
                "SYMMETRIX+000195900241+C+0001");
        event.put("SourceInstanceSVNameNamespace", "7");
        event.put("SourceInstanceEMCSPUsage", "2");
        event.put("SourceInstanceSSCreationClassName",
                "Symm_StorageVolumeSetting");
        event.put("SourceInstanceEMCSVIsBound", "false");
        event.put("SourceInstanceSSElementName", "Other RAID Level");
        event.put("SourceInstanceSVBlockSize", "512");
        event.put("SourceInstanceEMCSPPrimordial", "false");
        event.put("SourceInstanceModelPathClassPrefixTag", "Symm");
        event.put("SourceInstanceModelPathSVSystemName",
                "SYMMETRIX+000195900241");
        event.put("SourceInstanceEMCSVWWN", "60000970000195900241533030314132");
        event.put("SourceInstanceEMCSVIsComposite", "false");
        event.put("SourceInstanceModelPathSVSystemCreationClassName",
                "Symm_StorageSystem");
        event.put("SourceInstanceEMCSVVolumeAttributesDescription2", "NONE");
        event.put("SourceInstanceOperationalStatus", "2,15,32776");
        event.put("IndicationClassName", "OSLS_InstCreation");
        event.put("SourceInstanceSVPrimordial", "false");
        event.put("SourceInstanceModelPathSVCreationClassName",
                "Symm_StorageVolume");
        event.put("SourceInstanceSPPoolID", "0001");
        event.put("SourceInstanceSSExtentStripeLengthMin", "0");
        event.put("SourceInstanceEMCSVAddressOffset", "0");
        event.put("SourceInstanceSVOtherIdentifyingInfo",
                "60000970000195900241533030314132");
        event.put("SourceInstanceSVUsage", "2");
        event.put("SourceInstanceSSPackageRedundancyGoal", "0");
        event.put("SourceInstanceSSNoSinglePointOfFailure", "false");
        event.put("SourceInstanceSVClientSettableUsage", "8");
        event.put("SourceInstanceSVSystemCreationClassName",
                "Symm_StorageSystem");
        event.put("SourceInstanceEMCSVVolumeAttributes", "1,131072,268435456");
        event.put("SourceInstanceSSPackageRedundancyMin", "0");

        return event;
    }

    public Hashtable<String, String> getUnknownEventIndication() {
        Hashtable<String, String> event = new Hashtable<String, String>();
        event.put("SourceInstanceEMCSVVolumeAttributesDescription",
                "NO_PATH,SCSI3_PERSIST,THIN");
        event.put("SourceInstanceSVCreationClassName", "Symm_StorageVolume");
        event.put("SourceInstanceEMCSPTotalRawCapacity", "24586784738880");
        event.put(
                "SourceInstanceModelPath",
                "//192.168.101.58/root/emc:Symm_VolumeView.SPInstanceID=\"SYMMETRIX+000195900241+C+0001\",SVCreationClassName=\"Symm_StorageVolume\",SVDeviceID=\"001A2\",SVSystemCreationClassName=\"Symm_StorageSystem\",SVSystemName=\"SYMMETRIX+000195900241\"");
        event.put("SourceInstanceSVDataRedundancy", "0");
        event.put("SourceInstanceModelPathSVDeviceID", "001A2");
        event.put("SourceInstanceEMCSVAlignmentOffset", "0");
        event.put("SourceInstanceSSChangeableType", "0");
        event.put("SourceInstanceSPCreationClassName", "Symm_DeviceStoragePool");
        event.put("SourceInstanceEMCSVVolumeAttributes2", "0");
        event.put("SourceInstanceSVDeviceID", "001A2");
        event.put("SourceInstanceEMCSPRemainingManagedSpace", "2949207111760");
        event.put("SourceInstanceModelPathClassName", "Symm_VolumeView");
        event.put("CimIndicationType", "INST_INDICATION");
        event.put("SourceInstanceSPInstanceID", "SYMMETRIX+000195900241+C+0001");
        event.put("SourceInstanceEMCSPTotalManagedSpace", "21370186053712");
        event.put("SourceInstanceSSExtentStripeLength", "0");
        event.put("SourceInstanceSVNoSinglePointOfFailure", "false");
        event.put("SourceInstanceSSExtentStripeLengthMax", "0");
        event.put("SourceInstanceSVIsBasedOnUnderlyingRedundancy", "true");
        event.put("SourceInstanceSVName", "001A2");
        event.put("SourceInstanceSVPackageRedundancy", "0");
        event.put("SourceInstanceAFSPSpaceConsumed", "0");
        event.put("SourceInstanceSSDataRedundancyMin", "0");
        event.put("SourceInstanceSSPackageRedundancyMax", "0");
        event.put("SourceInstanceSVSystemName", "SYMMETRIX+000195900241");
        event.put("SourceInstanceEMCSVIsMapped", "false");
        event.put("SourceInstanceEMCSPRemainingRawCapacity", "0");
        event.put("SourceInstanceSSDataRedundancyGoal", "0");
        event.put("IndicationIdentifier", "12615607002616120165");
        event.put("SourceInstanceSSInstanceID", "SYMMETRIX+000195900241+001A2");
        event.put("SourceInstanceEMCSVVerifyingPriority", "0");
        event.put("SourceInstanceModelPathClassSuffixTag", "VolumeView");
        event.put("IndicationSource", "10.247.87.240");
        event.put("SourceInstanceSVElementName", "Volume 001A2");
        event.put("SourceInstanceSVNumberOfBlocks", "10487040");
        event.put("IndicationTime", "20120413143752");
        event.put("SourceInstanceSVIdentifyingDescriptions", "NAA;VPD83Type3");
        event.put("SourceInstanceEMCSVDataFormat", "FBA");
        event.put("SourceInstanceModelPathCompositeID",
                "SYMMETRIX+000195900241/001A2/SYMMETRIX+000195900241+C+0001");
        event.put("SourceInstanceClassName", "Symm_VolumeView");
        event.put("SourceInstanceSSDataRedundancyMax", "0");
        event.put("SourceInstanceSVExtentStatus", "1");
        event.put("SourceInstanceSVExtentDiscriminator", "SNIA:Allocated");
        event.put("SourceInstanceEMCSVThinlyProvisioned", "true");
        event.put("SourceInstanceSVConsumableBlocks", "10487040");
        event.put("SourceInstanceSVNameFormat", "7");
        event.put("SourceInstanceSVDeltaReservation", "30");
        event.put("IndicationClassTag", "OSLS.InstCreation");
        event.put("SourceInstanceEMCSVRebuildingPriority", "0");
        event.put("SourceInstanceModelPathSPInstanceID",
                "SYMMETRIX+000195900241+C+0001");
        event.put("SourceInstanceSVNameNamespace", "7");
        event.put("SourceInstanceEMCSPUsage", "2");
        event.put("SourceInstanceSSCreationClassName",
                "Symm_StorageVolumeSetting");
        event.put("SourceInstanceEMCSVIsBound", "false");
        event.put("SourceInstanceSSElementName", "Other RAID Level");
        event.put("SourceInstanceSVBlockSize", "512");
        event.put("SourceInstanceEMCSPPrimordial", "false");
        event.put("SourceInstanceModelPathClassPrefixTag", "Symm");
        event.put("SourceInstanceModelPathSVSystemName",
                "SYMMETRIX+000195900241");
        event.put("SourceInstanceEMCSVWWN", "60000970000195900241533030314132");
        event.put("SourceInstanceEMCSVIsComposite", "false");
        event.put("SourceInstanceModelPathSVSystemCreationClassName",
                "Symm_StorageSystem");
        event.put("SourceInstanceEMCSVVolumeAttributesDescription2", "NONE");
        event.put("SourceInstanceOperationalStatus", "2,15");
        event.put("IndicationClassName", "OSLS_InstCreation");
        event.put("SourceInstanceSVPrimordial", "false");
        event.put("SourceInstanceModelPathSVCreationClassName",
                "Symm_StorageVolume");
        event.put("SourceInstanceSPPoolID", "0001");
        event.put("SourceInstanceSSExtentStripeLengthMin", "0");
        event.put("SourceInstanceEMCSVAddressOffset", "0");
        event.put("SourceInstanceSVOtherIdentifyingInfo",
                "60000970000195900241533030314132");
        event.put("SourceInstanceSVUsage", "2");
        event.put("SourceInstanceSSPackageRedundancyGoal", "0");
        event.put("SourceInstanceSSNoSinglePointOfFailure", "false");
        event.put("SourceInstanceSVClientSettableUsage", "8");
        event.put("SourceInstanceSVSystemCreationClassName",
                "Symm_StorageSystem");
        event.put("SourceInstanceEMCSVVolumeAttributes", "1,131072,268435456");
        event.put("SourceInstanceSSPackageRedundancyMin", "0");

        return event;
    }

    public Hashtable<String, String> getVMAXAlertIndication() {
        Hashtable<String, String> event = new Hashtable<String, String>();
        event.put("AlertingElementFormat", "2");
        event.put("AlertingManagedElementClassName", "Symm_StorageVolume");
        event.put("AssociatedStoragePoolEMCIsBound", "true");
        event.put("ProbableCause", "123");
        event.put("PerceivedSeverity", "2");
        event.put("AssociatedStoragePoolOperationalStatus", "2");
        event.put("IndicationIdentifier", "14783285533950468446");
        event.put("AssociatedStoragePoolEMCSubscribedCapacity", "0");
        event.put("OtherAlertType", "SMC+Device Config Change");
        event.put("AssociatedStoragePoolStatusDescriptions", "OK");
        event.put("AlertingManagedElementSystemName", "SYMMETRIX+000195700363");
        event.put("IndicationTime", "1338301269000");
        event.put("AssociatedStoragePoolSpaceLimit", "245258298013160");
        event.put("AssociatedStoragePoolEMCPoolID", "C+0001");
        event.put("SystemCreationClassName", "Symm_StorageSystem");
        event.put("AssociatedStoragePoolEMCTotalRawCapacity", "245282457121000");
        event.put("AlertingManagedElementSystemCreationClassName",
                "Symm_StorageSystem");
        event.put("AlertingManagedElementClassSuffixTag", "storagevolume");
        event.put("Description", "Device configuration has changed.");
        event.put("IndicationClassTag", "OSLS.AlertIndication");
        event.put("AssociatedStoragePoolEMCLocality", "2");
        event.put("AlertType", "1");
        event.put("AssociatedStoragePoolElementName", "DISK_GROUP_0001");
        event.put("IndicationSource", "10.247.66.23");
        event.put("AssociatedStoragePoolLowSpaceWarningThreshold", "0");
        event.put("AssociatedStoragePoolSpaceLimitDetermination", "2");
        event.put("AssociatedStoragePoolClassName", "Symm_DeviceStoragePool");
        event.put("AlertingManagedElementDeviceID", "0056A");
        event.put("AlertingManagedElementCreationClassName",
                "Symm_StorageVolume");
        event.put("SystemName", "SYMMETRIX+000195700363");
        event.put("AssociatedStoragePoolEMCPercentSubscribed", "0");
        event.put("EventTime", "1338301269000");
        event.put("AssociatedStoragePoolEMCEFDCacheEnabled", "false");
        event.put("AssociatedStoragePoolEMCAutoRemove", "Disabled");
        event.put("EventID", "16016878351905984396");
        event.put(
                "AlertingManagedElement",
                "//169.254.165.97/root/emc:symm_storagevolume.CreationClassName=\"Symm_StorageVolume\",DeviceID=\"0056A\",SystemCreationClassName=\"Symm_StorageSystem\",SystemName=\"SYMMETRIX+000195700363\"");
        event.put("AlertingManagedElementCompositeID",
                "SYMMETRIX+000195700363/0056A");
        event.put("AssociatedStoragePoolRemainingManagedSpace",
                "150607107661400");
        event.put("AlertingManagedElementClassPrefixTag", "symm");
        event.put("ProviderName", "EMC SMI-S Array Provider");
        event.put("CimIndicationType", "ALERT_INDICATION");
        event.put("AssociatedStoragePoolEMCRemainingRawCapacity", "0");
        event.put("AssociatedStoragePoolConsumedResourceUnits", "count");
        event.put("AssociatedStoragePoolUsage", "2");
        event.put("AssociatedStoragePoolInstanceID",
                "SYMMETRIX+000195700363+C+0001");
        event.put("AlertTypeTag", "Other");
        event.put("IndicationClassName", "OSLS_AlertIndication");
        event.put("AssociatedStoragePoolEMCOversubscribedCapacity", "0");
        event.put("AssociatedStoragePoolEMCMaxSubscriptionPercent", "0");
        event.put("AssociatedStoragePoolTotalManagedSpace", "245258298013160");
        event.put("AssociatedStoragePoolPoolID", "0001");
        event.put("AssociatedStoragePoolEMCUnconfiguredSpace", "0");
        event.put("AssociatedStoragePoolPrimordial", "false");
        return event;
    }

    public Hashtable<String, String> getClariionStorageVolumeEvent() {

        _logger.info("Returning an Clariion Storage Volume Active Event");
        Hashtable<String, String> event = new Hashtable<String, String>();

        event.put("SourceInstanceSystemName", "CLARiiON+APM00120400480");
        event.put("SourceInstanceEMCRebuildingPriority", "3");
        event.put("SourceInstanceModelPath", "CLARiiON+APM00120400480");
        event.put("AssociatedStoragePoolEMCEFDCacheEnabled", "false");
        event.put("AssociatedStoragePoolPrimordial", "false");
        event.put("SourceInstanceEnabledState", "5");
        event.put("AssociatedStoragePoolUsage", "2");
        event.put("SourceInstanceDeviceID", "00008");
        event.put("SourceInstanceModelPathClassName", "Clar_StorageVolume");
        event.put("CimIndicationType", "INST_INDICATION");
        event.put("AssociatedStoragePoolSpaceLimit", "1146785628160");
        event.put("AssociatedStoragePoolStatusDescriptions", "OK,ONLINE");
        event.put("SourceInstanceStatusInfo", "5");
        event.put("SourceInstanceEMCVerifyingPriority", "2");
        event.put("SourceInstanceParityLayout", "2");
        event.put("AssociatedStoragePoolEMCIsBound", "true");
        event.put("SourceInstanceIsBasedOnUnderlyingRedundancy", "true");
        event.put("SourceInstanceName", "00008");
        event.put("AssociatedStoragePoolEMCTotalRawCapacity", "1433482035200");
        event.put("SourceInstanceNameFormat", "7");
        event.put("AssociatedStoragePoolTotalManagedSpace", "1146785628160");
        event.put("AssociatedStoragePoolEMCAutoRemove", "Disabled");
        event.put("SourceInstanceEMCCurrentOwningStorageProcessor", "SP_B");
        event.put("SourceInstancePurpose",
                "Exposed a logical volume to the connected hosts");
        event.put("SourceInstanceAccess", "3");
        event.put("SourceInstanceDataRedundancy", "1");
        event.put("SourceInstanceHealthState", "5");
        event.put("SourceInstanceEMCVolumeAttributesDescription2", "NONE");
        event.put("AssociatedStoragePoolClassName", "Clar_DeviceStoragePool");
        event.put("SourceInstanceEMCDefaultOwningStorageProcessor", "SP_B");
        event.put("SourceInstanceBlockSize", "512");
        event.put("AssociatedStoragePoolEMCLocality", "2");
        event.put("SourceInstanceEMCIsMapped", "true");
        event.put("SourceInstanceOtherIdentifyingInfo",
                "600601605D312F00C20478F72696E111");
        event.put("AssociatedStoragePoolPoolID", "0000");
        event.put("SourceInstanceEMCVolumeAttributesDescription",
                "NO_PATH,WRITE_CACHE,READ_CACHE");
        event.put("IndicationIdentifier", "17812966775953492158");
        event.put("SourceInstanceThinlyProvisioned", "false");
        event.put("AssociatedStoragePoolEMCPercentSubscribed", "0");
        event.put("SourceInstanceModelPathClassSuffixTag", "StorageVolume");
        event.put("IndicationSource", "10.247.66.23");
        event.put("AssociatedStoragePoolSpaceLimitDetermination", "2");
        event.put("AssociatedStoragePoolLowSpaceWarningThreshold", "0");
        event.put("IndicationTime", "1338301269000");
        event.put("SourceInstanceSystemCreationClassName", "Clar_StorageSystem");
        event.put("SourceInstanceEMCWWN", "600601605D312F00C20478F72696E111");
        event.put("SourceInstanceEnabledDefault", "2");
        event.put("SourceInstanceOperationalStatus", "2,32769");
        event.put("SourceInstanceEMCRecoverPointEnabled", "false");
        event.put("SourceInstanceModelPathCompositeID",
                "CLARiiON+APM00120400480/00008");
        event.put("SourceInstanceClassName", "Clar_StorageVolume");
        event.put("SourceInstanceEMCIsImported", "false");
        event.put("SourceInstanceModelPathDeviceID", "00008");
        event.put("SourceInstanceIsComposite", "false");
        event.put("SourceInstanceRequestedState", "5");
        event.put("AssociatedStoragePoolEMCOversubscribedCapacity", "0");
        event.put("SourceInstanceTransitioningToState", "12");
        event.put("SourceInstanceEMCIsCompressed", "false");
        event.put("SourceInstanceExtentStatus", "1");
        event.put("IndicationClassTag", "OSLS.InstModification");
        event.put("AssociatedStoragePoolEMCSubscribedCapacity", "0");
        event.put("SourceInstanceEMCRaidLevel", "RAID-5");
        event.put("SourceInstanceNameNamespace", "7");
        event.put("SourceInstanceEMCIsBound", "true");
        event.put("AssociatedStoragePoolEMCRemainingRawCapacity",
                "1304632944640");
        event.put("SourceInstancePrimordial", "false");
        event.put("SourceInstanceConsumableBlocks", "10485760");
        event.put("SourceInstanceCreationClassName", "Clar_StorageVolume");
        event.put("SourceInstanceEMCCompressionRate", "N/A");
        event.put("SourceInstanceNumberOfBlocks", "10485760");
        event.put("OtherSeverity",
                "LU+CLARiiON+APM00120400480+600601605D312F00C20478F72696E111");
        event.put("SourceInstanceDeltaReservation", "30");
        event.put("SourceInstanceEMCMetaDataSubscribedCapacity", "0");
        event.put("AssociatedStoragePoolElementName",
                "Concrete storage pool 0000 for APM00120400480 storage system.");
        event.put("SourceInstanceIdentifyingDescriptions", "NAA;VPD83Type3");
        event.put("SourceInstanceModelPathClassPrefixTag", "Clar");
        event.put("SourceInstanceElementName", "htest3");
        event.put("AssociatedStoragePoolConsumedResourceUnits", "count");
        event.put("SourceInstanceEMCVolumeAttributes", "1,16,32");
        event.put("AssociatedStoragePoolRemainingManagedSpace", "1043706413056");
        event.put("AssociatedStoragePoolEMCContiguousFreeBlocks", "2007031808");
        event.put("SourceInstanceEMCVolumeAttributes2", "0");
        event.put("SourceInstanceNoSinglePointOfFailure", "true");
        event.put("AssociatedStoragePoolInstanceID",
                "CLARiiON+APM00120400480+C+0000");
        event.put("SourceInstanceStorageTieringSelection", "0");
        event.put("AssociatedStoragePoolEMCUnconfiguredSpace", "0");
        event.put("SourceInstancePackageRedundancy", "1");
        event.put("SourceInstanceCaption", "LUN");
        event.put("SourceInstanceCanDelete", "true");
        event.put("SourceInstanceSeqentialAccess", "false");
        event.put("SourceInstanceEMCDataFormat", "FBA");
        event.put("SourceInstanceUsage", "2");
        event.put("AssociatedStoragePoolOperationalStatus", "2,32769");
        event.put("SourceInstanceModelPathSystemName",
                "CLARiiON+APM00120400480");
        event.put("SourceInstanceEMCAddressOffset", "170328064");
        event.put("IndicationClassName", "OSLS_InstCreation");
        event.put("SourceInstanceModelPathSystemCreationClassName",
                "Clar_StorageSystem");
        event.put("SourceInstanceEMCEFDCacheEnabled", "false");
        event.put("AssociatedStoragePoolEMCPoolID", "C+0000");
        event.put("AssociatedStoragePoolEMCMaxSubscriptionPercent", "0");
        event.put("SourceInstanceModelPathCreationClassName",
                "Clar_StorageVolume");
        event.put("SourceInstanceEMCMetaDataAllocatedCapacity", "0");
        event.put("SourceInstanceEMCIsComposite", "false");
        event.put("SourceInstanceExtentDiscriminator", "SNIA:Allocated");
        event.put("SourceInstanceEMCAlignmentOffset", "0");
        event.put("SourceInstanceEMCCompressionState", "N/A");
        event.put("SourceInstanceStatusDescriptions", "OK,ONLINE");
        return event;
    }

    public Hashtable<String, String> getClariionStorageVolumeInActiveEvent() {

        _logger.info("Returning an Clariion Storage Volume **InActive Event");

        Hashtable<String, String> event = new Hashtable<String, String>();

        event.put("SourceInstanceSystemName", "CLARiiON+APM00120400480");
        event.put("SourceInstanceEMCRebuildingPriority", "3");
        event.put("SourceInstanceModelPath", "CLARiiON+APM00120400480");
        event.put("AssociatedStoragePoolEMCEFDCacheEnabled", "false");
        event.put("AssociatedStoragePoolPrimordial", "false");
        event.put("SourceInstanceEnabledState", "5");
        event.put("AssociatedStoragePoolUsage", "2");
        event.put("SourceInstanceDeviceID", "00008");
        event.put("SourceInstanceModelPathClassName", "Clar_StorageVolume");
        event.put("CimIndicationType", "INST_INDICATION");
        event.put("AssociatedStoragePoolSpaceLimit", "1146785628160");
        event.put("AssociatedStoragePoolStatusDescriptions", "OK,ONLINE");
        event.put("SourceInstanceStatusInfo", "5");
        event.put("SourceInstanceEMCVerifyingPriority", "2");
        event.put("SourceInstanceParityLayout", "2");
        event.put("AssociatedStoragePoolEMCIsBound", "true");
        event.put("SourceInstanceIsBasedOnUnderlyingRedundancy", "true");
        event.put("SourceInstanceName", "00008");
        event.put("AssociatedStoragePoolEMCTotalRawCapacity", "1433482035200");
        event.put("SourceInstanceNameFormat", "7");
        event.put("AssociatedStoragePoolTotalManagedSpace", "1146785628160");
        event.put("AssociatedStoragePoolEMCAutoRemove", "Disabled");
        event.put("SourceInstanceEMCCurrentOwningStorageProcessor", "SP_B");
        event.put("SourceInstancePurpose",
                "Exposed a logical volume to the connected hosts");
        event.put("SourceInstanceAccess", "3");
        event.put("SourceInstanceDataRedundancy", "1");
        event.put("SourceInstanceHealthState", "5");
        event.put("SourceInstanceEMCVolumeAttributesDescription2", "NONE");
        event.put("AssociatedStoragePoolClassName", "Clar_DeviceStoragePool");
        event.put("SourceInstanceEMCDefaultOwningStorageProcessor", "SP_B");
        event.put("SourceInstanceBlockSize", "512");
        event.put("AssociatedStoragePoolEMCLocality", "2");
        event.put("SourceInstanceEMCIsMapped", "true");
        event.put("SourceInstanceOtherIdentifyingInfo",
                "600601605D312F00C20478F72696E111");
        event.put("AssociatedStoragePoolPoolID", "0000");
        event.put("SourceInstanceEMCVolumeAttributesDescription",
                "NO_PATH,WRITE_CACHE,READ_CACHE");
        event.put("IndicationIdentifier", "17812966775953492158");
        event.put("SourceInstanceThinlyProvisioned", "false");
        event.put("AssociatedStoragePoolEMCPercentSubscribed", "0");
        event.put("SourceInstanceModelPathClassSuffixTag", "StorageVolume");
        event.put("IndicationSource", "10.247.66.23");
        event.put("AssociatedStoragePoolSpaceLimitDetermination", "2");
        event.put("AssociatedStoragePoolLowSpaceWarningThreshold", "0");
        event.put("IndicationTime", "1338301269000");
        event.put("SourceInstanceSystemCreationClassName", "Clar_StorageSystem");
        event.put("SourceInstanceEMCWWN", "600601605D312F00C20478F72696E111");
        event.put("SourceInstanceEnabledDefault", "2");
        event.put("SourceInstanceOperationalStatus", "2,32769");
        event.put("SourceInstanceEMCRecoverPointEnabled", "false");
        event.put("SourceInstanceModelPathCompositeID",
                "CLARiiON+APM00120400480/00008");
        event.put("SourceInstanceClassName", "Clar_StorageVolume");
        event.put("SourceInstanceEMCIsImported", "false");
        event.put("SourceInstanceModelPathDeviceID", "00008");
        event.put("SourceInstanceIsComposite", "false");
        event.put("SourceInstanceRequestedState", "5");
        event.put("AssociatedStoragePoolEMCOversubscribedCapacity", "0");
        event.put("SourceInstanceTransitioningToState", "12");
        event.put("SourceInstanceEMCIsCompressed", "false");
        event.put("SourceInstanceExtentStatus", "1");
        event.put("IndicationClassTag", "OSLS.InstModification");
        event.put("AssociatedStoragePoolEMCSubscribedCapacity", "0");
        event.put("SourceInstanceEMCRaidLevel", "RAID-5");
        event.put("SourceInstanceNameNamespace", "7");
        event.put("SourceInstanceEMCIsBound", "true");
        event.put("AssociatedStoragePoolEMCRemainingRawCapacity",
                "1304632944640");
        event.put("SourceInstancePrimordial", "false");
        event.put("SourceInstanceConsumableBlocks", "10485760");
        event.put("SourceInstanceCreationClassName", "Clar_StorageVolume");
        event.put("SourceInstanceEMCCompressionRate", "N/A");
        event.put("SourceInstanceNumberOfBlocks", "10485760");
        event.put("OtherSeverity",
                "LU+CLARiiON+APM00120400480+600601605D312F00C20478F72696E111");
        event.put("SourceInstanceDeltaReservation", "30");
        event.put("SourceInstanceEMCMetaDataSubscribedCapacity", "0");
        event.put("AssociatedStoragePoolElementName",
                "Concrete storage pool 0000 for APM00120400480 storage system.");
        event.put("SourceInstanceIdentifyingDescriptions", "NAA;VPD83Type3");
        event.put("SourceInstanceModelPathClassPrefixTag", "Clar");
        event.put("SourceInstanceElementName", "htest3");
        event.put("AssociatedStoragePoolConsumedResourceUnits", "count");
        event.put("SourceInstanceEMCVolumeAttributes", "1,16,32");
        event.put("AssociatedStoragePoolRemainingManagedSpace", "1043706413056");
        event.put("AssociatedStoragePoolEMCContiguousFreeBlocks", "2007031808");
        event.put("SourceInstanceEMCVolumeAttributes2", "0");
        event.put("SourceInstanceNoSinglePointOfFailure", "true");
        event.put("AssociatedStoragePoolInstanceID",
                "CLARiiON+APM00120400480+C+0000");
        event.put("SourceInstanceStorageTieringSelection", "0");
        event.put("AssociatedStoragePoolEMCUnconfiguredSpace", "0");
        event.put("SourceInstancePackageRedundancy", "1");
        event.put("SourceInstanceCaption", "LUN");
        event.put("SourceInstanceCanDelete", "true");
        event.put("SourceInstanceSeqentialAccess", "false");
        event.put("SourceInstanceEMCDataFormat", "FBA");
        event.put("SourceInstanceUsage", "2");
        event.put("AssociatedStoragePoolOperationalStatus", "2,32769");
        event.put("SourceInstanceModelPathSystemName",
                "CLARiiON+APM00120400480");
        event.put("SourceInstanceEMCAddressOffset", "170328064");
        event.put("IndicationClassName", "OSLS_InstCreation");
        event.put("SourceInstanceModelPathSystemCreationClassName",
                "Clar_StorageSystem");
        event.put("SourceInstanceEMCEFDCacheEnabled", "false");
        event.put("AssociatedStoragePoolEMCPoolID", "C+0000");
        event.put("AssociatedStoragePoolEMCMaxSubscriptionPercent", "0");
        event.put("SourceInstanceModelPathCreationClassName",
                "Clar_StorageVolume");
        event.put("SourceInstanceEMCMetaDataAllocatedCapacity", "0");
        event.put("SourceInstanceEMCIsComposite", "false");
        event.put("SourceInstanceExtentDiscriminator", "SNIA:Allocated");
        event.put("SourceInstanceEMCAlignmentOffset", "0");
        event.put("SourceInstanceEMCCompressionState", "N/A");
        event.put("SourceInstanceStatusDescriptions", "OK,Dormant");
        return event;
    }

    public Hashtable<String, String> getUnknownClariionStorageVolumeEvent() {
        Hashtable<String, String> event = new Hashtable<String, String>();

        _logger.info("Returning an Clariion Storage Volume Unknown Event");

        event.put("SourceInstanceSystemName", "CLARiiON+APM00120400480");
        event.put("SourceInstanceEMCRebuildingPriority", "3");
        event.put("SourceInstanceModelPath", "CLARiiON+APM00120400480");
        event.put("AssociatedStoragePoolEMCEFDCacheEnabled", "false");
        event.put("AssociatedStoragePoolPrimordial", "false");
        event.put("SourceInstanceEnabledState", "5");
        event.put("AssociatedStoragePoolUsage", "2");
        event.put("SourceInstanceDeviceID", "00008");
        event.put("SourceInstanceModelPathClassName", "Clar_StorageVolume");
        event.put("CimIndicationType", "INST_INDICATION");
        event.put("AssociatedStoragePoolSpaceLimit", "1146785628160");
        event.put("AssociatedStoragePoolStatusDescriptions", "OK,ONLINE");
        event.put("SourceInstanceStatusInfo", "5");
        event.put("SourceInstanceEMCVerifyingPriority", "2");
        event.put("SourceInstanceParityLayout", "2");
        event.put("AssociatedStoragePoolEMCIsBound", "true");
        event.put("SourceInstanceIsBasedOnUnderlyingRedundancy", "true");
        event.put("SourceInstanceName", "00008");
        event.put("AssociatedStoragePoolEMCTotalRawCapacity", "1433482035200");
        event.put("SourceInstanceNameFormat", "7");
        event.put("AssociatedStoragePoolTotalManagedSpace", "1146785628160");
        event.put("AssociatedStoragePoolEMCAutoRemove", "Disabled");
        event.put("SourceInstanceEMCCurrentOwningStorageProcessor", "SP_B");
        event.put("SourceInstancePurpose",
                "Exposed a logical volume to the connected hosts");
        event.put("SourceInstanceAccess", "3");
        event.put("SourceInstanceDataRedundancy", "1");
        event.put("SourceInstanceHealthState", "5");
        event.put("SourceInstanceEMCVolumeAttributesDescription2", "NONE");
        event.put("AssociatedStoragePoolClassName", "Clar_DeviceStoragePool");
        event.put("SourceInstanceEMCDefaultOwningStorageProcessor", "SP_B");
        event.put("SourceInstanceBlockSize", "512");
        event.put("AssociatedStoragePoolEMCLocality", "2");
        event.put("SourceInstanceEMCIsMapped", "true");
        event.put("SourceInstanceOtherIdentifyingInfo",
                "600601605D312F00C20478F72696E111");
        event.put("AssociatedStoragePoolPoolID", "0000");
        event.put("SourceInstanceEMCVolumeAttributesDescription",
                "NO_PATH,WRITE_CACHE,READ_CACHE");
        event.put("IndicationIdentifier", "17812966775953492158");
        event.put("SourceInstanceThinlyProvisioned", "false");
        event.put("AssociatedStoragePoolEMCPercentSubscribed", "0");
        event.put("SourceInstanceModelPathClassSuffixTag", "StorageVolume");
        event.put("IndicationSource", "10.247.66.23");
        event.put("AssociatedStoragePoolSpaceLimitDetermination", "2");
        event.put("AssociatedStoragePoolLowSpaceWarningThreshold", "0");
        event.put("IndicationTime", "1338301269000");
        event.put("SourceInstanceSystemCreationClassName", "Clar_StorageSystem");
        event.put("SourceInstanceEMCWWN", "600601605D312F00C20478F72696E111");
        event.put("SourceInstanceEnabledDefault", "2");
        event.put("SourceInstanceOperationalStatus", "2,32769");
        event.put("SourceInstanceEMCRecoverPointEnabled", "false");
        event.put("SourceInstanceModelPathCompositeID",
                "CLARiiON+APM00120400480/00008");
        event.put("SourceInstanceClassName", "Clar_StorageVolume");
        event.put("SourceInstanceEMCIsImported", "false");
        event.put("SourceInstanceModelPathDeviceID", "00008");
        event.put("SourceInstanceIsComposite", "false");
        event.put("SourceInstanceRequestedState", "5");
        event.put("AssociatedStoragePoolEMCOversubscribedCapacity", "0");
        event.put("SourceInstanceTransitioningToState", "12");
        event.put("SourceInstanceEMCIsCompressed", "false");
        event.put("SourceInstanceExtentStatus", "1");
        event.put("IndicationClassTag", "OSLS.InstXYZ"); // TEST
        event.put("AssociatedStoragePoolEMCSubscribedCapacity", "0");
        event.put("SourceInstanceEMCRaidLevel", "RAID-5");
        event.put("SourceInstanceNameNamespace", "7");
        event.put("SourceInstanceEMCIsBound", "true");
        event.put("AssociatedStoragePoolEMCRemainingRawCapacity",
                "1304632944640");
        event.put("SourceInstancePrimordial", "false");
        event.put("SourceInstanceConsumableBlocks", "10485760");
        event.put("SourceInstanceCreationClassName", "Clar_StorageVolume");
        event.put("SourceInstanceEMCCompressionRate", "N/A");
        event.put("SourceInstanceNumberOfBlocks", "10485760");
        event.put("OtherSeverity",
                "LU+CLARiiON+APM00120400480+600601605D312F00C20478F72696E111");
        event.put("SourceInstanceDeltaReservation", "30");
        event.put("SourceInstanceEMCMetaDataSubscribedCapacity", "0");
        event.put("AssociatedStoragePoolElementName",
                "Concrete storage pool 0000 for APM00120400480 storage system.");
        event.put("SourceInstanceIdentifyingDescriptions", "NAA;VPD83Type3");
        event.put("SourceInstanceModelPathClassPrefixTag", "Clar");
        event.put("SourceInstanceElementName", "htest3");
        event.put("AssociatedStoragePoolConsumedResourceUnits", "count");
        event.put("SourceInstanceEMCVolumeAttributes", "1,16,32");
        event.put("AssociatedStoragePoolRemainingManagedSpace", "1043706413056");
        event.put("AssociatedStoragePoolEMCContiguousFreeBlocks", "2007031808");
        event.put("SourceInstanceEMCVolumeAttributes2", "0");
        event.put("SourceInstanceNoSinglePointOfFailure", "true");
        event.put("AssociatedStoragePoolInstanceID",
                "CLARiiON+APM00120400480+C+0000");
        event.put("SourceInstanceStorageTieringSelection", "0");
        event.put("AssociatedStoragePoolEMCUnconfiguredSpace", "0");
        event.put("SourceInstancePackageRedundancy", "1");
        event.put("SourceInstanceCaption", "LUN");
        event.put("SourceInstanceCanDelete", "true");
        event.put("SourceInstanceSeqentialAccess", "false");
        event.put("SourceInstanceEMCDataFormat", "FBA");
        event.put("SourceInstanceUsage", "2");
        event.put("AssociatedStoragePoolOperationalStatus", "2,32769");
        event.put("SourceInstanceModelPathSystemName",
                "CLARiiON+APM00120400480");
        event.put("SourceInstanceEMCAddressOffset", "170328064");
        event.put("IndicationClassName", "OSLS_InstCreation");
        event.put("SourceInstanceModelPathSystemCreationClassName",
                "Clar_StorageSystem");
        event.put("SourceInstanceEMCEFDCacheEnabled", "false");
        event.put("AssociatedStoragePoolEMCPoolID", "C+0000");
        event.put("AssociatedStoragePoolEMCMaxSubscriptionPercent", "0");
        event.put("SourceInstanceModelPathCreationClassName",
                "Clar_StorageVolume");
        event.put("SourceInstanceEMCMetaDataAllocatedCapacity", "0");
        event.put("SourceInstanceEMCIsComposite", "false");
        event.put("SourceInstanceExtentDiscriminator", "SNIA:Allocated");
        event.put("SourceInstanceEMCAlignmentOffset", "0");
        event.put("SourceInstanceEMCCompressionState", "N/A");
        event.put("SourceInstanceStatusDescriptions", "OK,Dormant");
        return event;
    }

    public Hashtable<String, String> getClariionFileShareEvent() {

        _logger.info("Returning an Clariion FileShare Active Event");

        Hashtable<String, String> event = new Hashtable<String, String>();
        event.put("SourceInstanceModelPathCompositeID", "129");
        event.put("SourceInstanceName", "129");
        event.put("IndicationSource", "10.247.66.249");
        event.put("SourceInstanceCaseSensitive", "true");
        event.put("CimIndicationType", "INST_INDICATION");
        event.put("IndicationClassName", "CIM_InstModification");
        event.put("SourceInstanceModelPathName", "129");
        event.put("SourceInstanceElementName", "vnxsanity0504131735");
        event.put("IndicationIdentifier", "1326322660816410368");
        event.put("SourceInstanceCasePreserved", "true");
        event.put("SourceInstanceModelPathClassSuffixTag",
                "UxfsLocalFileSystem");
        event.put("SourceInstanceLocalAccessDefinitionRequired", "3");
        event.put("SourceInstancePathNameSeparatorString", "/");
        event.put("SourceInstanceCreationClassName",
                "Celerra_UxfsLocalFileSystem");
        event.put("SourceInstanceModelPathCreationClassName",
                "Celerra_UxfsLocalFileSystem");
        event.put("SourceInstanceModelPathClassPrefixTag", "Celerra");
        event.put("SourceInstanceModelPath", "129");
        event.put("SourceInstanceModelPathCSName:", "");
        event.put("SourceInstanceMaxFileNameLength", "255");
        event.put("SourceInstanceModelPathCSCreationClassName",
                "Celerra_DataMoverComputerSystem");
        // -->
        event.put("IndicationClassTag", "CIM.InstModification");
        event.put("SourceInstanceFileSystemType", "21");
        event.put("IndicationTime", "1338301269000");
        event.put("SourceInstanceCSCreationClassName",
                "Celerra_DataMoverComputerSystem");
        event.put("SourceInstanceOperationalStatus", "2"); // SET THE RIGHT
                                                           // VALUE FOR TEST
        event.put("SourceInstanceCaption", "UFS:vnxsanity0504131735");
        event.put("SourceInstanceClassName", "Celerra_UxfsLocalFileSystem");
        event.put("SourceInstanceModelPathClassName",
                "Celerra_UxfsLocalFileSystem");
        return event;

    }

    public Hashtable<String, String> getClariionFileShareInActiveEvent() {
        Hashtable<String, String> event = new Hashtable<String, String>();

        _logger.info("Returning an Clariion FileShare ** In Active Event");

        event.put("SourceInstanceModelPathCompositeID", "129");
        event.put("SourceInstanceName", "129");
        event.put("IndicationSource", "10.247.66.249");
        event.put("SourceInstanceCaseSensitive", "true");
        event.put("CimIndicationType", "INST_INDICATION");
        event.put("IndicationClassName", "CIM_InstModification");
        event.put("SourceInstanceModelPathName", "129");
        event.put("SourceInstanceElementName", "vnxsanity0504131735");
        event.put("IndicationIdentifier", "1326322660816410368");
        event.put("SourceInstanceCasePreserved", "true");
        event.put("SourceInstanceModelPathClassSuffixTag",
                "UxfsLocalFileSystem");
        event.put("SourceInstanceLocalAccessDefinitionRequired", "3");
        event.put("SourceInstancePathNameSeparatorString", "/");
        event.put("SourceInstanceCreationClassName",
                "Celerra_UxfsLocalFileSystem");
        event.put("SourceInstanceModelPathCreationClassName",
                "Celerra_UxfsLocalFileSystem");
        event.put("SourceInstanceModelPathClassPrefixTag", "Celerra");
        event.put("SourceInstanceModelPath", "129");
        event.put("SourceInstanceModelPathCSName:", "");
        event.put("SourceInstanceMaxFileNameLength", "255");
        event.put("SourceInstanceModelPathCSCreationClassName",
                "Celerra_DataMoverComputerSystem");
        // -->
        event.put("IndicationClassTag", "CIM.InstModification");
        event.put("SourceInstanceFileSystemType", "21");
        event.put("IndicationTime", "1338301269000");
        event.put("SourceInstanceCSCreationClassName",
                "Celerra_DataMoverComputerSystem");
        event.put("SourceInstanceOperationalStatus", "10"); // SET THE RIGHT
                                                            // VALUE FOR TEST
        event.put("SourceInstanceCaption", "UFS:vnxsanity0504131735");
        event.put("SourceInstanceClassName", "Celerra_UxfsLocalFileSystem");
        event.put("SourceInstanceModelPathClassName",
                "Celerra_UxfsLocalFileSystem");
        return event;

    }

    public Hashtable<String, String> getUnknownClariionFileShareEvent() {
        Hashtable<String, String> event = new Hashtable<String, String>();

        _logger.info("Returning an Clariion FileShare ** In Active Event");

        event.put("SourceInstanceModelPathCompositeID", "129");
        event.put("SourceInstanceName", "129");
        event.put("IndicationSource", "10.247.66.249");
        event.put("SourceInstanceCaseSensitive", "true");
        event.put("CimIndicationType", "INST_INDICATION");
        event.put("IndicationClassName", "CIM_InstXYZ");
        event.put("SourceInstanceModelPathName", "129");
        event.put("SourceInstanceElementName", "vnxsanity0504131735");
        event.put("IndicationIdentifier", "1326322660816410368");
        event.put("SourceInstanceCasePreserved", "true");
        event.put("SourceInstanceModelPathClassSuffixTag",
                "UxfsLocalFileSystem");
        event.put("SourceInstanceLocalAccessDefinitionRequired", "3");
        event.put("SourceInstancePathNameSeparatorString", "/");
        event.put("SourceInstanceCreationClassName",
                "Celerra_UxfsLocalFileSystem");
        event.put("SourceInstanceModelPathCreationClassName",
                "Celerra_UxfsLocalFileSystem");
        event.put("SourceInstanceModelPathClassPrefixTag", "Celerra");
        event.put("SourceInstanceModelPath", "129");
        event.put("SourceInstanceModelPathCSName:", "");
        event.put("SourceInstanceMaxFileNameLength", "255");
        event.put("SourceInstanceModelPathCSCreationClassName",
                "Celerra_DataMoverComputerSystem");
        // -->
        event.put("IndicationClassTag", "CIM.InstXYZ");
        event.put("SourceInstanceFileSystemType", "21");
        event.put("IndicationTime", "1338301269000");
        event.put("SourceInstanceCSCreationClassName",
                "Celerra_DataMoverComputerSystem");
        event.put("SourceInstanceOperationalStatus", "10"); // SET THE RIGHT
                                                            // VALUE FOR TEST
        event.put("SourceInstanceCaption", "UFS:vnxsanity0504131735");
        event.put("SourceInstanceClassName", "Celerra_UxfsLocalFileSystem");
        event.put("SourceInstanceModelPathClassName",
                "Celerra_UxfsLocalFileSystem");
        return event;

    }

}
