/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.xml;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XmlUtils {
    /** Reusable document builder factory. */
    private static DocumentBuilderFactory documentBuilderFactory;
    /** Reusable transformer factory. */
    private static TransformerFactory transformerFactory;
    /** Reusable XPath factory. */
    private static XPathFactory xpathFactory;
    /** Reusable XPath. */
    private static XPath xpath;

    /**
     * Gets the document builder factory, creating it if necessary.
     * 
     * @return the document builder factory.
     */
    public static synchronized DocumentBuilderFactory getDocumentBuilderFactory() {
        if (documentBuilderFactory == null) {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilderFactory.setExpandEntityReferences(false);
        }
        return documentBuilderFactory;
    }

    /**
     * Gets the transformer factory, creating it if necessary.
     * 
     * @return the transformer factory.
     */
    public static synchronized TransformerFactory getTransformerFactory() {
        if (transformerFactory == null) {
            transformerFactory = TransformerFactory.newInstance();
        }
        return transformerFactory;
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
     * Gets the XPath API, creating it if necessary.
     * 
     * @return the XPath API.
     */
    public static synchronized XPath getXPath() {
        if (xpath == null) {
            xpath = getXPathFactory().newXPath();
        }
        return xpath;
    }

    /**
     * Creates an XPath instance with the given namespace mapping.
     * 
     * @param namespaces the prefix->namespaceURI mapping.
     * @return the XPath instance.
     */
    public static XPath createXPath(Map<String, String> namespaces) {
        XPath xpath = getXPathFactory().newXPath();
        xpath.setNamespaceContext(new NamespaceContextMap(namespaces));
        return xpath;
    }

    /**
     * Creates an XPath instance with the given namespace mapping. The namespace strings take the
     * form <code><i>prefix</i>=<i>namespaceURI</i></code>.
     * 
     * @param namespaces the namespaces.
     * @return the XPath instance.
     */
    public static XPath createXPath(String... namespaces) {
        Map<String, String> namespaceMap = new HashMap<String, String>();
        for (String namespace : namespaces) {
            String prefix = StringUtils.substringBefore(namespace, "=");
            String namespaceURI = StringUtils.substringAfter(namespace, "=");
            namespaceMap.put(prefix, namespaceURI);
        }
        return createXPath(namespaceMap);
    }

    /**
     * Parses the XML string into a Document.
     * 
     * @param xml the XML string.
     * @return the XML Document.
     */
    public static Document parseXml(String xml) {
        try {
            ByteArrayInputStream xmlStream = new ByteArrayInputStream(xml.getBytes("UTF-8"));
            return getDocumentBuilderFactory().newDocumentBuilder().parse(xmlStream);
        }
        catch (UnsupportedEncodingException e) {
            throw new Error("UTF-8 must be supported");
        }
        catch (Exception e) {
            throw new XmlException(e);
        }
    }

    /**
     * Formats the XML as a string.
     * 
     * @param node the XML node (typically a Document or Element).
     * @return the formatted XML string.
     */
    public static String formatXml(Node node) {
        return formatXml(node, true);
    }

    /**
     * Formats the XML as a string.
     * 
     * @param node the XML node (typically a Document or Element).
     * @param pretty whether to make the XML pretty.
     * @return the formatted XML string.
     */
    public static String formatXml(Node node, boolean pretty) {
        try {
            StringWriter writer = new StringWriter();
            Source source = new DOMSource(node);
            Result result = new StreamResult(writer);
            Transformer t = getTransformerFactory().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            if (pretty) {
                t.setOutputProperty(OutputKeys.INDENT, "yes");
                t.setOutputProperty("{http://xml.apache.org/xalan}indent-amount", "2");
            }
            t.transform(source, result);
            return writer.toString();
        }
        catch (Exception e) {
            throw new XmlException(e);
        }
    }

    /**
     * Escapes any XML entities in the text.
     * 
     * @param text the text.
     * @return the escaped text.
     */
    public static String escapeText(String text) {
        StrBuilder sb = new StrBuilder();
        escape(sb, text, false, false);
        return sb.toString();
    }

    /**
     * Escapes any XML entities in the text for use in an attribute.
     * 
     * @param text the text.
     * @return the escaped text.
     */
    public static String escapeAttr(String text) {
        StrBuilder sb = new StrBuilder();
        escape(sb, text, true, true);
        return sb.toString();
    }

    /**
     * Escapes any XML entities in the text, appending the result to the StrBuilder.
     * 
     * @param toAppend the StrBuilder to append the escaped text to.
     * @param text the text to escape.
     * @param escapeQuote whether quote characters will be escaped.
     * @param escapeApos whether apostrophe characters will be escaped.
     */
    public static void escape(StrBuilder toAppend, String text, boolean escapeQuote,
            boolean escapeApos) {
        if (text == null) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
            case '<':
                toAppend.append("&lt;");
                break;
            case '>':
                toAppend.append("&gt;");
                break;
            case '&':
                toAppend.append("&amp;");
                break;
            case '"':
                toAppend.append(escapeQuote ? "&quot;" : ch);
                break;
            case '\'':
                toAppend.append(escapeApos ? "&apos;" : ch);
                break;
            default:
                toAppend.append(ch);
                break;
            }
        }
    }

    /**
     * Creates a stylesheet template from the given source.
     * 
     * @param source the stylesheet source.
     * @return the compiled template of the stylesheet.
     */
    public static Templates createTemplates(Source source) {
        try {
            return getTransformerFactory().newTemplates(source);
        }
        catch (TransformerConfigurationException e) {
            throw new XmlException(e);
        }
    }

    /**
     * Creates an identity transformer.
     * 
     * @return the identity transformer.
     */
    public static Transformer createTransformer() {
        try {
            return getTransformerFactory().newTransformer();
        }
        catch (TransformerConfigurationException e) {
            throw new XmlException(e);
        }
    }

    /**
     * Creates a transformer from the given stylesheet source.
     * 
     * @param source the stylesheet source.
     * @return the transformer.
     */
    public static Transformer createTransformer(Source source) {
        try {
            return getTransformerFactory().newTransformer(source);
        }
        catch (TransformerConfigurationException e) {
            throw new XmlException(e);
        }
    }

    /**
     * Compiles an XPath expresssion.
     * 
     * @param xpath the XPath api.
     * @param expr the expression.
     * @return the compiled expression.
     */
    public static XPathExpression compileXPath(XPath xpath, String expr) {
        try {
            return xpath.compile(expr);
        }
        catch (XPathExpressionException e) {
            throw new XmlException(e);
        }
    }

    /**
     * Compiles an XPath expression using the default XPath.
     * 
     * @param expr the expression.
     * @return the compiled expression.
     */
    public static XPathExpression compileXPath(String expr) {
        return compileXPath(getXPath(), expr);
    }

    /**
     * Compiles an XPath expression, which is aware of the specified namespaces. This is a
     * convenience method for creating single XPath expressions. If multiple expressions are to be
     * compiled for the same set of namespaces, create an XPath using
     * {@link #createXPath(String...)} and compile each expression using
     * {@link #compileXPath(XPath, String)}.
     * 
     * @param expr the XPath expression.
     * @param namespaces the namespaces (<code><i>prefix</i>=<i>namespaceURI</i></code> format).
     * @return the compiled expression.
     * 
     * @see #createXPath(String...)
     * @see #compileXPath(XPath, String)
     */
    public static XPathExpression compileXPath(String expr, String... namespaces) {
        return compileXPath(createXPath(namespaces), expr);
    }

    /**
     * Evaluates the XPath expression as text.
     * 
     * @param expr the expression.
     * @param context the context node.
     * @return the text.
     */
    public static String selectText(XPathExpression expr, Node context) {
        try {
            return (String) expr.evaluate(context, XPathConstants.STRING);
        }
        catch (XPathExpressionException e) {
            throw new XmlException(e);
        }
    }

    /**
     * Evaluates the XPath expression as an Integer.
     * 
     * @param expr
     *        the expression.
     * @param context
     *        the context node.
     * @return the integer, or null if the value is not an integer.
     */
    public static Integer selectInteger(XPathExpression expr, Node context) {
        String value = selectText(expr, context);
        if (StringUtils.isNotBlank(value)) {
            try {
                return Integer.parseInt(value);
            }
            catch (NumberFormatException e) {
                return null;
            }
        }
        else {
            return null;
        }
    }

    /**
     * Evaluates the XPath expression as a Long.
     * 
     * @param expr
     *        the expression.
     * @param context
     *        the context node.
     * @return the long, or null if the value is not a long.
     */
    public static Long selectLong(XPathExpression expr, Node context) {
        String value = selectText(expr, context);
        if (StringUtils.isNotBlank(value)) {
            try {
                return Long.parseLong(value);
            }
            catch (NumberFormatException e) {
                return null;
            }
        }
        else {
            return null;
        }
    }

    /**
     * Evaluates the XPath expression to a single element.
     * 
     * @param expr the expression to evaluate.
     * @param context the context node.
     * @return the single element (or null).
     */
    public static Element selectElement(XPathExpression expr, Node context) {
        try {
            Object result = expr.evaluate(context, XPathConstants.NODE);
            if (result == null) {
                return null;
            }
            else if (result instanceof Element) {
                return (Element) result;
            }
            else {
                throw new XmlException("Not an element: " + result);
            }
        }
        catch (XPathExpressionException e) {
            throw new XmlException(e);
        }
    }

    /**
     * Evaluates the XPath expression to a list of elements.
     * 
     * @param expr the expression to evaluate.
     * @param context the context node.
     * @return the list of elements.
     */
    public static List<Element> selectElements(XPathExpression expr, Node context) {
        try {
            List<Element> elements = new ArrayList<Element>();
            NodeList result = (NodeList) expr.evaluate(context, XPathConstants.NODESET);
            for (int i = 0; i < result.getLength(); i++) {
                Node item = result.item(i);
                if (item instanceof Element) {
                    elements.add((Element) item);
                }
                else {
                    throw new XmlException("Not an element: " + item);
                }
            }
            return elements;
        }
        catch (XPathExpressionException e) {
            throw new XmlException(e);
        }
    }

    /**
     * Gets the text value of the given element.
     * 
     * @param e the element.
     * @return the text contents of the given element.s
     */
    public static String getText(Element e) {
        StringBuilder sb = null;

        if (e != null) {
            NodeList children = e.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);

                switch (node.getNodeType()) {
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    if (sb == null) {
                        sb = new StringBuilder();
                    }
                    sb.append(node.getNodeValue());
                    break;
                }
            }
        }

        return (sb != null) ? sb.toString() : null;
    }

    /**
     * Gets the first child element.
     * 
     * @param e the element.
     * @return the first child element.
     */
    public static Element getFirstChildElement(Element e) {
        if (e != null) {
            NodeList children = e.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    return (Element) node;
                }
            }
        }
        return null;
    }

    /**
     * This is an implementation of NamespaceContext that uses a Map to hold the
     * prefix->namespaceURI mapping.
     * 
     * @author jonnymiller
     */
    public static class NamespaceContextMap implements NamespaceContext {
        private Map<String, String> namespaces;

        public NamespaceContextMap(Map<String, String> namespaces) {
            this.namespaces = namespaces;
        }

        @Override
        public String getNamespaceURI(String prefix) {
            String namespaceURI = namespaces.get(prefix);
            if (namespaceURI != null) {
                return namespaceURI;
            }
            if (XMLConstants.XML_NS_PREFIX.equals(prefix)) {
                return XMLConstants.XML_NS_URI;
            }
            if (XMLConstants.XMLNS_ATTRIBUTE.equals(prefix)) {
                return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
            }
            return XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String namespaceURI) {
            if (XMLConstants.XML_NS_URI.equals(namespaceURI)) {
                return XMLConstants.XML_NS_PREFIX;
            }
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespaceURI)) {
                return XMLConstants.XMLNS_ATTRIBUTE;
            }

            for (String prefix : namespaces.keySet()) {
                String ns = namespaces.get(prefix);
                if (namespaceURI.equals(ns)) {
                    return prefix;
                }
            }
            return null;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Iterator getPrefixes(String namespaceURI) {
            if (XMLConstants.XML_NS_URI.equals(namespaceURI)) {
                return Collections.singletonList(XMLConstants.XML_NS_PREFIX).iterator();
            }
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespaceURI)) {
                return Collections.singletonList(XMLConstants.XMLNS_ATTRIBUTE).iterator();
            }

            List<String> prefixes = new ArrayList<String>();
            for (String prefix : namespaces.keySet()) {
                String ns = namespaces.get(prefix);
                if (namespaceURI.equals(ns)) {
                    prefixes.add(prefix);
                }
            }
            return Collections.unmodifiableList(prefixes).iterator();
        }
    }
}
