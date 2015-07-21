/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Event;

/**
 *  An XML event marshaler based on JAXB API
 */
public class XMLEventMarshaller implements EventMarshaller {

    final private Logger _logger = LoggerFactory.getLogger(XMLEventMarshaller.class);
    
    final static private Logger _staticLogger = LoggerFactory.getLogger(XMLEventMarshaller.class);

    private static JAXBContext _context = null;
    static {
        try {
            _context = JAXBContext.newInstance(Event.class);
        } catch (JAXBException e) {
            _staticLogger.error("XML Marshaller Creation Error", e);
        }
      }
    
    private final ThreadLocal<Marshaller> marshallers = new ThreadLocal<Marshaller>() {

        protected Marshaller initialValue() {
            Marshaller m = null;
            try {
                m = _context.createMarshaller();
                m.setProperty(Marshaller.JAXB_FRAGMENT, true);
            } catch (JAXBException e) {
                _logger.error("XML marshaller creation failed", e);
            }

            return m;
        }
    };

    private Marshaller getMarshaller() {
        return marshallers.get();
    }

    @Override
    public void header(Writer writer) throws MarshallingExcetion {
        BufferedWriter ow = ((BufferedWriter)writer);
        try {
            ow.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.write("<events>");
        } catch (IOException e) {
            throw new MarshallingExcetion("XML head Streaming failed", e);
        }
    }

    @Override
    public void marshal(Event event, Writer writer) throws MarshallingExcetion {
        BufferedWriter ow = ((BufferedWriter)writer);
        try {
            if (event == null) {
                _logger.warn("null event dropped");
            } else {
                Marshaller marshaller = getMarshaller();

                if (marshaller == null) {
                    _logger.error("Unable to create XML marshaller");
                } else {
                    StringWriter sw = new StringWriter();
                    marshaller.marshal(event, sw);
                    ow.write(sw.toString());
                }
            }
        } catch (JAXBException e) {
            throw new MarshallingExcetion("XML Marshalling Error"+event.getEventId(), e);
        } catch (IOException e) {
            throw new MarshallingExcetion("XML Streaming Error"+event.getEventId(), e);
        }
    }

    @Override
    public void tailer(Writer writer) throws MarshallingExcetion {
        BufferedWriter ow = ((BufferedWriter)writer);
        try {
            ow.write("</events>");
        } catch (IOException e) {
            throw new MarshallingExcetion("XML tail Streaming failed", e);
        }
    }

}
