/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import com.iwave.ext.windows.model.wmi.Win32Service;
import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMEnumerateOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlUtils;
import org.w3c.dom.Element;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import java.util.List;

/**
 */
public class ListWin32ServicesQuery  extends WinRMEnumerateOperation<Win32Service> {
    public static final String WIN32_SERVICES_URI = WinRMConstants.WMI_BASE_URI + "root/cimv2/Win32_Service";

    private static final XPath XPATH = XmlUtils.createXPath("ns=" + WIN32_SERVICES_URI);
    private static final XPathExpression SERVICE_EXPR = XmlUtils.compileXPath(XPATH, "ns:Win32_Service");
    private static final XPathExpression NAME_EXPR = XmlUtils.compileXPath(XPATH, "ns:Name");
    private static final XPathExpression STATE_EXPR = XmlUtils.compileXPath(XPATH, "ns:State");
    private static final XPathExpression STARTED_EXPR = XmlUtils.compileXPath(XPATH, "ns:Started");

    public ListWin32ServicesQuery(WinRMTarget target) {
        super(target, WIN32_SERVICES_URI);
    }

    @Override
    protected void processItems(Element items, List<Win32Service> results) {
        for (Element item : XmlUtils.selectElements(SERVICE_EXPR, items)) {
            Win32Service service = new Win32Service();
            service.setName(getName(item));
            service.setState(getState(item));
            service.setStarted(isStarted(item));
            results.add(service);
        }
    }

    protected String getName(Element item) {
        return XmlUtils.selectText(NAME_EXPR, item);
    }

    protected String getState(Element item) {
        return XmlUtils.selectText(STATE_EXPR, item);
    }

    protected boolean isStarted(Element item) {
        return Boolean.valueOf(XmlUtils.selectText(STARTED_EXPR, item));
    }
}
