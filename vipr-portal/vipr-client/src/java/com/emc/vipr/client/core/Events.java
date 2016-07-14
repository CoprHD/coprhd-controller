/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.impl.SearchConstants.TENANT_PARAM;
import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.event.EventBulkRep;
import com.emc.storageos.model.event.EventCreateParam;
import com.emc.storageos.model.event.EventList;
import com.emc.storageos.model.host.EventRestRep;
import com.emc.storageos.model.tasks.EventStatsRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Hosts resources.
 * <p>
 * Base URL: <tt>/vdc/events</tt>
 */
public class Events extends AbstractCoreBulkResources<EventRestRep>implements TenantResources<EventRestRep> {

    public Events(ViPRCoreClient parent, RestClient client) {
        super(parent, client, EventRestRep.class, PathConstants.EVENT_URL);
    }

    @Override
    public Events withInactive(boolean inactive) {
        return (Events) super.withInactive(inactive);
    }

    @Override
    public Events withInternal(boolean internal) {
        return (Events) super.withInternal(internal);
    }

    @Override
    protected List<EventRestRep> getBulkResources(BulkIdParam input) {
        EventBulkRep response = client.post(EventBulkRep.class, input, getBulkUrl());
        return defaultList(response.getHosts());
    }

    public void deactivate(URI id) {
        doDeactivate(id);
    }

    public void approve(URI id) {
        client.post(String.class, this.getIdUrl() + "/approve", id);
    }

    public void decline(URI id) {
        client.post(String.class, this.getIdUrl() + "/decline", id);
    }

    /**
     * Returns task statistics for the given tenant
     */
    public EventStatsRestRep getStatsByTenant(URI tenantId) {
        UriBuilder builder = client.uriBuilder(baseUrl + "/stats");
        addTenant(builder, tenantId);

        URI uri = builder.build();

        EventStatsRestRep events = client.resource(uri).get(EventStatsRestRep.class);
        events.setDeclined(100);
        return events;
    }

    private void addTenant(UriBuilder builder, URI tenantId) {
        if (tenantId != null) {
            builder.queryParam(TENANT_PARAM, tenantId);
        }
    }

    public EventRestRep create(EventCreateParam input) {
        return client.post(EventRestRep.class, input, PathConstants.EVENT_URL);
    }

    @Override
    public List<NamedRelatedResourceRep> listByTenant(URI tenantId) {
        UriBuilder uriBuilder = client.uriBuilder(baseUrl);
        if (tenantId != null) {
            uriBuilder.queryParam(TENANT_PARAM, tenantId);
        }
        EventList response = client.getURI(EventList.class, uriBuilder.build());
        return defaultList(response.getEvents());
    }

    @Override
    public List<NamedRelatedResourceRep> listByUserTenant() {
        return listByTenant(null);
    }

    @Override
    public List<EventRestRep> getByTenant(URI tenantId) {
        return getByTenant(tenantId, null);
    }

    @Override
    public List<EventRestRep> getByUserTenant() {
        return getByTenant(parent.getUserTenantId(), null);
    }

    @Override
    public List<EventRestRep> getByUserTenant(ResourceFilter<EventRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(parent.getUserTenantId());
        return getByRefs(refs, filter);
    }

    @Override
    public List<EventRestRep> getByTenant(URI tenantId, ResourceFilter<EventRestRep> filter) {
        List<NamedRelatedResourceRep> refs = listByTenant(tenantId);
        return getByRefs(refs, filter);
    }
}
