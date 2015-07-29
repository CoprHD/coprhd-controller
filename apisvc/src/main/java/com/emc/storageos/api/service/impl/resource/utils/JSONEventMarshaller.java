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

import com.emc.storageos.db.client.model.Event;

/**
 * A JSON event marshaler based on Jersey-Jackson API
 */
public class JSONEventMarshaller implements EventMarshaller {

    final private Logger _logger = LoggerFactory.getLogger(JSONEventMarshaller.class);

    private ObjectMapper _mapper = null;

    /**
     * internal count of events streamed from all threads.
     */
    private final AtomicLong _count = new AtomicLong(0);

    /**
     * atomic boolean indicating whether the very first event has been streamed.
     * This is important since in JSON format, the first element is streamed
     * differently from all the rest elements.
     */
    private final AtomicBoolean _firstWritten = new AtomicBoolean(false);

    public JSONEventMarshaller() {
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
            ow.write("{ \"events\": [");
        } catch (IOException e) {
            throw new MarshallingExcetion("JSON head Streaming failed", e);
        }
    }

    @Override
    public void marshal(Event event, Writer writer) throws MarshallingExcetion {

        BufferedWriter ow = ((BufferedWriter) writer);

        if (event == null) {
            _logger.warn("null event dropped");
        } else {
            writeOneEvent(ow, event);
        }
    }

    /**
     * Stream out one event.
     * Since the streaming format for the first event is slightly different from all the
     * rest of events, this method uses a boolean to block event being streamed until
     * the first event is streamed by a thread.
     * 
     * @param writer
     *            - the output writer to stream the event.
     * @param event
     *            - the event to be streamed.
     * @throws MarshallingExcetion
     *             - failure during streaming.
     */
    private void writeOneEvent(BufferedWriter writer, Event event) throws MarshallingExcetion {
        try {
            if (_count.getAndIncrement() > 0) {
                while (!_firstWritten.get()) {
                    // wait until the thread which writes the first event is done
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        _logger.warn("Sleep interrupted");
                    }
                }
                writer.write("," + _mapper.writeValueAsString(event));
            } else {
                writer.write(_mapper.writeValueAsString(event));
                _firstWritten.set(true);
            }
        } catch (JsonGenerationException e) {
            throw new MarshallingExcetion("JSON Generation Error", e);
        } catch (JsonMappingException e) {
            throw new MarshallingExcetion("JSON Mapping Error", e);
        } catch (IOException e) {
            throw new MarshallingExcetion("JSON streaming failed: " + event.getEventId(), e);
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
        _logger.info("{} JSON events streamed", _count);
    }

}
