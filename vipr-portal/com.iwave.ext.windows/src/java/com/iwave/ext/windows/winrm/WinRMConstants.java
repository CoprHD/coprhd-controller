/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

import javax.xml.soap.SOAPConstants;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;

import com.iwave.ext.xml.XmlUtils;

public class WinRMConstants {
    public static final String WSMAN_URI = "http://schemas.dmtf.org/wbem/wsman/1/wsman.xsd";
    public static final String MS_WSMAN_BASE_URI = "http://schemas.microsoft.com/wbem/wsman/1/";
    public static final String WMI_BASE_URI = MS_WSMAN_BASE_URI + "wmi/";

    public static final String ADDRESSING_URI = "http://schemas.xmlsoap.org/ws/2004/08/addressing";
    public static final String TRANSFER_URI = "http://schemas.xmlsoap.org/ws/2004/09/transfer";
    public static final String ENUMERATION_URI = "http://schemas.xmlsoap.org/ws/2004/09/enumeration";
    public static final String SCHEMA_INSTANCE_URI = "http://www.w3.org/2001/XMLSchema-instance";
    public static final String WSMAN_FAULT_URI = "http://schemas.microsoft.com/wbem/wsman/1/wsmanfault";
    public static final String GET_URI = TRANSFER_URI + "/Get";
    public static final String CREATE_URI = TRANSFER_URI + "/Create";
    public static final String DELETE_URI = TRANSFER_URI + "/Delete";
    public static final String ENUMERATE_URI = ENUMERATION_URI + "/Enumerate";
    public static final String PULL_URI = ENUMERATION_URI + "/Pull";

    public static final XPath XPATH = XmlUtils.createXPath(
            String.format("f=%s",WSMAN_FAULT_URI),
            String.format("s=%s", SOAPConstants.URI_NS_SOAP_1_2_ENVELOPE),
            String.format("w=%s", WSMAN_URI), String.format("a=%s", ADDRESSING_URI),
            String.format("x=%s", TRANSFER_URI), String.format("xsi=%s", SCHEMA_INSTANCE_URI),
            String.format("e=%s", ENUMERATION_URI));
    public static final XPathExpression SOAP_HEADER_EXPR = XmlUtils.compileXPath(XPATH,
            "/s:Envelope/s:Body");
    public static final XPathExpression SOAP_BODY_EXPR = XmlUtils.compileXPath(XPATH,
            "/s:Envelope/s:Body");
    public static final XPathExpression ENUMERATION_CONTEXT_EXPR = XmlUtils.compileXPath(XPATH,
            "e:EnumerateResponse/e:EnumerationContext | e:PullResponse/e:EnumerationContext");
    public static final XPathExpression END_OF_SEQUENCE_EXPR = XmlUtils.compileXPath(XPATH,
            "e:PullResponse/e:EndOfSequence");
    public static final XPathExpression ITEMS_EXPR = XmlUtils.compileXPath(XPATH,
            "e:PullResponse/e:Items");

}
