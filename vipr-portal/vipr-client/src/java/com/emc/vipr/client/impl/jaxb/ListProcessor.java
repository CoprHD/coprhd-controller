/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl.jaxb;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.SAXParser;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

import com.emc.vipr.client.util.ItemProcessor;

public class ListProcessor<T> {
    private SAXParser saxParser;
    private DocumentBuilder documentBuilder;
    private Transformer transformer;

    private Unmarshaller unmarshaller;
    private ItemProcessor<T> itemProcessor;

    protected ListProcessor() {
    }

    public ListProcessor(Unmarshaller unmarshaller, ItemProcessor<T> itemProcessor) {
        this.unmarshaller = unmarshaller;
        this.itemProcessor = itemProcessor;
    }

    public ListProcessor(Class<T> itemClass, ItemProcessor<T> itemProcessor) {
        this.unmarshaller = XmlUtils.createUnmarshaller(itemClass);
        this.itemProcessor = itemProcessor;
    }

    public SAXParser getSAXParser() {
        if (saxParser == null) {
            saxParser = XmlUtils.createSAXParser();
        }
        return saxParser;
    }

    public void setSAXParser(SAXParser saxParser) {
        this.saxParser = saxParser;
    }

    public DocumentBuilder getDocumentBuilder() {
        if (documentBuilder == null) {
            documentBuilder = XmlUtils.createDocumentBuilder();
        }
        return documentBuilder;
    }

    public void setDocumentBuilder(DocumentBuilder documentBuilder) {
        this.documentBuilder = documentBuilder;
    }

    public Transformer getTransformer() {
        if (transformer == null) {
            transformer = XmlUtils.createTransformer();
        }
        return transformer;
    }

    public void setTransformer(Transformer transformer) {
        this.transformer = transformer;
    }

    public void process(InputStream in) throws IOException, SAXException, TransformerException {
        try {
            ItemFilter filter = new ItemFilter();
            SAXSource source = filter.createSource(in);
            DOMResult result = filter.createResult();
            getTransformer().transform(source, result);
        } finally {
            in.close();
        }
    }

    protected void startItems() throws Exception {
        itemProcessor.startItems();
    }

    protected void endItems() throws Exception {
        itemProcessor.endItems();
    }

    protected void processItem(Element e) throws Exception {
        @SuppressWarnings("unchecked")
        T item = (T) unmarshaller.unmarshal(e);
        itemProcessor.processItem(item);
    }

    protected class ItemFilter extends XMLFilterImpl {
        private int depth;
        private Document itemDocument;

        public SAXSource createSource(InputStream in) throws SAXException {
            setParent(getSAXParser().getXMLReader());
            return new SAXSource(this, new InputSource(in));
        }

        public DOMResult createResult() {
            itemDocument = getDocumentBuilder().newDocument();
            return new DOMResult(itemDocument);
        }

        @Override
        public void startDocument() throws SAXException {
            super.startDocument();
            try {
                startItems();
            } catch (Exception e) {
                throw new SAXException(e);
            }
        }

        @Override
        public void endDocument() throws SAXException {
            super.endDocument();
            try {
                endItems();
            } catch (Exception e) {
                throw new SAXException(e);
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            super.startElement(uri, localName, qName, atts);
            depth++;
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement(uri, localName, qName);
            depth--;
            if (depth == 1) {
                Element item = getLastChildElement(itemDocument.getDocumentElement());
                try {
                    processItem(item);
                } catch (Exception e) {
                    throw new SAXException(e);
                }
                clear(itemDocument.getDocumentElement());
            }
        }

        /**
         * Get the last element of the parent. This will be the most recently added element.
         * 
         * @param parent the parent element.
         * @return the last element.
         */
        private Element getLastChildElement(Element parent) {
            NodeList children = parent.getChildNodes();
            for (int i = children.getLength() - 1; i >= 0; i--) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    return (Element) child;
                }
            }
            return null;
        }

        /**
         * Clears all children of the given element.
         * 
         * @param parent the parent element.
         */
        private void clear(Element parent) {
            NodeList children = parent.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                parent.removeChild(child);
            }
        }
    }
}
