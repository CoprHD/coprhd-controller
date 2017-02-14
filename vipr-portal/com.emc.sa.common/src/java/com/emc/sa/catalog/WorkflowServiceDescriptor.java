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
import java.util.Collection;
import java.util.List;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow;
import com.emc.storageos.db.client.model.uimodels.CustomServicesWorkflow.OrchestrationWorkflowStatus;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Input;
import com.emc.storageos.model.customservices.CustomServicesWorkflowDocument.Step;

/**
 * Service Descriptor for Workflow services
 */
@Component
public class WorkflowServiceDescriptor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowServiceDescriptor.class);
    private static final String INPUT_FROM_USER_INPUT_TYPE = "InputFromUser";
    private static final String ASSET_INPUT_TYPE = "AssetOption";
    private static final String INPUT_FROM_USER_FIELD_TYPE = "text";
    private static final String CUSTOM_SERVICE_CATEGORY = "Custom Services";


    @PostConstruct
    public void init() {
        log.info("Initializing WorkflowServiceDescriptor");
    }

    @Autowired
    private OrchestrationWorkflowManager orchestrationWorkflowManager;

    public ServiceDescriptor getDescriptor(String serviceName) {
        log.debug("Getting workflow descriptor for {}", serviceName);
        List<CustomServicesWorkflow> results = orchestrationWorkflowManager.getByName(serviceName);
        if(null == results || results.isEmpty()) {
            return null;
        }
        if(results.size() > 1) {
            throw new IllegalStateException(String.format("Multiple workflows with the name %s", serviceName));
        }
        CustomServicesWorkflow orchestrationWorkflow = results.get(0);
        // Return service only if its PUBLISHED
        if (!OrchestrationWorkflowStatus.PUBLISHED.toString().equals(orchestrationWorkflow.getState())) {
            log.debug("Not returning workflow service because its state ({}) is not published", orchestrationWorkflow.getState());
            return null;
        }
        return mapWorkflowToServiceDescriptor(orchestrationWorkflow);
    }

    // This method will only return service descriptors for PUBLISHED workflwos
    public Collection<ServiceDescriptor> listDescriptors() {
        List<ServiceDescriptor> wfServiceDescriptors = new ArrayList<>();
        List<NamedElement> oeElements = orchestrationWorkflowManager.listByStatus(OrchestrationWorkflowStatus.PUBLISHED);
        if (null != oeElements) {
            CustomServicesWorkflow oeWorkflow;
            for(NamedElement oeElement: oeElements) {
                oeWorkflow = orchestrationWorkflowManager.getById(oeElement.getId());
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
			//TODO add enum. and need to fix COP-28181
			for (final Input wfInput : step.getInputGroups().get("input_params").getInputGroup()) {
                            String wfInputType = null;
                            // Creating service fields for only inputs of type "inputfromuser" and "assetoption"
                            if (INPUT_FROM_USER_INPUT_TYPE.equals(wfInput.getType())) {
                                wfInputType = INPUT_FROM_USER_FIELD_TYPE;
                            } else if (ASSET_INPUT_TYPE.equals(wfInput.getType())) {
                                wfInputType = wfInput.getValue();
                            }
                            if (null != wfInputType) {
                                ServiceField serviceField = new ServiceField();
                                String inputName = wfInput.getName();
                                //TODO: change this to get description
                                serviceField.setDescription(wfInput.getFriendlyName());
                                serviceField.setLabel(wfInput.getFriendlyName());
                                serviceField.setName(inputName);
                                serviceField.setRequired(wfInput.getRequired());
                                serviceField.setInitialValue(wfInput.getDefaultValue());
                                // Setting all unlocked fields as lockable
                                if (!wfInput.getLocked()) {
                                    serviceField.setLockable(true);
                                }
                                serviceField.setType(wfInputType);
                                to.getItems().put(inputName, serviceField);
                            }
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

