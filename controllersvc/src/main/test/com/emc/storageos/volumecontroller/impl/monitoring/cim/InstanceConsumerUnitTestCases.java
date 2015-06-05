package com.emc.storageos.volumecontroller.impl.monitoring.cim;

import java.util.Hashtable;

import org.junit.Assert;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.utility.CIMConstants;

public class InstanceConsumerUnitTestCases {
    private static final Logger _logger = LoggerFactory
            .getLogger(InstanceConsumerUnitTestCases.class);

    CimConsumerTest processor = new CimConsumerTest();
    private static final String FILE_ALERT_TYPE_TAG_VALUE = "Other";
    private static final String STORAGE_VOLUME_ALERT_REPRESENTATION = "storagevolume";

    @Test
    public void verifyVolumeDeletedEventType() {
        String evtType = getEventType(processor.getVMAXVolumeViewIndication());
        _logger.info(evtType);
        Assert.assertEquals("VolumeView", evtType);
    }

    @Test
    public void verifyCIMBlockAlertIndication() {
        String indType = getIndicationType(processor.getBlockAlertIndication());
        _logger.info(indType);
        Assert.assertEquals(CimConstants.CIM_ALERT_INDICATION_TYPE, indType);
    }

    @Test
    public void verifyCIMEventIndication() {
        String indType = getIndicationType(processor
                .getVMAXVolumeViewIndication());
        _logger.info(indType);
        Assert.assertEquals(CimConstants.CIM_INST_INDICATION_TYPE, indType);
    }

    @Test
    public void verifyCIMFileAlertIndication() {
        String indType = getIndicationType(processor
                .getFileSystemAlertIndication());
        _logger.info(indType);
        Assert.assertEquals(CimConstants.CIM_ALERT_INDICATION_TYPE, indType);
    }

    public String getEventType(Hashtable<String, String> notification) {
        String instanceEventType = notification
                .get(CIMConstants.SOURCE_INSTANCE_MODEL_PATH_CLASS_SUFFIX_TAG);
        return instanceEventType;
    }

    public String getIndicationType(Hashtable<String, String> cimNotification) {
        String cimIndicationType = cimNotification
                .get(CimConstants.CIM_INDICATION_TYPE_KEY);
        return cimIndicationType;
    }

    @Test
    public void verifyNativeGuidLastIndexRemovalStatus() {
        String nativeGuidStr = "CELERRA+APM00120400480/310/server_2";
        if (nativeGuidStr != null && nativeGuidStr.length() > 0
                && nativeGuidStr.lastIndexOf("/") != -1) {
            nativeGuidStr = nativeGuidStr.substring(0,
                    nativeGuidStr.lastIndexOf("/"));
            _logger.info("after looking for extra \" / \" nativeGuid {}",
                    nativeGuidStr);
        }
        Assert.assertEquals("CELERRA+APM00120400480/310", nativeGuidStr);
    }

    @Test
    public void verifyVolumeCreateEventType() {
        String evtType = getEventType(processor.getVolumeCreatedIndication());
        _logger.info(evtType);
        Assert.assertEquals("VolumeView", evtType);
    }

    @Test
    public void verifyStorageVolumeAlert() {
        String alertClassSuffixTag = processor.getBlockAlertIndication().get(
                CIMConstants.ALERT_MANAGED_ELEMENT_CLASS_SUFFIX_TAG);
        Assert.assertEquals(alertClassSuffixTag,
                STORAGE_VOLUME_ALERT_REPRESENTATION);
    }

    @Test
    public void verifyStorageVolumeAlertType() {
        String alertType = getAlertType(processor.getBlockAlertIndication());
        _logger.info("Block Alert Type {}", alertType);
        Assert.assertEquals(
                processor.getBlockAlertIndication().get("AlertTypeTag"),
                alertType);
    }

    @Test
    public void verifyFileSystemAlertType() {
        String alertType = getAlertType(processor
                .getFileSystemAlertIndication());
        _logger.info("File Alert Type {}", alertType);
        Assert.assertEquals(
                processor.getFileSystemAlertIndication().get(
                        CIMConstants.OTHER_ALERT_TYPE_EVENT_ID), alertType);
    }

    public String getAlertType(Hashtable<String, String> cimNotification) {
        String alertType = cimNotification.get(CIMConstants.ALERT_TYPE_TAG);

        if (alertType != null
                && alertType.equalsIgnoreCase(FILE_ALERT_TYPE_TAG_VALUE)) {
            alertType = cimNotification
                    .get(CIMConstants.OTHER_ALERT_TYPE_EVENT_ID);
        }
        return alertType;
    }
}
