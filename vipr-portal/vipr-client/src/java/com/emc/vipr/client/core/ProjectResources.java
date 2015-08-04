/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.search.ProjectSearchBuilder;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

public abstract class ProjectResources<T extends DataObjectRestRep> extends AbstractCoreBulkResources<T> {
    public ProjectResources(ViPRCoreClient parent, RestClient client, Class<T> resourceClass, String baseUrl) {
        super(parent, client, resourceClass, baseUrl);
    }

    /**
     * Creates a search builder for searching by project.
     * 
     * @return a project search builder.
     */
    @Override
    public ProjectSearchBuilder<T> search() {
        return new ProjectSearchBuilder<T>(this);
    }

    /**
     * Finds the list of resources in the given project.
     * 
     * @param project
     *            the project.
     * @return the list of resources.
     */
    public List<T> findByProject(ProjectRestRep project) {
        return findByProject(ResourceUtils.id(project), null);
    }

    /**
     * Finds the list of resources in the given project, optionally filtering the results.
     * 
     * @param project
     *            the project.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of resources.
     */
    public List<T> findByProject(ProjectRestRep project, ResourceFilter<T> filter) {
        return findByProject(ResourceUtils.id(project), filter);
    }

    /**
     * Finds the list of resources in the given project by ID.
     * 
     * @param projectId
     *            the ID of the project.
     * @return the list of resources.
     */
    public List<T> findByProject(URI projectId) {
        return search().byProject(projectId).run();
    }

    /**
     * Finds the list of resources in the given project by ID, optionally filtering the results.
     * 
     * @param projectId
     *            the ID of the project.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of resources.
     */
    public List<T> findByProject(URI projectId, ResourceFilter<T> filter) {
        return search().byProject(projectId).filter(filter).run();
    }
}
