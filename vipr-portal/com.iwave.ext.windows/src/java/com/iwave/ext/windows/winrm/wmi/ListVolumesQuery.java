/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;

import org.w3c.dom.Element;

import com.iwave.ext.windows.model.wmi.Volume;
import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMEnumerateOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlUtils;

public class ListVolumesQuery extends WinRMEnumerateOperation<Volume> {
    public static final String VOLUME_URI = WinRMConstants.WMI_BASE_URI
            + "root/cimv2/Win32_Volume";

    private static final XPath XPATH = XmlUtils.createXPath("ns=" + VOLUME_URI);
    private static final XPathExpression VOLUME_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:Win32_Volume");
    private static final XPathExpression NAME_EXPR = XmlUtils.compileXPath(XPATH, "ns:Name");
    private static final XPathExpression CAPTION_EXPR = XmlUtils.compileXPath(XPATH, "ns:Caption");
    private static final XPathExpression DEVICE_ID_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:DeviceID");
    private static final XPathExpression DRIVE_LETTER_EXPR = XmlUtils.compileXPath(XPATH, "ns:DriveLetter");
    private static final XPathExpression DRIVE_LABEL_EXPR = XmlUtils.compileXPath(XPATH, "ns:Label");

    public ListVolumesQuery(WinRMTarget target) {
        super(target, VOLUME_URI);
    }

    @Override
    protected void processItems(Element items, List<Volume> results) {
        for (Element item : XmlUtils.selectElements(VOLUME_EXPR, items)) {
            Volume volume = new Volume();
            volume.setName(getName(item));
            volume.setCaption(getCaption(item));
            volume.setDeviceId(getDeviceId(item));
            volume.setDriveLetter(getDriveLetter(item));
            volume.setDriveLabel(getDriveLabel(item));
            results.add(volume);
        }
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

    protected String getDriveLetter(Element item) {
        return XmlUtils.selectText(DRIVE_LETTER_EXPR, item);
    }

    protected String getDriveLabel(Element item) {
        return XmlUtils.selectText(DRIVE_LABEL_EXPR, item);
    }

}
