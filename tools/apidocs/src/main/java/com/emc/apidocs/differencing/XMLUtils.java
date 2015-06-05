/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.apidocs.differencing;

import com.iwave.ext.xml.XmlException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class XMLUtils {
    private static XPathFactory xpathFactory;
    private static DocumentBuilderFactory documentBuilderFactory;

    public static Node getNode(Node node, XPathExpression xpath) {
        try {
            Object result = xpath.evaluate(node, XPathConstants.NODE);
            return (Node)result;
        }
        catch (XPathExpressionException e) {
            throw new RuntimeException("Invalid XPath",e);
        }
    }

    public static NodeList getNodeList(Node node, XPathExpression xpath) {
        try {
            Object result = xpath.evaluate(node, XPathConstants.NODESET);
            return (NodeList)result;
        }
        catch (XPathExpressionException e) {
            throw new RuntimeException("Invalid XPath",e);
        }
    }

    public static String getNodeText(Node node, XPathExpression xpath) {
        try {
            Object result = xpath.evaluate(node, XPathConstants.NODE);
            if (result == null) {
                return null;
            }

            return ((Node)result).getTextContent();
        }
        catch (XPathExpressionException e) {
            throw new RuntimeException("Invalid XPath",e);
        }
    }


    /**
     * Parses the XML string into a Document.
     *
     * @return the XML Document.
     */
    public static org.w3c.dom.Document loadDocument(InputStream xmlStream) {
        try {
            return getDocumentBuilderFactory().newDocumentBuilder().parse(xmlStream);
        }
        catch (UnsupportedEncodingException e) {
            throw new Error("UTF-8 must be supported");
        }
        catch (Exception e) {
            throw new XmlException(e);
        }
    }

    public static XPathExpression getXPath(String expression) {
        try {
            return getXPathFactory().newXPath().compile(expression);
        }
        catch (XPathExpressionException e) {
            throw new RuntimeException("Invalid XPath Expression "+expression, e);
        }
    }

    /**
     * Gets the XPath factory, creating it if necessary.
     *
     * @return the XPath factory.
     */
    public static synchronized XPathFactory getXPathFactory() {
        if (xpathFactory == null) {
            xpathFactory = XPathFactory.newInstance();
        }
        return xpathFactory;
    }

    /**
     * Gets the document builder factory, creating it if necessary.
     *
     * @return the document builder factory.
     */
    public static synchronized DocumentBuilderFactory getDocumentBuilderFactory() {
        if (documentBuilderFactory == null) {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
        }
        return documentBuilderFactory;
    }
}
