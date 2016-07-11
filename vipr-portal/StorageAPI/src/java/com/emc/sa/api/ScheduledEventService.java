/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api;

import static com.emc.sa.api.mapper.ScheduledEventMapper.*;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.db.client.URIUtil.asString;
import static com.emc.storageos.db.client.URIUtil.uri;

import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.URIUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.utils.ValidationUtils;
import com.emc.sa.catalog.CatalogServiceManager;
import com.emc.sa.descriptor.*;
import com.emc.sa.util.TextUtils;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.db.client.model.uimodels.*;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.*;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.model.catalog.*;
import com.google.common.collect.Lists;
import com.emc.sa.api.OrderService;

@DefaultPermissions(
        readRoles = {},
        writeRoles = {})
@Path("/catalog/events")
public class ScheduledEventService extends CatalogTaggedResourceService {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd_HH:mm:ss";

    private static final String EVENT_SERVICE_TYPE = "catalog-event";

    @Autowired
    private ModelClient client;

    @Autowired
    private CatalogServiceManager catalogServiceManager;

    @Autowired
    private OrderService orderService;

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.SCHEDULED_EVENT;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Override
    protected ScheduledEvent queryResource(URI id) {
        return getScheduledEventById(id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        ScheduledEvent event = queryResource(id);
        return uri(event.getTenant());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<ScheduledEvent> getResourceClass() {
        return ScheduledEvent.class;
    }

    @POST
    @Path("")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ScheduledEventRestRep createEvent(ScheduledEventCreateParam createParam) {
        StorageOSUser user = getUserFromContext();

        URI tenantId = createParam.getOrderCreateParam().getTenantId();
        if (tenantId != null) {
            verifyAuthorizedInTenantOrg(tenantId, user);
        }
        else {
            tenantId = uri(user.getTenantId());
        }

        ArgValidator.checkFieldNotNull(createParam.getOrderCreateParam().getCatalogService(), "catalogService");
        CatalogService catalogService = catalogServiceManager.getCatalogServiceById(createParam.getOrderCreateParam().getCatalogService());
        if (catalogService == null) {
            throw APIException.badRequests.orderServiceNotFound(
                    asString(createParam.getOrderCreateParam().getCatalogService()));
        }

        URI scheduledEventId = URIUtil.createId(ScheduledEvent.class);
        ScheduledEvent newObject = createNewObject(tenantId, scheduledEventId, createParam);
        if (catalogService.getExecutionWindowRequired()) {
            newObject.setExecutionWindowId(catalogService.getDefaultExecutionWindowId());
        } else {
            // TODO: set to global one
        }

        if (catalogService.getApprovalRequired() == false) {
            newObject.setEventStatus(ScheduledEventStatus.APPROVED);
        }

        client.save(newObject);

        createParam.getOrderCreateParam().setScheduledEventId(scheduledEventId);
        OrderRestRep restRep = orderService.createOrder(createParam.getOrderCreateParam());

        newObject.setLatestOrderId(restRep.getId());
        client.save(newObject);

        return map(newObject);
    }

    @GET
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public ScheduledEventRestRep getScheduledEvent(@PathParam("id") String id) {

        ScheduledEvent scheduledEvent = queryResource(uri(id));

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(uri(scheduledEvent.getTenant()), user);

        return map(scheduledEvent);
    }

    private ScheduledEvent getScheduledEventById(URI id, boolean checkInactive) {
        ScheduledEvent scheduledEvent = client.scheduledEvents().findById(id);
        ArgValidator.checkEntity(scheduledEvent, id, isIdEmbeddedInURL(id), checkInactive);
        return scheduledEvent;
    }

}
