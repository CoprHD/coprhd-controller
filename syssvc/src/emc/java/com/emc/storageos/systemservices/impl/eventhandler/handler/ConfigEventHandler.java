/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl.eventhandler.handler;

import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.emc.storageos.systemservices.impl.eventhandler.util.SystemConfigConstants;
import com.emc.storageos.systemservices.impl.eventhandler.util.SystemConfigManager;

public class ConfigEventHandler implements IEventHandler {

    private Document doc;

    public String handleEvent() {

        String xmlResult = null;

        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            this.doc = docBuilder.newDocument();

        } catch (ParserConfigurationException e) {

            e.printStackTrace();
        }

        createConfigXML();

        xmlResult = serializeXMLDoc();

        return xmlResult;

    }

    private void createConfigXML() {

        Element root = doc.createElement(SystemConfigConstants.SOFTWARE_VERSION);
        doc.appendChild(root);

        /**
         * Get the information from the utility class SystemConfigManager and
         * populate the XML elements
         */

        // Get Software Version
        Element version = doc.createElement(SystemConfigConstants.SOFTWARE_VERSION);
        version.appendChild(doc.createTextNode(SystemConfigManager
                .getSoftwareVersion()));
        root.appendChild(version);

        // Get Node List
        Element nodelist = doc.createElement(SystemConfigConstants.NODE_LIST);
        List<String> nlist = SystemConfigManager.getNodeList();
        Attr numNodes = doc.createAttribute(SystemConfigConstants.NUM_NODE_ATTR);
        numNodes.setValue(String.valueOf(nlist.size()));
        nodelist.setAttributeNode(numNodes);

        Iterator<String> nodeIter = nlist.iterator();
        while (nodeIter.hasNext()) {
            Element node = doc.createElement(SystemConfigConstants.NODE);
            node.appendChild(doc.createTextNode(nodeIter.next()));
            nodelist.appendChild(node);
        }
        root.appendChild(nodelist);

        // Get List of Hotfix applied
        Element hflist = doc.createElement(SystemConfigConstants.HOTFIX_LIST);
        Iterator<String> hfIter = SystemConfigManager.getHotfixLIst().iterator();
        while (hfIter.hasNext()) {
            Element hf = doc.createElement(SystemConfigConstants.HOTFIX);
            hf.appendChild(doc.createTextNode(hfIter.next()));
            hflist.appendChild(hf);
        }

        root.appendChild(hflist);
    }

    private String serializeXMLDoc() {
        String xmlConfigStr = null;
        try {
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);

            TransformerFactory transformerFactory = TransformerFactory
                    .newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(this.doc);
            transformer.transform(source, result);

            StringWriter strWriter = (StringWriter) result.getWriter();
            xmlConfigStr = strWriter.getBuffer().toString();

        } catch (TransformerException e) {
            e.printStackTrace();
        }
        return xmlConfigStr;
    }

}
