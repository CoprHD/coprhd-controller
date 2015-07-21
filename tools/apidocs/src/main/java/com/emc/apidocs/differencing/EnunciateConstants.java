/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.differencing;


import javax.xml.xpath.XPathExpression;

public class EnunciateConstants {

    public static XPathExpression TYPE = XMLUtils.getXPath("//schema/types/type");
    public static XPathExpression TYPE_NAME = XMLUtils.getXPath("@name");
    public static XPathExpression TYPE_PACKAGE = XMLUtils.getXPath("@package");
    public static XPathExpression TYPE_ELEMENT = XMLUtils.getXPath("elements/element");

    public static XPathExpression TYPE_ATTRIBUTE = XMLUtils.getXPath("attributes/attribute");
    public static XPathExpression ATTRIBUTE_NAME = XMLUtils.getXPath("@name");
    public static XPathExpression ATTRIBUTE_TYPE = XMLUtils.getXPath("@typeName");

    public static XPathExpression ELEMENT_NAME = XMLUtils.getXPath("choice/@name");
    public static XPathExpression ELEMENT_TYPENAME = XMLUtils.getXPath("@typeName");
    public static XPathExpression ELEMENT_CHOICE_TYPENAME = XMLUtils.getXPath("choice/@typeName");
    public static XPathExpression ELEMENT_JSON_NAME = XMLUtils.getXPath("@jsonName");
    public static XPathExpression ELEMENT_MAX_OCCURS = XMLUtils.getXPath("@maxOccurs");

    public static XPathExpression RESOURCES = XMLUtils.getXPath("//rest/resources/resource");
    public static XPathExpression RESOURCE_SERVICE_CLASS = XMLUtils.getXPath("groups/group");
    public static XPathExpression RESOURCE_HTTP_OPERATION = XMLUtils.getXPath("operation/@name");
    public static XPathExpression RESOURCE_PATH = XMLUtils.getXPath("@name");
    public static XPathExpression RESOURCE_REQUEST_ELEMENT_TYPE = XMLUtils.getXPath("operation/inValue/xmlElement/@elementName");
    public static XPathExpression RESOURCE_RESPONSE_ELEMENT_TYPE = XMLUtils.getXPath("operation/outValue/xmlElement/@elementName");


    public static XPathExpression GROUP = XMLUtils.getXPath("groups/group");
}
