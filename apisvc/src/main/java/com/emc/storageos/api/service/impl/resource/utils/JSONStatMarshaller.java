/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service.impl.resource.utils;

import java.io.IOException;
import java.io.PrintWriter;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Stat;

public class JSONStatMarshaller implements StatMarshaller {
    final private Logger _logger = LoggerFactory
            .getLogger(JSONStatMarshaller.class);
    private ObjectMapper _mapper = null;
    private long _count = 0;

    public JSONStatMarshaller() {
        _mapper = new ObjectMapper();
        AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
        _mapper.getDeserializationConfig().withAnnotationIntrospector(
                introspector);
        _mapper.getSerializationConfig().withAnnotationIntrospector(
                introspector);
    }

    @Override
    public void header(PrintWriter writer) {
        writer.println("{ \"stats\": [");
    }

    @Override
    public synchronized void  marshall(Stat stat, PrintWriter writer) {
        if (stat == null) {
            _logger.warn("Null Stat received");
        } else {
            try {
                if (_count > 0) {
                    writer.print(",\n" + _mapper.writeValueAsString(stat));
                } else {
                    writer.print(_mapper.writeValueAsString(stat));
                }
            } catch (JsonGenerationException e) {
                _logger.error("JSON Streaming Error", e);
            } catch (JsonMappingException e) {
                _logger.error("JSON Mapping Excpetion Error", e);
            } catch (IOException e) {
                _logger.error("JSON IO Exception", e);
            }
            _count++;
        }
    }

    @Override
    public void tailer(PrintWriter writer) {
        writer.println("] }");
        _logger.info("{} JSON events streamed", _count);
    }

    @Override
    public void error(PrintWriter writer, String error) {
        writer.println("{ \"error\": [" + error + "] }");
    }

}
