/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.differencing;


import com.emc.apidocs.model.ApiClass;
import com.emc.apidocs.model.ApiField;
import com.emc.apidocs.model.ApiMethod;
import com.emc.apidocs.model.ApiService;
import com.google.common.collect.Maps;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.util.Map;


public class EnunciateFileReader {
    private static XPathFactory xpathFactory;
    private static DocumentBuilderFactory documentBuilderFactory;

    private Map<String, ApiClass> resolvedClasses;
    private Map<String, ApiClass> resolvedElementTypes;

    public synchronized Map<String, ApiService> loadServices(InputStream inputStream) {
        resolvedClasses = Maps.newHashMap();
        resolvedElementTypes = Maps.newHashMap();

        Document document = XMLUtils.loadDocument(inputStream);
        return loadServices(document);
    }

    private Map<String, ApiService> loadServices(Document document) {
        Map<String, ApiClass> apiClasses = loadTypes(document);

        Map<String, ApiService> apiServices = Maps.newHashMap();
        NodeList resourceNodes = XMLUtils.getNodeList(document, EnunciateConstants.RESOURCES);
        for (int f=0;f<resourceNodes.getLength();f++) {
            Node resourceNode  = resourceNodes.item(f);

            String serviceClass = XMLUtils.getNodeText(resourceNode, EnunciateConstants.RESOURCE_SERVICE_CLASS);
            ApiService apiService = null;
            if (apiClasses.containsKey(serviceClass)) {
                apiService = apiServices.get(serviceClass);
            }
            else {
                apiService = new ApiService();
                apiService.javaClassName = serviceClass;
                apiServices.put(serviceClass, apiService);
            }

            ApiMethod apiMethod = toApiMethod(resourceNode, apiClasses);
            System.out.println("Loaded Method "+serviceClass+" "+apiMethod.httpMethod+" "+apiMethod.path);
            apiService.addMethod(apiMethod);
        }

        return apiServices;
    }

    private Map<String, ApiClass> loadTypes(Document document) {
        Map<String, ApiClass> types = Maps.newHashMap();

        NodeList typeNodes = XMLUtils.getNodeList(document, EnunciateConstants.TYPE);
        for (int i=0;i<typeNodes.getLength();i++) {
            Node typeNode = typeNodes.item(i);

            ApiClass apiClass = toApiClass(typeNode);
            types.put(apiClass.name, apiClass);
        }

        return types;
    }

    public ApiMethod toApiMethod(Node resourceNode, Map<String, ApiClass> types) {
        ApiMethod apiMethod = new ApiMethod();
        apiMethod.httpMethod = XMLUtils.getNodeText(resourceNode, EnunciateConstants.RESOURCE_HTTP_OPERATION);
        apiMethod.path = XMLUtils.getNodeText(resourceNode, EnunciateConstants.RESOURCE_PATH);

        String requestElementName = XMLUtils.getNodeText(resourceNode, EnunciateConstants.RESOURCE_REQUEST_ELEMENT_TYPE);
        if (requestElementName != null) {
            apiMethod.input = findElementType(requestElementName, types, resourceNode.getOwnerDocument());
        }

        String responseElementName = XMLUtils.getNodeText(resourceNode, EnunciateConstants.RESOURCE_RESPONSE_ELEMENT_TYPE);
        if (responseElementName != null) {
            apiMethod.output = findElementType(responseElementName, types, resourceNode.getOwnerDocument());
        }

        return apiMethod;
    }

    private ApiClass findElementType(String elementName, Map<String, ApiClass> types,  Document document) {
        if (resolvedElementTypes.containsKey(elementName)) {
            return resolvedElementTypes.get(elementName);
        }

        Node node = XMLUtils.getNode(document, XMLUtils.getXPath("//schema/elements/element[@name=\""+elementName+"\"]"));
        if (node == null) {
            throw new RuntimeException("Unable to find element "+elementName);
        }

        String elementType = XMLUtils.getNodeText(node, EnunciateConstants.ELEMENT_TYPENAME);
        if (!types.containsKey(elementType)) {
             throw new RuntimeException("Unable to find type "+elementType+" for element "+elementName);
        }

        resolvedElementTypes.put(elementName, types.get(elementType));

        return types.get(elementType);
    }

    private ApiClass loadType(String name, Document document) {
        if (resolvedClasses.containsKey(name)) {
            return resolvedClasses.get(name);
        }

        Node typeNode = XMLUtils.getNode(document, XMLUtils.getXPath("//schema/types/type[@name=\""+name+"\"]"));

        if (typeNode == null) {
            throw new RuntimeException("Type "+name+" not found in document");
        }

        ApiClass apiClass = toApiClass(typeNode);
        resolvedClasses.put(name, apiClass);
        System.out.println("Loaded Class "+name);

        return apiClass;
    }

    private ApiClass toApiClass(Node typeNode) {
        ApiClass apiClass = new ApiClass();
        apiClass.name = XMLUtils.getNodeText(typeNode, EnunciateConstants.TYPE_NAME);

        NodeList elementList = XMLUtils.getNodeList(typeNode, EnunciateConstants.TYPE_ELEMENT);
        for (int f=0;f<elementList.getLength();f++) {
            Node element = elementList.item(f);
            apiClass.addField(toApiField(element));
        }

        NodeList attributeList = XMLUtils.getNodeList(typeNode, EnunciateConstants.TYPE_ATTRIBUTE);
        for (int f=0;f<attributeList.getLength();f++) {
            Node attribute = attributeList.item(f);
            String attributeType = XMLUtils.getNodeText(attribute, EnunciateConstants.ATTRIBUTE_TYPE);
            String attributeName = XMLUtils.getNodeText(attribute, EnunciateConstants.ATTRIBUTE_NAME);

            ApiField attributeField = new ApiField();
            attributeField.name = attributeName;
            attributeField.primitiveType = attributeType;

            apiClass.addAttribute(attributeField);
        }

        return apiClass;
    }

    private ApiField toApiField(Node elementNode) {
        ApiField apiField = new ApiField();
        apiField.name = XMLUtils.getNodeText(elementNode, EnunciateConstants.ELEMENT_NAME);

        String typeName = XMLUtils.getNodeText(elementNode, EnunciateConstants.ELEMENT_CHOICE_TYPENAME);
        if (typeName == null) {
            typeName = XMLUtils.getNodeText(elementNode, EnunciateConstants.ELEMENT_TYPENAME);
        }

        if (typeName != null) {
            boolean isPrimitive = typeName.equals("string") ||
                    typeName.equals("dateTime") ||
                    typeName.equals("long") ||
                    typeName.equals("boolean") ||
                    typeName.equals("int") ||
                    typeName.equals("double");

            if (isPrimitive) {
                apiField.primitiveType = typeName;
            }
            else {
                apiField.type = loadType(typeName, elementNode.getOwnerDocument());
            }
        }
        else {
            System.out.println("Warning: Unable to find typeName for element "+apiField.name);
        }

        return apiField;
    }
}
