/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;

import org.w3c.dom.Element;

import com.google.common.collect.Lists;
import com.iwave.ext.windows.model.wmi.IScsiDevice;
import com.iwave.ext.windows.model.wmi.IScsiSession;
import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMEnumerateOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlUtils;

public class ListIScsiSessionsQuery extends WinRMEnumerateOperation<IScsiSession> {
    public static final String ISCSI_SESSION_URI = WinRMConstants.WMI_BASE_URI
            + "root/wmi/MSiSCSIInitiator_SessionClass";
    public static final String ISCSI_CONNECTION_INFORMATION_URI = WinRMConstants.WMI_BASE_URI
            + "root/wmi/MSiSCSIInitiator_ConnectionInformation";
    public static final String ISCSI_DEVICE_URI = WinRMConstants.WMI_BASE_URI
            + "root/wmi/MSiSCSIInitiator_DeviceOnSession";

    private static final XPath XPATH = XmlUtils.createXPath(
            String.format("ns=%s", ISCSI_SESSION_URI),
            String.format("ns1=%s", ISCSI_CONNECTION_INFORMATION_URI),
            String.format("ns2=%s", ISCSI_DEVICE_URI));

    private static final XPathExpression SESSIONS_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:MSiSCSIInitiator_SessionClass");
    private static final XPathExpression TARGET_ADDRESS_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:ConnectionInformation/ns1:TargetAddress");
    private static final XPathExpression TARGET_PORT_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:ConnectionInformation/ns1:TargetPort");
    private static final XPathExpression INITIATOR_NAME_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:InitiatorName");
    private static final XPathExpression SESSION_ID_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:SessionId");
    private static final XPathExpression TARGET_NAME_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:TargetName");
    private static final XPathExpression DEVICES_EXPR = XmlUtils.compileXPath(XPATH, "ns:Devices");
    private static final XPathExpression DEVICE_INTERFACE_GUID_EXPR = XmlUtils.compileXPath(XPATH,
            "ns2:DeviceInterfaceGuid");
    private static final XPathExpression DEVICE_INTERFACE_NAME_EXPR = XmlUtils.compileXPath(XPATH,
            "ns2:DeviceInterfaceName");
    private static final XPathExpression DEVICE_NUMBER_EXPR = XmlUtils.compileXPath(XPATH,
            "ns2:DeviceNumber");
    private static final XPathExpression DEVICE_INITIATOR_NAME_EXPR = XmlUtils.compileXPath(XPATH,
            "ns2:InitiatorName");
    private static final XPathExpression DEVICE_PARTITION_NUMBER_EXPR = XmlUtils.compileXPath(
            XPATH, "ns2:PartitionNumber");
    private static final XPathExpression DEVICE_SCSI_LUN_EXPR = XmlUtils.compileXPath(XPATH,
            "ns2:ScsiLun");
    private static final XPathExpression DEVICE_SCSI_BUS_EXPR = XmlUtils.compileXPath(XPATH,
            "ns2:ScsiPathId");
    private static final XPathExpression DEVICE_SCSI_PORT_EXPR = XmlUtils.compileXPath(XPATH,
            "ns2:ScsiPortNumber");
    private static final XPathExpression DEVICE_SCSI_TARGET_EXPR = XmlUtils.compileXPath(XPATH,
            "ns2:ScsiTargetId");
    private static final XPathExpression DEVICE_TARGET_NAME_EXPR = XmlUtils.compileXPath(XPATH,
            "ns2:TargetName");

    public ListIScsiSessionsQuery(WinRMTarget target) {
        super(target, ISCSI_SESSION_URI);
    }

    @Override
    protected void processItems(Element items, List<IScsiSession> results) {
        for (Element item : XmlUtils.selectElements(SESSIONS_EXPR, items)) {
            IScsiSession session = new IScsiSession();
            session.setTargetName(getTargetName(item));
            session.setTargetAddress(getTargetAddress(item));
            session.setTargetPort(getTargetPort(item));
            session.setInitiatorName(getInitiatorName(item));
            session.setSessionId(getSessionId(item));
            session.setDevices(getDevices(item));
            results.add(session);
        }
    }

    protected String getTargetName(Element item) {
        return XmlUtils.selectText(TARGET_NAME_EXPR, item);
    }

    protected String getTargetAddress(Element item) {
        return XmlUtils.selectText(TARGET_ADDRESS_EXPR, item);
    }

    protected int getTargetPort(Element item) {
        return Integer.parseInt(XmlUtils.selectText(TARGET_PORT_EXPR, item));
    }

    protected String getInitiatorName(Element item) {
        return XmlUtils.selectText(INITIATOR_NAME_EXPR, item);
    }

    protected String getSessionId(Element item) {
        return XmlUtils.selectText(SESSION_ID_EXPR, item);
    }

    protected List<IScsiDevice> getDevices(Element item) {
        List<IScsiDevice> results = Lists.newArrayList();
        for (Element e : XmlUtils.selectElements(DEVICES_EXPR, item)) {
            IScsiDevice device = new IScsiDevice();
            device.setDeviceInterfaceGuid(getDeviceInterfaceGuid(e));
            device.setDeviceInterfaceName(getDeviceInterfaceName(e));
            device.setDeviceNumber(getDeviceNumber(e));
            device.setInitiatorName(getDeviceInterfaceGuid(e));
            device.setPartitionNumber(getDevicePartitionNumber(e));
            device.setScsiBus(getDeviceScsiBus(e));
            device.setScsiTarget(getDeviceScsiTarget(e));
            device.setScsiPort(getDeviceScsiPort(e));
            device.setScsiLun(getDeviceScsiLun(e));
            device.setTargetName(getDeviceTargetName(e));
            results.add(device);
        }
        return results;
    }

    protected String getDeviceInterfaceGuid(Element item) {
        return XmlUtils.selectText(DEVICE_INTERFACE_GUID_EXPR, item);
    }

    protected String getDeviceInterfaceName(Element item) {
        return XmlUtils.selectText(DEVICE_INTERFACE_NAME_EXPR, item);
    }

    protected int getDeviceNumber(Element item) {
        Integer number = XmlUtils.selectInteger(DEVICE_NUMBER_EXPR, item);
        return number != null ? number : -1;
    }

    protected String getDeviceInitiatorName(Element item) {
        return XmlUtils.selectText(DEVICE_INITIATOR_NAME_EXPR, item);
    }

    protected int getDevicePartitionNumber(Element item) {
        Integer number = XmlUtils.selectInteger(DEVICE_PARTITION_NUMBER_EXPR, item);
        return number != null ? number : -1;
    }

    protected int getDeviceScsiBus(Element item) {
        Integer number = XmlUtils.selectInteger(DEVICE_SCSI_BUS_EXPR, item);
        return number != null ? number : -1;
    }

    protected int getDeviceScsiTarget(Element item) {
        Integer number = XmlUtils.selectInteger(DEVICE_SCSI_TARGET_EXPR, item);
        return number != null ? number : -1;
    }

    protected int getDeviceScsiPort(Element item) {
        Integer number = XmlUtils.selectInteger(DEVICE_SCSI_PORT_EXPR, item);
        return number != null ? number : -1;
    }

    protected int getDeviceScsiLun(Element item) {
        Integer number = XmlUtils.selectInteger(DEVICE_SCSI_LUN_EXPR, item);
        return number != null ? number : -1;
    }

    protected String getDeviceTargetName(Element item) {
        return XmlUtils.selectText(DEVICE_TARGET_NAME_EXPR, item);
    }
}
