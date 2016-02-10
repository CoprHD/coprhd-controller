/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource.utils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.security.audit.AuditLogUtils;
import com.emc.storageos.db.client.model.AuditLog;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * A JSON auditlog marshaler based on Jersey-Jackson API
 */
public class JSONAuditLogMarshaller implements AuditLogMarshaller {

    final private Logger _logger = LoggerFactory.getLogger(JSONAuditLogMarshaller.class);

    private static volatile Locale locale = null;
    private static volatile ResourceBundle resb = null;

    private ObjectMapper _mapper = null;

    /**
     * internal count of auditlogs streamed from all threads.
     */
    private final AtomicLong _count = new AtomicLong(0);

    /**
     * atomic boolean indicating whether the very first auditlog has been streamed.
     * This is important since in JSON format, the first element is streamed
     * differently from all the rest elements.
     */
    private final AtomicBoolean _firstWritten = new AtomicBoolean(false);

    public JSONAuditLogMarshaller() {
        _mapper = new ObjectMapper();
        AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
        _mapper.getDeserializationConfig().withAnnotationIntrospector(introspector);
        _mapper.getSerializationConfig().withAnnotationIntrospector(introspector);
        _mapper.setSerializationInclusion(Inclusion.NON_NULL);
    }

    @Override
    public void header(Writer writer) throws MarshallingExcetion {
        BufferedWriter ow = ((BufferedWriter) writer);
        try {
            ow.write("{ \"auditlogs\": [");
        } catch (IOException e) {
            throw new MarshallingExcetion("JSON head Streaming failed", e);
        }
    }

    @Override
    public void marshal(AuditLog auditlog, Writer writer) throws MarshallingExcetion {
        marshal(auditlog, writer, null);
    }

    public boolean marshal(AuditLog auditlog, Writer writer, String keyword) throws MarshallingExcetion {

        BufferedWriter ow = ((BufferedWriter) writer);

        if (auditlog == null) {
            _logger.warn("null auditlog dropped");
            return false;
        }
        return matchAndwriteOneAuditLog(ow, auditlog, null);
    }

    /**
     * Match if auditlog description contains expected keyword ,Stream out the auditlog if
     * matched,Otherwise ignore this one piece of auditlog.
     * Since the streaming format for the first auditlog is slightly different from all the
     * rest of auditlogs, this method uses a boolean to block auditlog being streamed until
     * the first auditlog is streamed by a thread.
     * 
     * @param writer
     *            - the output writer to stream the auditlog.
     * @param auditlog
     *            - the auditlog to be streamed.
     * @param keyword
     *            - keyword if audit log description contain
     * @return true if the Auditlog outputted to the writer,else false
     * @throws MarshallingExcetion
     *             - failure during streaming.
     */
    private boolean matchAndwriteOneAuditLog(BufferedWriter writer, AuditLog auditlog,String keyword) throws MarshallingExcetion {
        try {
            AuditLogUtils.resetDesc(auditlog, resb);
            if (AuditLogUtils.isKeywordContained(auditlog,keyword)) {
                if (_count.getAndIncrement() > 0) {
                    while (!_firstWritten.get()) {
                        // wait until the thread which writes the first auditlog is done
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            _logger.warn("Sleep interrupted");
                        }
                    }
                    writer.write("," + _mapper.writeValueAsString(auditlog));
                } else {
                    writer.write(_mapper.writeValueAsString(auditlog));
                    _firstWritten.set(true);
                }
                return true;
            }
            _logger.debug("{} filter out by description keyword {}",auditlog.getDescription(),keyword);
            return false;
        } catch (JsonGenerationException e) {
            throw new MarshallingExcetion("JSON Generation Error", e);
        } catch (JsonMappingException e) {
            throw new MarshallingExcetion("JSON Mapping Error", e);
        } catch (IOException e) {
            throw new MarshallingExcetion("JSON streaming failed: ", e);
        }
    }

    @Override
    public void tailer(Writer writer) throws MarshallingExcetion {
        BufferedWriter ow = ((BufferedWriter) writer);
        try {
            ow.write("] }");
        } catch (IOException e) {
            throw new MarshallingExcetion("JSON tail Streaming failed", e);
        }
        _logger.info("{} JSON auditlogs streamed", _count);
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
