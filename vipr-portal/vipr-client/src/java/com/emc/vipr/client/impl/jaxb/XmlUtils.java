/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl.jaxb;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;

import com.emc.vipr.client.exceptions.ViPRException;

public class XmlUtils {
    private static SAXParserFactory SAX_FACTORY;
    private static DocumentBuilderFactory DOM_FACTORY;
    private static TransformerFactory TRANSFORMER_FACTORY;

    public static synchronized SAXParserFactory getSAXFactory() {
        if (SAX_FACTORY == null) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            SAX_FACTORY = factory;
        }
        return SAX_FACTORY;
    }

    public static synchronized DocumentBuilderFactory getDOMFactory() {
        if (DOM_FACTORY == null) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            DOM_FACTORY = factory;
        }
        return DOM_FACTORY;
    }

    public static synchronized TransformerFactory getTransformerFactory() {
        if (TRANSFORMER_FACTORY == null) {
            TRANSFORMER_FACTORY = TransformerFactory.newInstance();
        }
        return TRANSFORMER_FACTORY;
    }

    public static SAXParser createSAXParser() {
        try {
            return getSAXFactory().newSAXParser();
        } catch (Exception e) {
            throw new ViPRException(e);
        }
    }

    public static DocumentBuilder createDocumentBuilder() {
        try {
            return getDOMFactory().newDocumentBuilder();
        } catch (Exception e) {
            throw new ViPRException(e);
        }
    }

    public static Transformer createTransformer() {
        try {
            return getTransformerFactory().newTransformer();
        } catch (Exception e) {
            throw new ViPRException(e);
        }
    }

    public static Unmarshaller createUnmarshaller(Class<?> itemClass) {
        try {
            return JAXBContext.newInstance(itemClass).createUnmarshaller();
        } catch (Exception e) {
            throw new ViPRException(e);
        }
    }
}
