/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.model.dao.ModelClient;
import com.google.common.collect.Lists;

import javax.inject.Inject;
import java.net.URI;
import java.util.List;
public abstract class ModelClientTask<T> extends ExecutionTask<T> {
    @Inject
    protected ModelClient modelClient;

    /**
     * Converts strings to a list of URIs.
     * 
     * @param ids
     *        the IDs, as strings.
     * @return the list of URIs.
     */
    protected static List<URI> toURIs(Iterable<String> ids) {
        List<URI> uris = Lists.newArrayList();
        for (String id : ids) {
            uris.add(URI.create(id));
        }
        return uris;
    }
}
