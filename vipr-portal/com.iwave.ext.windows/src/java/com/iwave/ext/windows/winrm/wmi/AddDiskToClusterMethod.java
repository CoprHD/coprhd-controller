/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMInvokeOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlStringBuilder;
import com.iwave.ext.xml.XmlUtils;
import org.w3c.dom.Element;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddDiskToClusterMethod extends WinRMInvokeOperation<String> {
    public static final String CLUSTER_AVAILABLE_DISKS_URI = WinRMConstants.WMI_BASE_URI + "root/mscluster/MSCluster_AvailableDisk";
    public static final String CLUSTER_AVAILABLE_DISKS_ACTION_URI = CLUSTER_AVAILABLE_DISKS_URI + "/AddToCluster";

    private static final XPath XPATH = XmlUtils.createXPath(String.format("ns=%s", CLUSTER_AVAILABLE_DISKS_URI));
    private static final XPathExpression PATH_EXPR = XmlUtils.compileXPath(XPATH, "ns:Path");
    private static final XPathExpression RETURN_VALUE_EXPR = XmlUtils.compileXPath(XPATH, "ns:ReturnValue");

    private static final Pattern RESOURCE_NAME_PATTERN = Pattern.compile("MSCluster_Resource.Name=[\"'](.*)[\"']");

    public AddDiskToClusterMethod(WinRMTarget target, String diskId) {
        super(target, CLUSTER_AVAILABLE_DISKS_URI, CLUSTER_AVAILABLE_DISKS_ACTION_URI);
        setSelector("id", diskId);
    }

    @Override
    protected String createInput() {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.start("AddToCluster_INPUT").attr("xmlns", getResourceUri());
        xml.end();
        return xml.toString();
    }

    @Override
    protected String processOutput(Element output) {
        String path = getPath(output);

        Matcher m = RESOURCE_NAME_PATTERN.matcher(path);
        if (m.find()) {
            return m.group(1);
        } else {
            throw new RuntimeException("Unable to determine resource name from " + path);
        }
    }

    public String getPath(Element parent) {
        for (Element e : XmlUtils.selectElements(PATH_EXPR, parent)) {
            return XmlUtils.getText(e);
        }
        return null;
    }

    public boolean getReturnValue(Element parent) {
        for (Element e : XmlUtils.selectElements(RETURN_VALUE_EXPR, parent)) {
            return Boolean.parseBoolean(XmlUtils.getText(e));
        }
        return false;
    }
}
