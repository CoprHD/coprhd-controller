/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation 
 * All Rights Reserved 
 *
 * This software contains the intellectual property of EMC Corporation 
 * or is licensed to EMC Corporation from third parties.  Use of this 
 * software and the intellectual property contained therein is expressly 
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.apidiff.model;

import com.emc.storageos.apidiff.Constants;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to parse XML File into data structure ServiceCatalog.
 * Prepare data for further comparison
 */
public class ServiceCatalogBuilder {

    private ServiceCatalogBuilder() {}

    public static ServiceCatalog build(final String filePath) {
        return build(new File(filePath));
    }

    /**
     * Parses xml file to ServiceCatalog with Jdom
     * @param xmlFile
     *          The instance of xml file
     * @return instance of ServiceCatalog
     */
    public static ServiceCatalog build(final File xmlFile) {

        String fileName = xmlFile.getName().trim().toLowerCase();
        // remove suffix
        if (fileName.endsWith(Constants.XML_FILE_SUFFIX))
            fileName = fileName.substring(0, fileName.length()-Constants.XML_FILE_SUFFIX.length()-1);
        else
            throw new IllegalArgumentException("API file is not xml format: "+ fileName);

        // filter name
        int separatorIndex = fileName.indexOf(Constants.NAME_STRING_SEPARATOR);
        if (separatorIndex == -1)
            throw new IllegalArgumentException("API file name should split with "
                    + Constants.NAME_STRING_SEPARATOR + " actually: " + fileName);
        String serviceName = fileName.substring(0, separatorIndex);
        String version = fileName.substring(separatorIndex+1, fileName.length());


        Document document;
        try {
            document = new SAXBuilder().build(xmlFile);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid XML file:\n " + xmlFile.getAbsolutePath(), ex);
        }

        if (document == null)
            return null;

        // Navigates to resource tag, it's a little tricky here, depends on structure of xml file completely.
        List<Element> resourceList = document.getRootElement()
                .getChild(Constants.REST_NODE)
                .getChild(Constants.RESOURCES_NODE)
                .getChildren();

        // Navigates to element tag, it's a little tricky here, depends on structure of xml file completely.
        List<Element> elementList = document.getRootElement()
                .getChild(Constants.DATA_NODE)
                .getChild(Constants.DATA_SCHEMA_NODE)
                .getChild(Constants.ELEMENTS_NODE)
                .getChildren();

        return new ServiceCatalog(parseResource(resourceList), parseElement(elementList), serviceName, version);
    }

    /**
     * Parses tag list <resource> to get REST API details
     * @param resourceList
     *          The list of resource tag
     * @return details of api resource
     */
    public static Map<ApiIdentifier, ApiDescriptor> parseResource(List<Element> resourceList) {
        if (resourceList == null)
            throw new NullPointerException("Please navigate to right resource tag before parse it");
        Map<ApiIdentifier, ApiDescriptor> resourceMap = new HashMap<ApiIdentifier, ApiDescriptor>();

        for (Element element : resourceList) {
            String path = element.getAttributeValue(Constants.ATTRIBUTE_NAME).trim();
            Element opElement = element.getChild(Constants.OPERATION_NODE);
            String method = opElement.getAttributeValue(Constants.ATTRIBUTE_NAME).trim();
            ApiIdentifier apiIdentifier = new ApiIdentifier(method, path);

            ApiDescriptor apiResource = new ApiDescriptor();

            Element opInValue = opElement.getChild(Constants.OPERATION_INVALUE_NODE);
            if (opInValue != null) {
                Element xmlElement = opInValue.getChild(Constants.XML_ELEMENT_NODE);
                if (xmlElement != null && xmlElement.getAttributeValue(Constants.ATTRIBUTE_ELEMENT_NAME) != null)
                    apiResource.setRequestElement(xmlElement.getAttributeValue(Constants.ATTRIBUTE_ELEMENT_NAME).trim());
            }

            Element opOutValue = opElement.getChild(Constants.OPERATION_OUTVALUE_NODE);
            if (opOutValue != null) {
                Element xmlElement = opOutValue.getChild(Constants.XML_ELEMENT_NODE);
                if (xmlElement != null && xmlElement.getAttributeValue(Constants.ATTRIBUTE_ELEMENT_NAME) != null)
                    apiResource.setResponseElement(xmlElement.getAttributeValue(Constants.ATTRIBUTE_ELEMENT_NAME).trim());
            }

            for (Element paramElement : opElement.getChildren(Constants.OPERATION_PARAMETER_NODE)){
                if(paramElement.getAttributeValue(Constants.ATTRIBUTE_NAME) != null)
                    apiResource.getParameters().add(paramElement.getAttributeValue(Constants.ATTRIBUTE_NAME).trim());
            }

            resourceMap.put(apiIdentifier, apiResource);
        }

        return resourceMap;
    }

    /**
     * Parses tag list <element> to get REST API details
     * @param elementList
     *          The list of element tag
     * @return details of API element
     */
    public static Map<String, String> parseElement(List<Element> elementList) {
        if (elementList == null)
            throw new NullPointerException("Please navigate to right element tag before parse it");
        Map<String, String> elmentMap = new HashMap<String, String>();
        for (Element element : elementList) {
            String exampleXml = filterExampleXml(element.getChildText(Constants.ELEMENT_EXAMPLE_XML_NODE).trim());
            elmentMap.put(element.getAttributeValue(Constants.ATTRIBUTE_NAME).trim(), exampleXml);
        }
        return elmentMap;
    }

    /**
     * Helper method to filter example xml
     * @param examplXml
     *          The content of example xml
     * @return the filtered example xml
     */
    private static String filterExampleXml(String examplXml) {
        int begin = 0;
        int end = examplXml.length();
        if (examplXml.startsWith(Constants.EXAMLE_XML_HEADER))
            begin = Constants.EXAMLE_XML_HEADER.length()-1;
        if (examplXml.endsWith(Constants.EXAMLE_XML_TAILER))
            end = end - Constants.EXAMLE_XML_TAILER.length();

        return examplXml.substring(begin, end);
    }

}
