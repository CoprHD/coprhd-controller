/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.search;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.core.AbstractResources;
import java.net.URI;
import static com.emc.vipr.client.core.impl.SearchConstants.PROJECT_PARAM;

public class ProjectSearchBuilder<T extends DataObjectRestRep> extends SearchBuilder<T> {
    public ProjectSearchBuilder(AbstractResources<T> resources) {
        super(resources);
    }

    public SearchBuilder<T> byProject(URI project) {
        return by(PROJECT_PARAM, project);
    }
}
