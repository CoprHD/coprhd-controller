/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;
import com.google.common.collect.Lists;
import org.w3c.dom.Element;

import com.iwave.ext.windows.model.wmi.FibreChannelTargetMapping;
import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMInvokeOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlStringBuilder;
import com.iwave.ext.xml.XmlUtils;

public class GetFcpTargetMappingMethod extends
        WinRMInvokeOperation<List<FibreChannelTargetMapping>> {
    private static final String HBA_FCP_INFO_URI = WinRMConstants.WMI_BASE_URI
            + "root/wmi/MSFC_HBAFCPInfo";
    private static final String GET_FCP_TARGET_MAPPING = "GetFcpTargetMapping";
    private static final String GET_FCP_TARGET_MAPPING_URI = HBA_FCP_INFO_URI + "/"
            + GET_FCP_TARGET_MAPPING;
    private static final String HBA_FCP_SCSI_ENTRY = "root/wmi/HBAFCPScsiEntry";
    private static final String HBA_FCP_ID = "root/wmi/HBAFCPID";
    private static final String HBA_SCSI_ID = "root/wmi/HBAScsiID";

    private static final XPath XPATH = XmlUtils.createXPath(
            String.format("ns=%s", HBA_FCP_INFO_URI),
            String.format("ns1=%s%s", WinRMConstants.WMI_BASE_URI, HBA_FCP_SCSI_ENTRY),
            String.format("ns2=%s%s", WinRMConstants.WMI_BASE_URI, HBA_FCP_ID),
            String.format("ns3=%s%s", WinRMConstants.WMI_BASE_URI, HBA_SCSI_ID));
    private static final XPathExpression ENTRY_EXPR = XmlUtils.compileXPath(XPATH, "ns:Entry");
    private static final XPathExpression NODE_WWNS_EXPR = XmlUtils.compileXPath(XPATH,
            "ns1:FCPId/ns2:NodeWWN");
    private static final XPathExpression PORT_WWNS_EXPR = XmlUtils.compileXPath(XPATH,
            "ns1:FCPId/ns2:PortWWN");
    private static final XPathExpression FCP_LUN_EXPR = XmlUtils.compileXPath(XPATH,
            "ns1:FCPId/ns2:FcpLun");
    private static final XPathExpression SCSI_BUS_EXPR = XmlUtils.compileXPath(XPATH,
            "ns1:ScsiId/ns3:ScsiBusNumber");
    private static final XPathExpression SCSI_TARGET_EXPR = XmlUtils.compileXPath(XPATH,
            "ns1:ScsiId/ns3:ScsiTargetNumber");
    private static final XPathExpression SCSI_LUN_EXPR = XmlUtils.compileXPath(XPATH,
            "ns1:ScsiId/ns3:ScsiOSLun");

    private String portWWN;

    public GetFcpTargetMappingMethod(WinRMTarget target) {
        super(target, HBA_FCP_INFO_URI, GET_FCP_TARGET_MAPPING_URI);
    }

    public GetFcpTargetMappingMethod(WinRMTarget target, String instanceName, String portWWN) {
        this(target);
        setInstanceName(instanceName);
        setPortWWN(portWWN);
    }

    public void setInstanceName(String instanceName) {
        setSelector("InstanceName", instanceName);
    }

    public void setPortWWN(String portWWN) {
        this.portWWN = portWWN;
    }

    @Override
    protected String createInput() {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.start(GET_FCP_TARGET_MAPPING + "_INPUT").attr("xmlns", getResourceUri());
        for (String part : StringUtils.split(portWWN, ":")) {
            int value = Integer.parseInt(part, 16);
            xml.element("HbaPortWWN", String.valueOf(value));
        }
        xml.element("InEntryCount", "50");
        xml.end();
        return xml.toString();
    }

    @Override
    protected List<FibreChannelTargetMapping> processOutput(Element output) {
        List<FibreChannelTargetMapping> results = Lists.newArrayList();
        for (Element entry : XmlUtils.selectElements(ENTRY_EXPR, output)) {
            FibreChannelTargetMapping mapping = new FibreChannelTargetMapping();
            mapping.setNodeWWN(getNodeWWN(entry));
            mapping.setPortWWN(getPortWWN(entry));
            mapping.setFcpLun(getFcpLun(entry));
            mapping.setScsiBus(getScsiBus(entry));
            mapping.setScsiTarget(getScsiTarget(entry));
            mapping.setScsiLun(getScsiLun(entry));
            results.add(mapping);
        }
        return results;
    }

    protected String getPortWWN(Element entry) {
        StrBuilder portWWN = new StrBuilder();
        for (Element e : XmlUtils.selectElements(PORT_WWNS_EXPR, entry)) {
            portWWN.appendSeparator(":");
            int value = Integer.parseInt(XmlUtils.getText(e));
            portWWN.append(String.format("%02x", value));
        }
        return portWWN.toString();
    }

    protected String getNodeWWN(Element entry) {
        StrBuilder nodeWWN = new StrBuilder();
        for (Element e : XmlUtils.selectElements(NODE_WWNS_EXPR, entry)) {
            nodeWWN.appendSeparator(":");
            int value = Integer.parseInt(XmlUtils.getText(e));
            nodeWWN.append(String.format("%02x", value));
        }
        return nodeWWN.toString();
    }

    protected Integer getFcpLun(Element entry) {
        return Integer.parseInt(XmlUtils.selectText(FCP_LUN_EXPR, entry));
    }

    protected Integer getScsiBus(Element entry) {
        return Integer.parseInt(XmlUtils.selectText(SCSI_BUS_EXPR, entry));
    }

    protected Integer getScsiTarget(Element entry) {
        return Integer.parseInt(XmlUtils.selectText(SCSI_TARGET_EXPR, entry));
    }

    protected Integer getScsiLun(Element entry) {
        return Integer.parseInt(XmlUtils.selectText(SCSI_LUN_EXPR, entry));
    }
}
