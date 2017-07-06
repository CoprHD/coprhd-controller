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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
import com.emc.storageos.primitives.CustomServicesConstants;
import com.emc.storageos.security.authorization.Role;

/**
 * Service Descriptor for Workflow services
 */
@Component
public class WorkflowServiceDescriptor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowServiceDescriptor.class);
    private static final String CUSTOM_SERVICE_CATEGORY = "Custom Services";
    @Autowired
    private CustomServicesWorkflowManager customServicesWorkflowManager;

    @PostConstruct
    public void init() {
        log.info("Initializing WorkflowServiceDescriptor");
    }

    public ServiceDescriptor getDescriptor(String serviceName) {
        log.debug("Getting workflow descriptor for {}", serviceName);
        List<CustomServicesWorkflow> results = customServicesWorkflowManager.getByName(serviceName);
        if (null == results || results.isEmpty()) {
            throw new IllegalStateException(String.format("No workflow with the name %s", serviceName));
        }
        if (results.size() > 1) {
            throw new IllegalStateException(String.format("Multiple workflows with the name %s", serviceName));
        }
        CustomServicesWorkflow customServicesWorkflow = results.get(0);
        return mapWorkflowToServiceDescriptor(customServicesWorkflow);
    }

    // This method will only return service descriptors for PUBLISHED workflows
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
            final List<CustomServicesWorkflow> wfs = customServicesWorkflowManager.getByName(wfDocument.getName());
            if (wfs.isEmpty() || wfs.size() > 1) {
                log.error("Cannot get workflow or more than one workflow mapped per workflow name:{}", wfDocument.getName());
                throw new IllegalStateException(
                        String.format("Cannot get workflow or more than one workflow mapped per workflow name %s", wfDocument.getName()));
            }
            if (StringUtils.isEmpty(wfs.get(0).getState()) || wfs.get(0).getState().equals(CustomServicesWorkflowStatus.NONE) ||
                    wfs.get(0).getState().equals(CustomServicesWorkflowStatus.INVALID)) {
                log.error("Workflow state is not valid. State:{} Workflow name:{}", wfs.get(0).getState(), wfDocument.getName());
                throw new IllegalStateException(String.format("Workflow state is not valid. State %s", wfs.get(0).getState()));
            }

            to.setCategory(CUSTOM_SERVICE_CATEGORY);
            to.setDescription(StringUtils.isNotBlank(wfDocument.getDescription()) ? wfDocument.getDescription() : wfDocument.getName());
            to.setDestructive(false);
            to.setServiceId(wfDocument.getName());
            to.setTitle(wfDocument.getName());
            to.setWorkflowId(wfDocument.getName());
            to.setRoles(new ArrayList<String>(Arrays.asList(Role.SYSTEM_ADMIN.toString())));

            for (final Step step : wfDocument.getSteps()) {
                if (null != step.getInputGroups()) {
                    // Looping through all input groups
                    for (final InputGroup inputGroup : step.getInputGroups().values()) {
                        final MultiValueMap tableMap = new MultiValueMap();
                        for (final Input wfInput : inputGroup.getInputGroup()) {
                            final ServiceField serviceField = new ServiceField();
                            if (CustomServicesConstants.InputType.FROM_USER.toString().equals(wfInput.getType())) {
                                serviceField.setType(wfInput.getInputFieldType());
                            } else if (CustomServicesConstants.InputType.ASSET_OPTION_SINGLE.toString().equals(wfInput.getType())) {
                                serviceField.setType(wfInput.getValue());
                            } else if (CustomServicesConstants.InputType.ASSET_OPTION_MULTI.toString().equals(wfInput.getType())) {
                                serviceField.setType(wfInput.getValue());
                                serviceField.setSelect(ServiceField.SELECT_MANY);
                            } else if (CustomServicesConstants.InputType.FROM_USER_MULTI.toString().equals(wfInput.getType())) {
                                serviceField.setType(ServiceField.TYPE_CHOICE);
                                if (StringUtils.isNotBlank(wfInput.getDefaultValue())) {
                                    // For list of options
                                    final Map<String, String> options = new HashMap<>();
                                    final List<String> defaultList = Arrays.asList(wfInput.getDefaultValue().split(","));
                                    for (final String value : defaultList) {
                                        // making the key and value the same
                                        options.put(value, value);
                                    }
                                    serviceField.setOptions(options);
                                    serviceField.setInitialValue(options.get(defaultList.get(0)));
                                } else if (MapUtils.isNotEmpty(wfInput.getOptions())) {
                                    // For options Map
                                    serviceField.setOptions(wfInput.getOptions());
                                }
                            } else {
                                continue;
                            }
                            final String inputName = wfInput.getName();
                            if (StringUtils.isNotBlank(wfInput.getDescription())) {
                                serviceField.setDescription(wfInput.getDescription());
                            }
                            final String friendlyName = StringUtils.isBlank(wfInput.getFriendlyName()) ? inputName
                                    : wfInput.getFriendlyName();
                            serviceField
                                    .setLabel(friendlyName);
                            serviceField.setName(friendlyName.replaceAll(CustomServicesConstants.SPACES_REGEX, StringUtils.EMPTY));
                            serviceField.setRequired(wfInput.getRequired());
                            if (!(CustomServicesConstants.InputType.FROM_USER_MULTI.toString().equals(wfInput.getType()))) {
                                // Initial value already set for FROM_USER_MULTI
                                serviceField.setInitialValue(wfInput.getDefaultValue());
                            }

                            // Setting all unlocked fields as lockable
                            if (!wfInput.getLocked()) {
                                serviceField.setLockable(true);
                            }
                            // if there is a table name we will build ServiceFieldTable later
                            if (null != wfInput.getTableName()) {
                                tableMap.put(wfInput.getTableName(), serviceField);
                            } else {
                                to.getItems().put(friendlyName, serviceField);
                            }
                        }
                        for (final String table : (Set<String>) tableMap.keySet()) {
                            final ServiceFieldTable serviceFieldTable = new ServiceFieldTable();
                            serviceFieldTable.setType(ServiceItem.TYPE_TABLE);
                            serviceFieldTable.setLabel(table);
                            serviceFieldTable.setName(table);
                            for (final ServiceField serviceField : (List<ServiceField>) tableMap.getCollection(table)) {
                                serviceFieldTable.addItem(serviceField);
                            }
                            to.getItems().put(table, serviceFieldTable);
                        }
                    }
                }
            }

        } catch (final IOException io) {
            log.error("Error deserializing workflow", io);
            throw new IllegalStateException(String.format("Error deserializing workflow %s", from.getLabel()));
        }
        log.debug("Mapped workflow service descriptor for {}", from.getLabel());
        return to;
    }
}
