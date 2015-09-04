/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;

import org.w3c.dom.Element;

import com.iwave.ext.windows.model.wmi.WindowsVersion;
import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMEnumerateOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlUtils;

public class GetWindowsVersionQuery extends WinRMEnumerateOperation<WindowsVersion> {
    private static final String VERSION_RESOURCE_URI = WinRMConstants.WMI_BASE_URI + "root/cimv2/Win32_OperatingSystem";
    private static final XPath XPATH = XmlUtils
            .createXPath("os=http://schemas.microsoft.com/wbem/wsman/1/wmi/root/cimv2/Win32_OperatingSystem");
    private static final XPathExpression ITEM_EXPR = XmlUtils.compileXPath(XPATH, "os:Win32_OperatingSystem");
    private static final XPathExpression VERSION_EXPR = XmlUtils.compileXPath(XPATH, "os:Version");
    private static final XPathExpression CAPTION_EXPR = XmlUtils.compileXPath(XPATH, "os:Caption");

    public GetWindowsVersionQuery(WinRMTarget target) {
        super(target, VERSION_RESOURCE_URI);
    }

    @Override
    protected void processItems(Element items, List<WindowsVersion> results) {
        for (Element item : XmlUtils.selectElements(ITEM_EXPR, items)) {
            String version = XmlUtils.selectText(VERSION_EXPR, item);
            String caption = XmlUtils.selectText(CAPTION_EXPR, item);
            results.add(new WindowsVersion(version, caption));
        }
    }
}
