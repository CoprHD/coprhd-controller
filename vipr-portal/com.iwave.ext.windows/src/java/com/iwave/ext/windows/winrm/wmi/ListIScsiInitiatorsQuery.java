/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import java.util.List;

import javax.xml.xpath.XPathExpression;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Element;

import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMEnumerateOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlUtils;

public class ListIScsiInitiatorsQuery extends WinRMEnumerateOperation<String> {
    public static final String ISCSI_INITIATOR_URI = WinRMConstants.WMI_BASE_URI
            + "root/wmi/MSiSCSIInitiator_MethodClass";
    private static final XPathExpression ISCSI_NODE_NAME_EXPR = XmlUtils.compileXPath(
            "ns:MSiSCSIInitiator_MethodClass/ns:iSCSINodeName", "ns=" + ISCSI_INITIATOR_URI);

    public ListIScsiInitiatorsQuery(WinRMTarget target) {
        super(target, ISCSI_INITIATOR_URI);
    }

    @Override
    protected void processItems(Element items, List<String> results) {
        for (Element e : XmlUtils.selectElements(ISCSI_NODE_NAME_EXPR, items)) {
            String nodeName = XmlUtils.getText(e);
            if (StringUtils.isNotBlank(nodeName)) {
                results.add(nodeName);
            }
        }
    }
}
