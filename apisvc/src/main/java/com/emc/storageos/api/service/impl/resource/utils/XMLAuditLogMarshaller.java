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

import com.emc.storageos.security.audit.AuditLogUtils;
import com.emc.storageos.db.client.model.AuditLog;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * An XML auditlog marshaler based on JAXB API
 */
public class XMLAuditLogMarshaller implements AuditLogMarshaller {

    final private Logger _logger = LoggerFactory.getLogger(XMLAuditLogMarshaller.class);

    final static private Logger _staticLogger = LoggerFactory.getLogger(XMLAuditLogMarshaller.class);

    private static JAXBContext _context = null;
    private static volatile Locale locale = null;
    private static volatile ResourceBundle resb = null;

    static {
        try {
            _context = JAXBContext.newInstance(AuditLog.class);
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
        BufferedWriter ow = ((BufferedWriter) writer);
        try {
            ow.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.write("<auditlogs>");
        } catch (IOException e) {
            throw new MarshallingExcetion("XML head Streaming failed", e);
        }
    }

    @Override
    public void marshal(AuditLog auditlog, Writer writer) throws MarshallingExcetion {
        marshal(auditlog, writer, null);
    }

    @Override
    public boolean marshal(AuditLog auditlog, Writer writer,String keyword) throws MarshallingExcetion {
        BufferedWriter ow = ((BufferedWriter) writer);
        try {
            if (auditlog == null) {
                _logger.warn("null auditlog dropped");
                return false;
            }
            Marshaller marshaller = getMarshaller();
            if (marshaller == null) {
                _logger.error("Unable to create XML marshaller");
                return false;
            }
            AuditLogUtils.resetDesc(auditlog, resb);
            if (AuditLogUtils.isKeywordContained(auditlog,keyword)) {
                StringWriter sw = new StringWriter();
                marshaller.marshal(auditlog, sw);
                ow.write(sw.toString());
                return true;
            }
            _logger.debug("{} filter out by description keyword {}", auditlog.getDescription(), keyword);
            return false;
        } catch (JAXBException e) {
            throw new MarshallingExcetion("XML Marshalling Error", e);
        } catch (IOException e) {
            throw new MarshallingExcetion("XML Streaming Error", e);
        }
    }

    @Override
    public void tailer(Writer writer) throws MarshallingExcetion {
        BufferedWriter ow = ((BufferedWriter) writer);
        try {
            ow.write("</auditlogs>");
        } catch (IOException e) {
            throw new MarshallingExcetion("XML tail Streaming failed", e);
        }
    }

    @Override
    public void setLang(String lang) {
        String language, country;
        String[] array = lang.split("_");
        if (array.length != 2) {
            language = "en";
            country = "US";
        } else {
            language = array[0];
            country = array[1];
        }

        locale = new Locale(language, country);
        resb = ResourceBundle.getBundle("SDSAuditlogRes", locale);
    }
}
