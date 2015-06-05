/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;

import org.w3c.dom.Element;

import com.iwave.ext.windows.model.wmi.DiskDrive;
import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMEnumerateOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlUtils;

public class ListDiskDrivesQuery extends WinRMEnumerateOperation<DiskDrive> {
    public static final String DISK_URI = WinRMConstants.WMI_BASE_URI
            + "root/cimv2/Win32_DiskDrive";

    private static final XPath XPATH = XmlUtils.createXPath("ns=" + DISK_URI);
    private static final XPathExpression DISK_DRIVE_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:Win32_DiskDrive");
    private static final XPathExpression NUMBER_EXPR = XmlUtils.compileXPath(XPATH, "ns:Index");
    private static final XPathExpression NAME_EXPR = XmlUtils.compileXPath(XPATH, "ns:Name");
    private static final XPathExpression CAPTION_EXPR = XmlUtils.compileXPath(XPATH, "ns:Caption");
    private static final XPathExpression DEVICE_ID_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:DeviceID");
    private static final XPathExpression PNP_DEVICE_ID_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:PNPDeviceID");
    private static final XPathExpression INTERFACE_TYPE_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:InterfaceType");
    private static final XPathExpression SCSI_BUS_EXPR = XmlUtils.compileXPath(XPATH, "ns:SCSIBus");
    private static final XPathExpression SCSI_PORT_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:SCSIPort");
    private static final XPathExpression SCSI_TARGET_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:SCSITargetId");
    private static final XPathExpression SCSI_LUN_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:SCSILogicalUnit");
    private static final XPathExpression SERIAL_NUMBER_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:SerialNumber");
    private static final XPathExpression SIZE_EXPR = XmlUtils.compileXPath(XPATH, "ns:Size");
    private static final XPathExpression STATUS_EXPR = XmlUtils.compileXPath(XPATH, "ns:Status");
    private static final XPathExpression SIGNATURE_EXPR = XmlUtils.compileXPath(XPATH, "ns:Signature");

    public ListDiskDrivesQuery(WinRMTarget target) {
        super(target, DISK_URI);
    }

    @Override
    protected void processItems(Element items, List<DiskDrive> results) {
        for (Element item : XmlUtils.selectElements(DISK_DRIVE_EXPR, items)) {
            int number = getNumber(item);
            if (number > -1) {
                DiskDrive diskDrive = new DiskDrive();
                diskDrive.setNumber(number);
                diskDrive.setName(getName(item));
                diskDrive.setCaption(getCaption(item));
                diskDrive.setDeviceId(getDeviceId(item));
                diskDrive.setPnpDeviceId(getPNPDeviceId(item));
                diskDrive.setInterfaceType(getInterfaceType(item));
                diskDrive.setScsiBus(getScsiBus(item));
                diskDrive.setScsiPort(getScsiPort(item));
                diskDrive.setScsiTarget(getScsiTarget(item));
                diskDrive.setScsiLun(getScsiLun(item));
                diskDrive.setSerialNumber(getSerialNumber(item));
                diskDrive.setSize(getSize(item));
                diskDrive.setStatus(getStatus(item));
                diskDrive.setSignature(getSignature(item));
                results.add(diskDrive);
            }
        }
    }

    protected int getNumber(Element item) {
        Integer number = XmlUtils.selectInteger(NUMBER_EXPR, item);
        return number != null ? number : -1;
    }

    protected String getName(Element item) {
        return XmlUtils.selectText(NAME_EXPR, item);
    }

    protected String getCaption(Element item) {
        return XmlUtils.selectText(CAPTION_EXPR, item);
    }

    protected String getDeviceId(Element item) {
        return XmlUtils.selectText(DEVICE_ID_EXPR, item);
    }

    protected String getPNPDeviceId(Element item) {
        return XmlUtils.selectText(PNP_DEVICE_ID_EXPR, item);
    }

    protected String getInterfaceType(Element item) {
        return XmlUtils.selectText(INTERFACE_TYPE_EXPR, item);
    }

    protected Integer getScsiBus(Element item) {
        return XmlUtils.selectInteger(SCSI_BUS_EXPR, item);
    }

    protected Integer getScsiPort(Element item) {
        return XmlUtils.selectInteger(SCSI_PORT_EXPR, item);
    }

    protected Integer getScsiTarget(Element item) {
        return XmlUtils.selectInteger(SCSI_TARGET_EXPR, item);
    }

    protected Integer getScsiLun(Element item) {
        return XmlUtils.selectInteger(SCSI_LUN_EXPR, item);
    }

    protected String getSerialNumber(Element item) {
        return XmlUtils.selectText(SERIAL_NUMBER_EXPR, item);
    }

    protected String getSignature(Element item) {
        return XmlUtils.selectText(SIGNATURE_EXPR, item);
    }

    protected long getSize(Element item) {
        Long size = XmlUtils.selectLong(SIZE_EXPR, item);
        return size != null ? size : -1;
    }

    protected String getStatus(Element item) {
        return XmlUtils.selectText(STATUS_EXPR, item);
    }
}
