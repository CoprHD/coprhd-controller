/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class XMLStatMarshaller implements StatMarshaller {
    final private Logger _logger = LoggerFactory
            .getLogger(XMLStatMarshaller.class);
    private AtomicLong _count = new AtomicLong(0);
    private static JAXBContext _context = null;
    static {
        try {
            _context = JAXBContext.newInstance(Stat.class);
        } catch (JAXBException e) {
            throw APIException.internalServerErrors.jaxbContextError(e.getMessage(), e);
        }
    }

    @Override
    public void header(PrintWriter writer) {
        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        writer.println("<stats>");
    }

    /**
     * Using ThreadLocal To ensure Marshaler is unique per thread since
     * Marshaler by default is NOT thread safe.
     */
    private final ThreadLocal<Marshaller> marshallers = new ThreadLocal<Marshaller>() {
        protected Marshaller initialValue() {
            Marshaller m = null;
            try {
                m = _context.createMarshaller();
                m.setProperty(Marshaller.JAXB_FRAGMENT, true);
                m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            } catch (JAXBException e) {
                _logger.error("XML marshaller creation failed", e);

            }
            return m;
        }
    };

    public void marshall(Stat stat, PrintWriter writer)
            throws MarshallingExcetion {
        try {
            if (stat == null) {
                _logger.warn("null event dropped");
            } else {
                Marshaller marshaller = marshallers.get();
                if (marshaller == null) {
                    _logger.error("Unable to create XML marshaller");
                }
                marshaller.marshal(stat, writer);
                _count.incrementAndGet();
            }
        } catch (JAXBException e) {
            throw new MarshallingExcetion("XML Marshalling Error"
                    + stat.getResourceId(), e);
        }
    }

    @Override
    public void tailer(PrintWriter writer) {
        writer.println("</stats>");
        _logger.info("{} XML events streamed", _count);
    }

    @Override
    public void error(PrintWriter writer, String error) {
        writer.println("<stats>" + error + "</stats>");
    }
}