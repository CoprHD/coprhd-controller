/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.winrs;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;

import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.xml.XmlUtils;

public class WinRSConstants {
    public static final String WINRS_URI = "http://schemas.microsoft.com/wbem/wsman/1/windows/shell";
    public static final String WINRS_CMD_URI = WINRS_URI + "/cmd";
    public static final String WINRS_CREATE_URI = WinRMConstants.CREATE_URI;
    public static final String WINRS_DELETE_URI = WinRMConstants.DELETE_URI;
    public static final String WINRS_COMMAND_URI = WINRS_URI + "/Command";
    public static final String WINRS_RECEIVE_URI = WINRS_URI + "/Receive";
    public static final String WINRS_COMMAND_STATE_DONE_URI = WINRS_URI + "/CommandState/Done";
    public static final String SHELL_ID = "ShellId";

    public static final XPath XPATH = XmlUtils.createXPath(
            String.format("w=%s", WinRMConstants.WSMAN_URI), String.format("rsp=%s", WINRS_URI));
    public static final XPathExpression COMMAND_ID_EXPR = XmlUtils.compileXPath(XPATH,
            "//rsp:CommandResponse/rsp:CommandId");
    public static final XPathExpression STDOUT = XmlUtils.compileXPath(XPATH,
            "//rsp:ReceiveResponse/rsp:Stream[@Name='stdout']");
    public static final XPathExpression STDERR = XmlUtils.compileXPath(XPATH,
            "//rsp:ReceiveResponse/rsp:Stream[@Name='stderr']");
    public static final XPathExpression COMMAND_STATE = XmlUtils.compileXPath(XPATH,
            "//rsp:ReceiveResponse/rsp:CommandState/@State");
    public static final XPathExpression EXIT_CODE = XmlUtils.compileXPath(XPATH,
            "//rsp:ReceiveResponse/rsp:CommandState/rsp:ExitCode");
    public static final XPathExpression SHELL_ID_EXPR = XmlUtils.compileXPath(XPATH,
            "//w:SelectorSet/w:Selector[@Name='" + SHELL_ID + "']");

}
