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
package com.emc.sa.catalog;

import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.descriptor.ServiceFieldTable;
import com.emc.sa.descriptor.ServiceItem;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow.CustomServicesWorkflowStatus;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Input;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.InputGroup;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.PostConstruct;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service Descriptor for Workflow services
 */
@Component
public class WorkflowServiceDescriptor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowServiceDescriptor.class);
    private static final String INPUT_FROM_USER_INPUT_TYPE = "InputFromUser";
    private static final String ASSET_INPUT_SINGLE_TYPE = "AssetOptionSingle";
    private static final String ASSET_INPUT_MULTI_TYPE = "AssetOptionMulti";
    private static final String CUSTOM_SERVICE_CATEGORY = "Custom Services";

    @PostConstruct
    public void init() {
        log.info("Initializing WorkflowServiceDescriptor");
    }

    @Autowired
    private CustomServicesWorkflowManager customServicesWorkflowManager;

    public ServiceDescriptor getDescriptor(String serviceName) {
        log.debug("Getting workflow descriptor for {}", serviceName);
        List<CustomServicesWorkflow> results = customServicesWorkflowManager.getByName(serviceName);
        if (null == results || results.isEmpty()) {
            return null;
        }
        if (results.size() > 1) {
            throw new IllegalStateException(String.format("Multiple workflows with the name %s", serviceName));
        }
        CustomServicesWorkflow customServicesWorkflow = results.get(0);
        return mapWorkflowToServiceDescriptor(customServicesWorkflow);
    }

    // This method will only return service descriptors for PUBLISHED workflwos
    public Collection<ServiceDescriptor> listDescriptors() {
        List<ServiceDescriptor> wfServiceDescriptors = new ArrayList<>();
        List<NamedElement> oeElements = customServicesWorkflowManager.listByStatus(CustomServicesWorkflowStatus.PUBLISHED);
        if (null != oeElements) {
            CustomServicesWorkflow oeWorkflow;
            for (NamedElement oeElement : oeElements) {
                oeWorkflow = customServicesWorkflowManager.getById(oeElement.getId());
                wfServiceDescriptors.add(mapWorkflowToServiceDescriptor(oeWorkflow));
            }
        }

        return wfServiceDescriptors;
    }

    private ServiceDescriptor mapWorkflowToServiceDescriptor(final CustomServicesWorkflow from) {
        final ServiceDescriptor to = new ServiceDescriptor();
        try {
            final CustomServicesWorkflowDocument wfDocument = WorkflowHelper.toWorkflowDocument(from);
            to.setCategory(CUSTOM_SERVICE_CATEGORY);
            to.setDescription(wfDocument.getDescription());
            to.setDestructive(false);
            to.setServiceId(wfDocument.getName());
            to.setTitle(wfDocument.getName());
            to.setWorkflowId(wfDocument.getName());

            for (final Step step : wfDocument.getSteps()) {
                if (null != step.getInputGroups()) {
                    // Looping through all input groups
                    MultiValueMap tableMap = new MultiValueMap();
                    for (final InputGroup inputGroup : step.getInputGroups().values()) {
                        for (final Input wfInput : inputGroup.getInputGroup()) {
                            ServiceField serviceField = new ServiceField();
                            // Creating service fields for only inputs of type "inputfromuser" and "assetoption"
                            if (INPUT_FROM_USER_INPUT_TYPE.equals(wfInput.getType())) {
                                serviceField.setType(wfInput.getInputFieldType());
                            } else if (ASSET_INPUT_SINGLE_TYPE.equals(wfInput.getType())){
                                serviceField.setType(wfInput.getValue());
                            } else if (ASSET_INPUT_MULTI_TYPE.equals(wfInput.getType())) {
                                serviceField.setType(wfInput.getValue());
                                serviceField.setSelect(ServiceField.SELECT_MANY);
                            }else {
                                continue;
                            }
                            String inputName = wfInput.getName();
                            // TODO: change this to get description
                            serviceField.setDescription(wfInput.getFriendlyName());
                            final String friendlyName = StringUtils.isBlank(wfInput.getFriendlyName()) ?
                                    inputName :
                                    wfInput.getFriendlyName();
                            serviceField
                                    .setLabel(friendlyName);
                            serviceField.setName(friendlyName);
                            serviceField.setRequired(wfInput.getRequired());
                            serviceField.setInitialValue(wfInput.getDefaultValue());
                            // Setting all unlocked fields as lockable
                            if (!wfInput.getLocked()) {
                                serviceField.setLockable(true);
                            }
                            //if there is a table name we will build ServiceFieldTable later
                            if (null != wfInput.getTableName()){
                                tableMap.put(wfInput.getTableName(),serviceField);
                            } else {
                                to.getItems().put(friendlyName, serviceField);
                            }
                        }

                    }
                    for (String table: (Set<String>) tableMap.keySet()){
                        ServiceFieldTable serviceFieldTable = new ServiceFieldTable();
                        serviceFieldTable.setType(ServiceItem.TYPE_TABLE);
                        serviceFieldTable.setLabel(table);
                        serviceFieldTable.setName(table);
                        for (ServiceField serviceField : (List<ServiceField>)tableMap.getCollection(table)){
                            serviceFieldTable.addItem(serviceField);
                        }
                        to.getItems().put(table,serviceFieldTable);
                    }
                }
            }

        } catch (final IOException io) {
            log.error("Error deserializing workflow", io);
            throw new IllegalStateException(String.format("Error deserializing workflow %s", from.getName()));
        }
        log.debug("Mapped workflow service descriptor for {}", from.getName());
        return to;
    }
}
