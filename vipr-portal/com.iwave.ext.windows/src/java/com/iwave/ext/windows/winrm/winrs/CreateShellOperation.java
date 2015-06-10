/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.winrs;

import org.w3c.dom.Element;

import com.iwave.ext.windows.winrm.WinRMInvokeOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlStringBuilder;
import com.iwave.ext.xml.XmlUtils;

/**
 * Creates a new remote shell. The result of this operation is the remote ShellID.
 * 
 * @author jonnymiller
 */
public class CreateShellOperation extends WinRMInvokeOperation<String> {
    public CreateShellOperation(WinRMTarget target) {
        super(target, WinRSConstants.WINRS_CMD_URI, WinRSConstants.WINRS_CREATE_URI);
    }

    @Override
    protected String createInput() {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.start("Shell").attr("xmlns", WinRSConstants.WINRS_URI);
        xml.element("InputStreams", "stdin");
        xml.element("OutputStreams", "stdout stderr");
        xml.end();
        return xml.toString();
    }

    @Override
    protected String processOutput(Element output) {
        return XmlUtils.selectText(WinRSConstants.SHELL_ID_EXPR, output);
    }
}
