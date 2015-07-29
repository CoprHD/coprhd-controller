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

package com.emc.storageos.api.service.utils;

import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.MediaType;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.api.service.impl.resource.utils.MarshallingExcetion;
import com.emc.storageos.api.service.impl.resource.utils.StatMarshaller;
import com.emc.storageos.api.service.impl.resource.utils.StatMarshallerFactory;
import com.emc.storageos.api.service.impl.resource.utils.StatRetriever;
import com.emc.storageos.db.client.TimeSeriesMetadata.TimeBucket;
import com.emc.storageos.db.client.model.Stat;

/**
 * Implemation of StatRetriever to retrieve stats locally instead of getting from
 * Cassandra database.
 * 
 * @author rvobugar
 * 
 */
public class DummyStatRetriever implements StatRetriever {

    final private Logger _logger = LoggerFactory
            .getLogger(DummyStatRetriever.class);

    private List<Stat> getDummyStats() throws URISyntaxException {

        // Creates dummy stats for tests
        List<Stat> stlist = new ArrayList<Stat>();

        for (int i = 0; i < 100; i++) {
            Stat st = new Stat();
            st.setProject(new URI("http://mypoject"));
            st.setTenant(new URI("http://mytenant"));
            st.setUser(new URI("http://myuser"));
            st.setVirtualPool(new URI("http://vpool.gold"));
            st.setResourceId(new URI("http://resourceId"));
            stlist.add(st);
        }

        return stlist;
    }

    @Override
    public void getBulkStats(DateTime timeBucket, TimeBucket granularity,
            MediaType mediaType, PrintWriter out) throws MarshallingExcetion {

        StatMarshaller marshaller = StatMarshallerFactory
                .getMarshaller(mediaType);

        marshaller.header(out);

        List<Stat> stats = null;
        try {
            stats = getDummyStats();
        } catch (URISyntaxException e) {
            _logger.error("Error getting stats", e);
        }

        for (Stat stat : stats) {
            if (mediaType == MediaType.APPLICATION_XML_TYPE) {
                try {
                    marshaller.marshall(stat, out);
                } catch (Exception e) {
                    _logger.error(e.getMessage(), e);
                }
            } else if (mediaType == MediaType.APPLICATION_JSON_TYPE) {
                try {
                    marshaller.marshall(stat, out);
                } catch (Exception e) {
                    _logger.error(e.getMessage(), e);
                }
            }
        }
        marshaller.tailer(out);
    }
}
