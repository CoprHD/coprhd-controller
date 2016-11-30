/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.response;

import java.net.URI;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.search.SearchResultResourceRep;

/**
 * list of searched named resources (including id, link, name info etc.)
 */
public class SearchedResRepList extends ResRepList<SearchResultResourceRep> {

    private static final Logger _log = LoggerFactory.getLogger(SearchedResRepList.class);

    private ResourceTypeEnum _type;

    public SearchedResRepList() {
    }

    public SearchedResRepList(ResourceTypeEnum type) {
        _type = type;
    }

    @Override
    public SearchResultResourceRep createQueryHit(URI uri) {
        RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(_type, uri));
        SearchResultResourceRep r = new SearchResultResourceRep(uri, selfLink, null);
        _log.info("===== hit res, id: {}, name: {}, ref: {}, match: {}", uri, selfLink.getLinkName(), selfLink.getLinkRef(), r.getMatch());
        return r;
    }

    @Override
    public SearchResultResourceRep createQueryHit(URI uri, String match, UUID timestamp) {
        RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(_type, uri));
        _log.info("===== hit res 2, id: {}, name: {}, ref: {}, match: {}", uri, selfLink.getLinkName(), selfLink.getLinkRef(), match);
        return new SearchResultResourceRep(uri, selfLink, match);
    }

}
