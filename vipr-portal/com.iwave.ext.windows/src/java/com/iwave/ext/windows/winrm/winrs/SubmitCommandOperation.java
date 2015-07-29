/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.winrs;

import java.util.Arrays;

import org.w3c.dom.Element;

import com.iwave.ext.windows.winrm.WinRMInvokeOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlStringBuilder;
import com.iwave.ext.xml.XmlUtils;

/**
 * Submits a command to the remote shell. The result of this operation is the CommandID.
 * 
 * @author jonnymiller
 */
public class SubmitCommandOperation extends WinRMInvokeOperation<String> {
    private String command;
    private String[] arguments;

    public SubmitCommandOperation(WinRMTarget target, String shellId, String command,
            String[] arguments) {
        super(target, WinRSConstants.WINRS_CMD_URI, WinRSConstants.WINRS_COMMAND_URI);
        setSelector(WinRSConstants.SHELL_ID, shellId);
        this.command = command;
        if (arguments == null) {
            this.arguments = new String[0];
        } else {
            this.arguments = Arrays.copyOf(arguments, arguments.length);
        }
        setOption("WINRS_CONSOLE_MODE_STDIN", "TRUE");
    }

    @Override
    protected String createInput() {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.start("CommandLine").attr("xmlns", WinRSConstants.WINRS_URI);
        xml.element("Command", command);
        for (String arg : arguments) {
            xml.element("Arguments", arg);
        }
        xml.end();
        return xml.toString();
    }

    @Override
    protected String processOutput(Element output) {
        return XmlUtils.selectText(WinRSConstants.COMMAND_ID_EXPR, output);
    }
}
