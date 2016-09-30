package org.camunda.bpm.example.modelapi;


import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnDiagram;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaEntry;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaMap;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.xml.sax.SAXException;

import com.opensymphony.workflow.InvalidWorkflowDescriptorException;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.FunctionDescriptor;
import com.opensymphony.workflow.loader.ResultDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;
import com.opensymphony.workflow.loader.WorkflowLoader;

public class OSworkflowModelAdaptor {
	

	  
	private BpmnModelHelper bpmnModelHelper;

	public void initBpmnModel(){
		bpmnModelHelper = new BpmnModelHelper();
		bpmnModelHelper.createEmptyModel();
		
	}
	
	public void convertActionToBpmn(String resourcePath, boolean validate ) throws InvalidWorkflowDescriptorException, SAXException, IOException{
		
		InputStream is = OSworkflowModelAdaptor.class.getClassLoader().getResourceAsStream(resourcePath);


		WorkflowDescriptor descriptor = WorkflowLoader.load(is, validate);
/*
     <startEvent id="startEvent_0fcf6e07-510a-4f37-b5fe-f9a41edcf316" name="Start Workflow Init">
      <outgoing>sequenceFlow_d33fa5fd-e3d4-46c8-a1e8-346d1fb6ec32</outgoing>
    </startEvent>
 */
	    Process process = bpmnModelHelper.createElement(bpmnModelHelper.definitions, "CreateExportVolumeWorkflowProcess", Process.class);
		processWorkflowDescriptor(process,descriptor);

        
        Bpmn.writeModelToStream(System.out, bpmnModelHelper.modelInstance);
	}
	
	
	public void processWorkflowDescriptor(Process process,WorkflowDescriptor descriptor){
		
        for (Iterator iterator = descriptor.getInitialActions().iterator(); iterator.hasNext();) {
            ActionDescriptor actionDescriptor = (ActionDescriptor) iterator.next();
            int id =actionDescriptor.getId();
            String name = actionDescriptor.getName();
            ResultDescriptor result = actionDescriptor.getUnconditionalResult();
            int step = result.getStep();
            
            List<FunctionDescriptor> prefuncs =  (List<FunctionDescriptor>)actionDescriptor.getPreFunctions();
            
            for (FunctionDescriptor function : prefuncs){
                String type = function.getType();
                int idf =function.getId();

                Map args = new HashMap(function.getArgs());

//                for (Iterator iter = args.entrySet().iterator();
//                		iter.hasNext();) {
//                    Map.Entry mapEntry = (Map.Entry) iterator.next();
//                   // mapEntry.setValue(getConfiguration().getVariableResolver().translateVariables((String) mapEntry.getValue(), transientVars, ps));
//                }
            }
            
            
            actionDescriptor.getPostFunctions();
            
            actionDescriptor.getConditionalResults();
            
            
            StartEvent startEvent = createStartEvent(id,name, step);
            
            startEvent.addChildElement(createExtensionElements(null));
            
            
            bpmnModelHelper.addChildElement(process, startEvent);

            EndEvent endEvent = createEndEvent(999,"End", -1);
            bpmnModelHelper.addChildElement(process, endEvent);

            bpmnModelHelper.addChildElement(process, endEvent);
            
            SequenceFlow sequenceFlow = bpmnModelHelper.createSequenceFlow(process, startEvent, endEvent);
            
            Model model = bpmnModelHelper.modelInstance.getModel();
            
            Collection<ModelElementType> types = model.getTypes();
            
            
            
             
            
        }
        createBPMNDiagram(process, bpmnModelHelper.modelInstance);
        
	}
	
 
    /*
     			<function type="class">
					<arg name="class.name">com.opensymphony.workflow.util.Caller</arg>
				</function>
				<function type="spring">
					<arg name="bean.name">viprWorkflowService</arg>
					<arg name="WfInputParams">CreateExportVolume</arg>
					<arg name="WFinputParamsClass">com.emc.ctd.workflow.vipr.GenericWFInputParams</arg>
				</function>
     */
    
    
	
	  private ExtensionElements createExtensionElements(Map extElements) {
		  //   Collection<ExtensionElements> extensionElements = modelInstance.getModelElementsByType(ExtensionElements.class);
		  
		  ExtensionElements extensionElements =  bpmnModelHelper.getElementByClass(ExtensionElements.class);
		  
		  Map<String,String> elems = new HashMap<String,String>();
	      // Put elements to the map
		  elems.put("func:class", "com.opensymphony.workflow.util.Caller");
		  elems.put("func:spring", "viprWorkflowService");
		  elems.put("func:args", "CreateExportVolume");
		  elems.put("func:args", "com.emc.ctd.workflow.vipr.GenericWFInputParams");
	      


//	      CamundaMap map = bpmnModelHelper.getElementByClass(CamundaMap.class);
//
//	      
//	      
//	      for (Map.Entry<String, String> mapentry : elems.entrySet()) {
//		      CamundaEntry centry = bpmnModelHelper.getElementByClass(CamundaEntry.class);
//		      centry.setCamundaKey(mapentry.getKey());
//		      centry.setTextContent(mapentry.getValue());
//	    	  map.getCamundaEntries().add(centry);
//	      }
//	      CamundaEntry entry = bpmnModelHelper.getElementByClass(CamundaEntry.class);	      
//	      entry.setCamundaKey("test");
//	      entry.setTextContent("value");
//	      map.getCamundaEntries().add(entry);
		  
		  
		  CamundaProperties props = bpmnModelHelper.getElementByClass(CamundaProperties.class);
		  
		for (Map.Entry<String, String> mapentry : elems.entrySet()) {
			CamundaProperty prop = bpmnModelHelper.getElementByClass(CamundaProperty.class);
			prop.setCamundaName(mapentry.getKey());
			prop.setCamundaValue(mapentry.getValue());
			//prop.setCamundaId("1");
			

			props.getCamundaProperties().add(prop);
		}
	
	      
	      extensionElements.addChildElement(props);

	      
	  	for (Map.Entry<String, String> mapentry : elems.entrySet()) {
			CamundaProperty prop = bpmnModelHelper.getElementByClass(CamundaProperty.class);
			prop.setCamundaName(mapentry.getKey());
			prop.setCamundaValue(mapentry.getValue());
			//prop.setCamundaId("2");

			props.getCamundaProperties().add(prop);
		}
	  	
		  return extensionElements;
	}
	
	  private StartEvent createStartEvent(int id, String name, int step) {
		  
		  StartEvent startEvent =  bpmnModelHelper.getElementByClass(StartEvent.class);
		  startEvent.setId("startEvent_"+Integer.toString(id));
		  startEvent.setName(name);
		  return startEvent;
	}


		
	  private EndEvent createEndEvent(int id, String name, int step) {
		  
		  EndEvent endEvent =  bpmnModelHelper.getElementByClass(EndEvent.class);
		  endEvent.setId("endEvent_"+Integer.toString(id));
		  endEvent.setName(name);
		  return endEvent;
	}
		
		public void createBPMNDiagram(Process process,BpmnModelInstance modelInstance){
			
			List<StartEvent> startEvents = new ArrayList (modelInstance.getModelElementsByType(StartEvent.class) );
			List<EndEvent> endEvents = new ArrayList (modelInstance.getModelElementsByType(EndEvent.class) );
			List<SequenceFlow> sequenceFlows = new ArrayList (modelInstance.getModelElementsByType(SequenceFlow.class) );
	        // create bpmn diagram
	        BpmnDiagram bpmnDiagram = modelInstance.newInstance(BpmnDiagram.class);
	        bpmnDiagram.setId("diagram");
	        bpmnDiagram.setName("diagram");
	        bpmnDiagram.setDocumentation("bpmn diagram element");
	        bpmnDiagram.setResolution(120.0);
	        modelInstance.getDefinitions().addChildElement(bpmnDiagram);

	        // create plane for process
	        BpmnPlane processPlane = modelInstance.newInstance(BpmnPlane.class);
	        processPlane.setId("plane");
	        processPlane.setBpmnElement(process);
	        bpmnDiagram.setBpmnPlane(processPlane);

	        // create shape for start event
	        BpmnShape startEventShape = modelInstance.newInstance(BpmnShape.class);
	        startEventShape.setId("startShape");
	        startEventShape.setBpmnElement( startEvents.get(0));
	        processPlane.getDiagramElements().add(startEventShape);

	        // create bounds for start event shape
	        Bounds startEventBounds = modelInstance.newInstance(Bounds.class);
	        startEventBounds.setHeight(36.0);
	        startEventBounds.setWidth(36.0);
	        startEventBounds.setX(632.0);
	        startEventBounds.setY(312.0);
	        startEventShape.setBounds(startEventBounds);

	        // create shape for end event
	        BpmnShape endEventShape = modelInstance.newInstance(BpmnShape.class);
	        endEventShape.setId("endShape");
	        endEventShape.setBpmnElement(endEvents.get(0));
	        processPlane.getDiagramElements().add(endEventShape);

	        // create bounds for end event shape
	        Bounds endEventBounds = modelInstance.newInstance(Bounds.class);
	        endEventBounds.setHeight(36.0);
	        endEventBounds.setWidth(36.0);
	        endEventBounds.setX(718.0);
	        endEventBounds.setY(312.0);
	        endEventShape.setBounds(endEventBounds);

	        // create edge for sequence flow
	        BpmnEdge flowEdge = modelInstance.newInstance(BpmnEdge.class);
	        flowEdge.setId("flowEdge");
	        flowEdge.setBpmnElement(sequenceFlows.get(0));
	        flowEdge.setSourceElement(startEventShape);
	        flowEdge.setTargetElement(endEventShape);
	        processPlane.getDiagramElements().add(flowEdge);

	        // create waypoints for sequence flow edge
	        Waypoint startWaypoint = modelInstance.newInstance(Waypoint.class);
	        startWaypoint.setX(668.0);
	        startWaypoint.setY(330.0);
	        flowEdge.getWaypoints().add(startWaypoint);

	        Waypoint endWaypoint = modelInstance.newInstance(Waypoint.class);
	        endWaypoint.setX(718.0);
	        endWaypoint.setY(330.0);
	        flowEdge.getWaypoints().add(endWaypoint);

		}

	public void testCreateInvoiceProcess() throws Exception {
		    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("CreateExportVolume")
		      .name("Create Export Volume")
		      .startEvent().name("Start Workflow Init")
		      .userTask("task")
        .camundaInputParameter("foo", "bar")
        .camundaInputParameter("yoo", "hoo")
        .camundaOutputParameter("one", "two")
        .camundaOutputParameter("three", "four")
 		      
		      
//		        .name("Archive Invoice")
//		        .camundaClass("org.camunda.bpm.example.invoice.service.ArchiveInvoiceService")
 
//		      .userTask()
//		        .name("Assign Approver")
//		        .camundaAssignee("demo")
//		      .userTask()
//		        .id("approveInvoice")
//		        .name("Approve Invoice")
//		      .exclusiveGateway()
//		        .name("Invoice approved?")
//		        .gatewayDirection(GatewayDirection.Diverging)
//		      .condition("yes", "${approved}")
//		      .userTask()
//		        .name("Prepare Bank Transfer")
//		        .camundaFormKey("embedded:app:forms/prepare-bank-transfer.html")
//		        .camundaCandidateGroups("accounting")
//		      .serviceTask()
//		        .name("Archive Invoice")
//		        .camundaClass("org.camunda.bpm.example.invoice.service.ArchiveInvoiceService")
		      .endEvent()
		        .name("End Workflow Init")
//		      .moveToLastGateway()
//		      .condition("no", "${!approved}")
//		      .userTask()
//		        .name("Review Invoice")
//		        .camundaAssignee("demo")
//		      .exclusiveGateway()
//		        .name("Review successful?")
//		        .gatewayDirection(GatewayDirection.Diverging)
//		      .condition("no", "${!clarified}")
//		      .endEvent()
//		        .name("Invoice not processed")
//		      .moveToLastGateway()
//		      .condition("yes", "${clarified}")
//		      .connectTo("approveInvoice")
		      .done();

		      // deploy process model
//		      processEngine.getRepositoryService().createDeployment().addModelInstance("invoice.bpmn", modelInstance).deploy();
		       Bpmn.writeModelToStream(System.out, modelInstance);
		       
		  }

	
	public static void main(String[] args) throws Exception {
		
		
//		BpmnModelInstance modelInstance = Bpmn.readModelFromStream(OSworkflowModelAdaptor.class.getClassLoader().getResourceAsStream("process.bpmn"));
//		
//		System.out.println(modelInstance.getDocument().toString());
		
//		OSworkflowModelAdaptor adaptor = new OSworkflowModelAdaptor();
//		
//		adaptor.testCreateInvoiceProcess();
		
		OSworkflowModelAdaptor adaptor = new OSworkflowModelAdaptor();
		adaptor.initBpmnModel();
//	    Process process = createElement(definitions, "process-with-one-task", Process.class);
		adaptor.convertActionToBpmn("createExportVolumeSample.xml", true);
		
		
	}
}
