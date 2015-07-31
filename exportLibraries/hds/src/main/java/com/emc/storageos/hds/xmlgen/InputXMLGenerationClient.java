/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.xmlgen;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.milyn.payload.JavaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.util.SmooksUtil;
import com.emc.storageos.hds.xmlgen.beans.Operation;
import com.emc.storageos.hds.xmlgen.beans.XmlElementSequenceAttribute;

public class InputXMLGenerationClient {

    private static final Logger log = LoggerFactory
            .getLogger(InputXMLGenerationClient.class);

    public static String getInputXMLString(String requestedOperationName,
            Map<String, Object> attributeMap, String xmlInputContextFile,
            String smooksConfigFile) {

        String operationInputXMLString = null;
        InputStream stream = null;
        try {
            List<Operation> availableOperations = new ArrayList<Operation>();
            log.debug("XML Context File name to be loaded: {}", xmlInputContextFile);
            stream = InputXMLGenerationClient.class.getClass().getResourceAsStream(
                    xmlInputContextFile);

            JavaResult javaResult = SmooksUtil.getParsedXMLJavaResult(stream,
                    smooksConfigFile);

            @SuppressWarnings("unchecked")
            List<Operation> operationList = (List<Operation>) javaResult
                    .getBean(XMLConstants.OPERATION_LIST_BEAN_NAME);
            if (null != operationList && !operationList.isEmpty()) {
                log.debug("{} operations found in configuration file",
                        operationList.size());

                for (Operation operation : operationList) {
                    if (operation.getName().equalsIgnoreCase(requestedOperationName)) {
                        log.debug("Found matching operation {}", operation.getName());
                        availableOperations.add(operation);
                    }
                }

                Operation operation = getModelSupportedOperation(attributeMap,
                        availableOperations);
                if (null != operation) {
                    operationInputXMLString = generateXMLString(operation, attributeMap);
                } else {
                    log.error("No Operation found with the given model");
                    HDSException.exceptions
                            .unableToGenerateInputXmlDueToUnSupportedModelFound();
                }
            } else {
                log.error("No operation list found to generate input xml.");
                HDSException.exceptions.unableToGenerateInputXmlDueToNoOperations();
            }
        } catch (Exception ex) {
            HDSException.exceptions.unableToGenerateInputXmlForGivenRequest(ex
                    .getLocalizedMessage());
        }
        return operationInputXMLString;
    }

    /**
     * This method will make sure that if a new model is used to test then the
     * operation with model "default" will be used to execute commands.
     * 
     * @param attributeMap
     * @param availableOperations
     * @return
     */
    private static Operation getModelSupportedOperation(Map<String, Object> attributeMap,
            List<Operation> availableOperations) {
        String requestedSystemModel = getModelFromMap(attributeMap);
        Operation operationToUse = null;
        if (null != availableOperations && !availableOperations.isEmpty()) {
            for (Operation operation : availableOperations) {
                List<String> supportedModelsFromOperation = Arrays.asList(operation
                        .getModels().split(XMLConstants.COMMA_OPERATOR));
                if (supportedModelsFromOperation.contains(requestedSystemModel)) {
                    operationToUse = operation;
                    break;
                } else if (supportedModelsFromOperation
                        .contains(XMLConstants.DEFAULT_MODEL)) {
                    operationToUse = operation;
                }
            }
        }
        return operationToUse;
    }

    /**
     * Return the model if it is present in the attributeMap else return any.
     * 
     * @param attributeMap
     * @return
     */
    private static String getModelFromMap(Map<String, Object> attributeMap) {
        if (!attributeMap.containsKey(XMLConstants.MODEL)) {
            return XMLConstants.DEFAULT_MODEL;
        }
        return attributeMap.get(XMLConstants.MODEL).toString();
    }

    /**
     * Generates the XML String for the given operation & attributemap.
     * 
     * @param operation
     * @param attributeMap
     * @return
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws SecurityException
     */
    private static String generateXMLString(Operation operation,
            Map<String, Object> attributeMap) throws IllegalAccessException,
            IllegalArgumentException, InvocationTargetException, ClassNotFoundException,
            NoSuchMethodException, SecurityException {
        StringBuilder xmlNodeWithAttributes = new StringBuilder(XMLConstants.XMl_START_ELEMENT);
        List<String> sequenceList = Arrays.asList(operation.getXmlElementSequence().split(
                XMLConstants.COMMA_OPERATOR));
        Class noparams[] = {};
        LinkedList<String> xmlNodesToClose = new LinkedList<String>();
        List<String> preClosedXMLNodes = new ArrayList<String>();
        Map<String, XmlElementSequenceAttribute> elementAttributes = getElementProperites(operation
                .getXmlElementSequenceAttributeList());
        Iterator<String> sequenceItr = sequenceList.iterator();
        while (sequenceItr.hasNext()) {
            String elementName = sequenceItr.next().toString();
            if (elementAttributes.containsKey(elementName)) {
                XmlElementSequenceAttribute sequenceAttrs = elementAttributes
                        .get(elementName);
                if (null != sequenceAttrs.getModel()) {
                    String type = sequenceAttrs.getType();
                    String model = sequenceAttrs.getModel();
                    String elementKey = (null == type) ? elementName : elementName
                            + XMLConstants.UNDERSCORE_OP + type;
                    if (attributeMap.containsKey(elementKey)) {
                        log.debug("Found key {} in map", elementKey);
                        Object modelValue = attributeMap.get(elementKey);
                        if (modelValue instanceof List) {
                            List<Object> elementList = (List<Object>) modelValue;
                            if (null != elementList && !elementList.isEmpty()) {
                                for (Object object : elementList) {
                                    log.debug("list instance : {} object: {}", elementName,
                                            object);
                                    xmlNodeWithAttributes.append(XMLConstants.LESS_THAN_OP)
                                            .append(elementName);
                                    Class cls = Class.forName(model);
                                    Method method = cls.getDeclaredMethod(
                                            XMLConstants.METHOD_TO_INVOKE, noparams);
                                    Object output = method.invoke(object, null);

                                    xmlNodeWithAttributes.append(XMLConstants.SPACE)
                                            .append(output.toString())
                                            .append(XMLConstants.GREATER_THAN_OP);
                                    log.debug("getChildExists: {}", sequenceAttrs.getChildExists());
                                    if (null != sequenceAttrs.getChildExists() && sequenceAttrs.getChildExists()) {
                                        String childNodeStr = processIfChildNodesExists(
                                                cls, object, noparams, elementName);
                                        if (null != childNodeStr && !childNodeStr.isEmpty()) {
                                            xmlNodeWithAttributes.append(childNodeStr);
                                        }
                                    }
                                    xmlNodeWithAttributes.append(XMLConstants.XML_CLOSING_START_TAG)
                                            .append(elementName).append(XMLConstants.GREATER_THAN_OP);

                                }
                            }
                        } else {
                            log.debug("no list: {}", elementName);
                            xmlNodeWithAttributes.append(XMLConstants.LESS_THAN_OP)
                                    .append(elementName);
                            Class cls = Class.forName(model);
                            Method method = cls.getDeclaredMethod(
                                    XMLConstants.METHOD_TO_INVOKE, noparams);
                            Object output = method.invoke(modelValue, null);
                            log.debug("object : {}", output);
                            xmlNodeWithAttributes.append(XMLConstants.SPACE)
                                    .append(output.toString())
                                    .append(XMLConstants.GREATER_THAN_OP);
                            xmlNodesToClose.addFirst(elementName);
                        }
                    }

                }
                if (null != sequenceAttrs.getProperties()
                        && !sequenceAttrs.getProperties().isEmpty()) {
                    log.debug("Found properties for element name: {}", elementName);
                    xmlNodeWithAttributes.append(XMLConstants.LESS_THAN_OP).append(
                            elementName);
                    String attributeNames[] = sequenceAttrs.getProperties().split(
                            XMLConstants.COMMA_OPERATOR);
                    int count = 0;
                    for (String attributeName : attributeNames) {
                        String attributeValue = null;
                        if (null != sequenceAttrs.getValues()) {
                            String defaultValuesFromXML[] = sequenceAttrs.getValues()
                                    .split(XMLConstants.COMMA_OPERATOR);
                            if (null != defaultValuesFromXML[count]) {
                                attributeValue = defaultValuesFromXML[count];
                                count++;
                            }
                        }
                        if (null == attributeValue) {
                            attributeValue = attributeMap.get(
                                    elementName + XMLConstants.UNDERSCORE_OP
                                            + attributeName).toString();
                        }
                        if (null != attributeValue && attributeValue.length() > 0) {
                            xmlNodeWithAttributes.append(" ").append(
                                    attributeName + XMLConstants.EQUAL_OP + "\""
                                            + attributeValue + "\" ");
                        }
                    }
                }
                if (operation.getXmlElementsToClose().contains(elementName)) {
                    preClosedXMLNodes.add(elementName);
                    xmlNodeWithAttributes.append(XMLConstants.XML_CLOSING_TAG);
                }

            } else {
                if (operation.getXmlElementsToClose().contains(elementName)) {
                    log.debug("pre closed element: {}", elementName);
                    xmlNodeWithAttributes.append(XMLConstants.LESS_THAN_OP)
                            .append(elementName).append(XMLConstants.XML_CLOSING_TAG);
                    preClosedXMLNodes.add(elementName);
                } else {
                    log.debug("No sequence element found. hence adding: {}", elementName);
                    xmlNodeWithAttributes.append(XMLConstants.LESS_THAN_OP)
                            .append(elementName).append(XMLConstants.GREATER_THAN_OP);
                    xmlNodesToClose.addFirst(elementName);
                }

            }
        }
        if (null != xmlNodesToClose && !xmlNodesToClose.isEmpty()) {
            for (String xmlNodeToClose : xmlNodesToClose) {
                if (!preClosedXMLNodes.contains(xmlNodeToClose)) {
                    log.debug("Closing {} element tag", xmlNodeToClose);
                    xmlNodeWithAttributes.append("</").append(xmlNodeToClose)
                            .append(XMLConstants.GREATER_THAN_OP);
                }
            }
        }
        return xmlNodeWithAttributes.toString();
    }

    /**
     * Process the child nodes if they are present in the object which is passed in.
     * 
     * @param cls : class to invoke
     * @param object : object in which method exists.
     * @param noparams : method params.
     * @return
     */
    private static String processIfChildNodesExists(Class cls, Object object,
            Class[] noparams, String elementName) throws NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {
        String childNodeOutput = null;
        Method method = cls.getDeclaredMethod(
                XMLConstants.CHILD_NODE_METHOD, noparams);
        Object output = method.invoke(object, null);
        if (null != output && !output.toString().isEmpty()) {
            log.debug("Processing child nodes of node {}", elementName);
            childNodeOutput = output.toString();
        }

        return childNodeOutput;
    }

    private static Map<String, XmlElementSequenceAttribute> getElementProperites(
            List<XmlElementSequenceAttribute> xmlElementSequenceAttributeList) {
        Map<String, XmlElementSequenceAttribute> elementAttributes = new HashMap<String, XmlElementSequenceAttribute>();
        if (null != xmlElementSequenceAttributeList
                && !xmlElementSequenceAttributeList.isEmpty()) {
            for (XmlElementSequenceAttribute xmlElementAttribute : xmlElementSequenceAttributeList) {
                elementAttributes.put(xmlElementAttribute.getName(), xmlElementAttribute);
            }
        }

        return elementAttributes;
    }

}
