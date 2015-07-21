/* **********************************************************
 * Copyright 2010 VMware, Inc.  All rights reserved. -- VMware Confidential
 * **********************************************************/

package com.emc.storageos.vasa.util;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.net.URLDecoder;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Simple wrapper for the Java XPath API
 */
public class XmlParser {
	/** Class logger */
	private static Log log = LogFactory.getLog(XmlParser.class);

	private Document document;
	private XPath xpath;

	/** Constructor */
	public XmlParser() {
		document = null;
		xpath = XPathFactory.newInstance().newXPath();
	}

	/**
	 * Loads the given XML file for parsing.
	 * 
	 * @param filename
	 *            name of XML file to load
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	public void loadFile(String filename) throws ParserConfigurationException,
			SAXException, IOException {
		DocumentBuilder builder = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		document = builder.parse(new File(filename));
	}

	/**
	 * Loads the given XML resource for parsing.
	 * 
	 * @param resource
	 *            XML resource to load
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	public void loadResource(String resource)
			throws ParserConfigurationException, SAXException, IOException {
		URL filePath = getClass().getClassLoader().getResource(resource);
		loadFile(URLDecoder.decode(filePath.getFile(), "UTF-8"));
	}

	/**
	 * Loads the given XML string for parsing.
	 * 
	 * @param source
	 *            XML string to load
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	public void loadString(String source) throws ParserConfigurationException,
			SAXException, IOException {
		DocumentBuilder builder = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder();
		document = builder.parse(new InputSource(new StringReader(source)));
	}

	/**
	 * Evaluates the given XPath expression against the context and returns the
	 * string value of the result. The context can be a node or nodelist
	 * relative to which the expression is to be evaluated. If null, the loaded
	 * document object is used.
	 * 
	 * @param expression
	 *            XPath expression
	 * @param context
	 *            root object (node or nodelist) against which to evaluate the
	 *            XPath expression
	 * 
	 * @return string value of result
	 * @throws XPathExpressionException
	 */
	public String getString(String expression, Object context)
			throws XPathExpressionException {
		assert (expression != null);

		if (context == null) {
			assert (document != null);
			context = document;
		}

		return (String) xpath.evaluate(expression, context,
				XPathConstants.STRING);
	}

	/**
	 * Evaluates the given XPath expression against the context and returns the
	 * integer value of the result. The context can be a node or nodelist
	 * relative to which the expression is to be evaluated. If null, the loaded
	 * document object is used.
	 * 
	 * @param expression
	 *            XPath expression
	 * @param context
	 *            root object (node or nodelist) against which to evaluate the
	 *            XPath expression
	 * 
	 * @return integer value of result
	 * @throws XPathExpressionException
	 */
	public Integer getInteger(String expression, Object context)
			throws XPathExpressionException {
		assert (expression != null);

		if (context == null) {
			assert (document != null);
			context = document;
		}

		return ((Double) xpath.evaluate(expression, context,
				XPathConstants.NUMBER)).intValue();
	}

	/**
	 * Evaluates the given XPath expression against the context and returns the
	 * boolean value of the result. The context can be a node or nodelist
	 * relative to which the expression is to be evaluated. If null, the loaded
	 * document object is used.
	 * 
	 * @param expression
	 *            XPath expression
	 * @param context
	 *            root object (node or nodelist) against which to evaluate the
	 *            XPath expression
	 * 
	 * @return boolean value of result
	 * @throws XPathExpressionException
	 */
	public boolean getBoolean(String expression, Object context)
			throws XPathExpressionException {
		return Boolean.valueOf(getString(expression, context));
	}

	/**
	 * Evaluates the given XPath expression against the context and returns the
	 * result as a nodelist. The context can be a node or nodelist relative to
	 * which the expression is to be evaluated. If null, the loaded document
	 * object is used.
	 * 
	 * @param expression
	 *            XPath expression
	 * @param context
	 *            root object (node or nodelist) against which to evaluate the
	 *            XPath expression
	 * 
	 * @return result as a nodelist
	 * @throws XPathExpressionException
	 */
	public NodeList getNodeList(String expression, Object context)
			throws XPathExpressionException {
		assert (expression != null);

		if (context == null) {
			assert (document != null);
			context = document;
		}

		return (NodeList) xpath.evaluate(expression, context,
				XPathConstants.NODESET);
	}

	/**
	 * Evaluates the given XPath expression against the context and returns the
	 * result as a node. The context can be a node or nodelist relative to which
	 * the expression is to be evaluated. If null, the loaded document object is
	 * used.
	 * 
	 * @param expression
	 *            XPath expression
	 * @param context
	 *            root object (node or nodelist) against which to evaluate the
	 *            XPath expression
	 * 
	 * @return result as a node
	 * @throws XPathExpressionException
	 */
	public Node getNode(String expression, Object context)
			throws XPathExpressionException {
		assert (expression != null);

		if (context == null) {
			assert (document != null);
			context = document;
		}

		return (Node) xpath.evaluate(expression, context, XPathConstants.NODE);
	}
}
