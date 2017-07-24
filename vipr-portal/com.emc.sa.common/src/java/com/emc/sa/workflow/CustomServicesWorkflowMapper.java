/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.sa.workflow;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.customservices.CustomServicesWorkflowBulkRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowList;
import com.emc.storageos.model.customservices.CustomServicesWorkflowRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 *
 */
public class CustomServicesWorkflowMapper implements Function<CustomServicesWorkflow, CustomServicesWorkflowRestRep> {
    public static final CustomServicesWorkflowMapper instance = new CustomServicesWorkflowMapper();

    public static CustomServicesWorkflowMapper getInstance() {
        return instance;
    }

    private CustomServicesWorkflowMapper() {
    }
    
    public static CustomServicesWorkflowRestRep map(CustomServicesWorkflow from) {
        CustomServicesWorkflowRestRep to = new CustomServicesWorkflowRestRep(); 
        
        mapDataObjectFields(from, to);
        
        try {
            to.setDocument(WorkflowHelper.toWorkflowDocument(from));
            to.setState(from.getState());
        } catch (IOException e) {
            throw APIException.internalServerErrors.genericApisvcError("Error deserializing workflow", e);
        }
        
        return to;
    }
    
    public static CustomServicesWorkflowList mapList(List<NamedElement> fromList) {
        List<NamedRelatedResourceRep> resources = new ArrayList<NamedRelatedResourceRep>();
        for(NamedElement element : fromList) {
            resources.add(toNamedRelatedResource(ResourceTypeEnum.CUSTOM_SERVICES_WORKFLOW, element.getId(), element.getName()));
        }
        return new CustomServicesWorkflowList(resources);
    }
    
    public static CustomServicesWorkflowBulkRep mapBulk(List<CustomServicesWorkflow> workflows) {
        final List<CustomServicesWorkflowRestRep> workflowRestRepList = Lists.newArrayList();
        for( final CustomServicesWorkflow workflow : workflows) {
            workflowRestRepList.add(map(workflow));
        }
        return new CustomServicesWorkflowBulkRep(workflowRestRepList);
    }

    @Override
    public CustomServicesWorkflowRestRep apply(CustomServicesWorkflow input) {
        return map(input);
    }
}
