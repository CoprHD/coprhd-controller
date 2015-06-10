/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering;

import java.io.PrintWriter;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.TimeSeriesSerializer;

public abstract class XMLMarshaller {
    private Logger       _logger  = LoggerFactory.getLogger(XMLMarshaller.class);
    private static JAXBContext _context = null;
    static {
        try {
            _context = JAXBContext.newInstance(Stat.class);
        } catch (JAXBException e) {
            throw new IllegalStateException("Unable to create a JAXB context:", e);
        }
    }
    /**
     * 
     * @param <T>
     * @param writer
     * @param records
     * @throws Exception
     */
    public <T extends TimeSeriesSerializer.DataPoint> void dumpXML(
            PrintWriter writer, List<T> records) throws Exception {
        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        header(writer);
        for (T record : records)
            marshall(record, writer);
        tailer(writer);
    }
    /**
     * Header delegated to subClasses
     * @param writer
     */
    public abstract void header(PrintWriter writer);
    /**
     * Tailer delegated to subclasses
     * @param writer
     */
    public abstract void tailer(PrintWriter writer);

    /**
     * Using ThreadLocal To ensure Marshaler is unique per thread since
     * Marshaler by default is NOT thread safe.
     */
    private final ThreadLocal<Marshaller> marshallers = 
        new ThreadLocal<Marshaller>() {
        protected Marshaller initialValue() {
            Marshaller m = null;
            try {
                m = _context
                .createMarshaller();
                m.setProperty(
                        Marshaller.JAXB_FRAGMENT,
                        true);
                m.setProperty(
                        Marshaller.JAXB_FORMATTED_OUTPUT,
                        Boolean.TRUE);
            } catch (JAXBException e) {
                _logger.error(
                        "XML marshaller creation failed",
                        e);
            }
            return m;
        }
    };
    /**
     * marshall each Stat Record
     * @param <T>
     * @param record
     * @param writer
     * @throws Exception
     */
    public <T extends TimeSeriesSerializer.DataPoint> void marshall(
            T record, PrintWriter writer) throws Exception {
        try {
            if (record == null) {
                _logger.warn("null event dropped");
            } else {
                Marshaller marshaller = marshallers.get();
                if (marshaller == null) {
                    _logger.error("Unable to create XML marshaller");
                }
                marshaller.marshal(record, writer);
            }
        } catch (JAXBException e) {
            _logger.error("Marshalling failed : ", e);
        }
    }

    public void error(PrintWriter writer, String error) {
        writer.println("<stats>" + error + "</stats>");
    }
}