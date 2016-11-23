package com.emc.sa.catalog;

import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.sa.descriptor.ServiceField;
import com.emc.sa.workflow.WorkflowHelper;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.OrchestrationWorkflow;
import com.emc.storageos.model.orchestration.OrchestrationWorkflowDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by balak1 on 11/22/2016.
 */
@Component
public class WorkflowServiceDescriptor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowServiceDescriptor.class);

    @PostConstruct
    public void init() {
        log.info("Initializing WorkflowServiceDescriptor");
    }

    @Autowired
    private OrchestrationWorkflowManager orchestrationWorkflowManager;

    public ServiceDescriptor getDescriptor(String serviceName) {
        List<OrchestrationWorkflow> results = orchestrationWorkflowManager.getByName(serviceName);
        if(null == results || results.isEmpty()) {
            return null;
        }
        if(results.size() > 1) {
            throw new IllegalStateException("Multiple workflows with the name " + serviceName);
        }
        return mapWorkflowToServiceDescriptor(results.get(0));
    }

    public OrchestrationWorkflowDocument getWorkflowDocument(String serviceId) throws IOException {
        List<OrchestrationWorkflow> results = orchestrationWorkflowManager.getByName(serviceId);
        if(null == results || results.isEmpty()) {
            return null;
        }
        if(results.size() > 1) {
            throw new IllegalStateException("Multiple workflows with the name " + serviceId);
        }
        return WorkflowHelper.toWorkflowDocument(results.get(0));
    }



    public Collection<ServiceDescriptor> listDescriptors() {
        log.info("createServiceDescriptorForWF start");
        List<ServiceDescriptor> wfServiceDescriptors = new ArrayList<>();
        List<NamedElement> oeElements = orchestrationWorkflowManager.list();
        //List<URI> oeWorkflowIDs = _dbClient.queryByType(OrchestrationWorkflow.class, true);

        OrchestrationWorkflow oeWorkflow;
        for(NamedElement oeElement: oeElements) {
            oeWorkflow = orchestrationWorkflowManager.getById(oeElement.getId());
            wfServiceDescriptors.add(mapWorkflowToServiceDescriptor(oeWorkflow));
        }

        log.info("createServiceDescriptorForWF end");
        return wfServiceDescriptors;
    }

    private ServiceDescriptor mapWorkflowToServiceDescriptor(OrchestrationWorkflow from) {
        ServiceDescriptor to = new ServiceDescriptor();
        try {
            log.info(from.getSteps());
            //final ObjectMapper MAPPER = new ObjectMapper();
            //MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            OrchestrationWorkflowDocument wfDocument = WorkflowHelper.toWorkflowDocument(from);
            //MAPPER.readValue(from.getSteps(), OrchestrationWorkflowDocument.class);
            to.setCategory("OrchestrationServices");
            to.setDescription(wfDocument.getDescription());
            to.setDestructive(false);
            to.setServiceId(wfDocument.getName());
            to.setTitle(wfDocument.getName());
            //to.setRoles(null);

            ServiceField serviceField = new ServiceField();

            String inputName;
            OrchestrationWorkflowDocument.Input wfInput;
            for (OrchestrationWorkflowDocument.Step step : wfDocument.getSteps()) {
                if (null != step.getInput()) {
                    for(Map.Entry<String, OrchestrationWorkflowDocument.Input> inputEntry: step.getInput().entrySet()) {
                        wfInput = inputEntry.getValue();
                        if ("InputFromUser".equals(wfInput.getType())) {
                            serviceField = new ServiceField();
                            inputName = inputEntry.getKey();
                            serviceField.setDescription(wfInput.getFriendlyName());
                            serviceField.setLabel(wfInput.getFriendlyName());
                            serviceField.setName(inputName);
                            serviceField.setRequired(wfInput.getRequired());
                            serviceField.setInitialValue(wfInput.getDefaultValue());
                            serviceField.setLockable(true);
                            serviceField.setType("text");
                            to.getItems().put(inputName, serviceField);
                        }
                        else if ("AssetOption".equals(wfInput.getType())) {
                            serviceField = new ServiceField();
                            inputName = inputEntry.getKey();
                            serviceField.setDescription(wfInput.getFriendlyName());
                            serviceField.setLabel(wfInput.getFriendlyName());
                            serviceField.setName(inputName);
                            serviceField.setRequired(wfInput.getRequired());
                            serviceField.setInitialValue(wfInput.getDefaultValue());
                            serviceField.setLockable(true);
                            serviceField.setType(wfInput.getValue());
                            to.getItems().put(inputName, serviceField);
                        }
                    }
                }

            }
        }
        catch (Exception e) {
            //TODO
            log.error("Hey! got an exception", e);
        }
        log.info("mapping done");
        return to;
    }
}
