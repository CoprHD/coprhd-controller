package org.camunda.bpm.example.modelapi;

import static org.camunda.bpm.model.bpmn.impl.BpmnModelConstants.BPMNDI_ELEMENT_BPMN_DIAGRAM;
import static org.camunda.bpm.model.bpmn.impl.BpmnModelConstants.BPMNDI_NS;
import static org.camunda.bpm.model.bpmn.impl.BpmnModelConstants.DI_ELEMENT_DIAGRAM_ELEMENT;
import static org.camunda.bpm.model.bpmn.impl.BpmnModelConstants.DI_NS;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.GatewayDirection;
import org.camunda.bpm.model.bpmn.builder.StartEventBuilder;
import org.camunda.bpm.model.bpmn.impl.instance.bpmndi.BpmnDiagramImpl;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnDiagram;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Diagram;
import org.camunda.bpm.model.bpmn.instance.di.DiagramElement;
import org.camunda.bpm.model.bpmn.instance.di.Edge;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.xml.sax.SAXException;

import com.emc.ctd.workflow.vipr.GenericWFInputParams;
import com.opensymphony.workflow.InvalidWorkflowDescriptorException;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.ResultDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;
import com.opensymphony.workflow.loader.WorkflowLoader;

public class OSworkflowModelAdaptorORG {
	
	private BpmnModelHelper bpmnModelHelper;

	public void initBpmnModel(){
		bpmnModelHelper = new BpmnModelHelper();
		bpmnModelHelper.createEmptyModel();
		
	}
	
	public void convertActionToBpmn(String resourcePath, boolean validate ) throws InvalidWorkflowDescriptorException, SAXException, IOException{
		
		InputStream is = OSworkflowModelAdaptorORG.class.getClassLoader().getResourceAsStream(resourcePath);


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
            
            StartEvent startEvent = createStartEvent(id,name, step);
            bpmnModelHelper.addChildElement(process, startEvent);

            EndEvent endEvent = createEndEvent(999,"End", -1);
            bpmnModelHelper.addChildElement(process, endEvent);

            bpmnModelHelper.addChildElement(process, endEvent);
            
            bpmnModelHelper.createSequenceFlow(process, startEvent, endEvent);
            
            Model model = bpmnModelHelper.modelInstance.getModel();
            
            Collection<ModelElementType> types = model.getTypes();
            
//            BpmnDiagram diagram = bpmnModelHelper.createElement(bpmnModelHelper.definitions, "BPMNDiagram_1", BpmnDiagram.class);
            
//            BpmnPlane plane = bpmnModelHelper.createElement(bpmnModelHelper.definitions, "BPMNPlane_1", BpmnPlane.class);
//            
//            bpmnModelHelper.addChildElement(diagram, plane);
            
            
            BpmnModelInstance modelInstance = bpmnModelHelper.modelInstance;
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
            startEventShape.setBpmnElement(startEvent);
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
            endEventShape.setBpmnElement(endEvent);
            processPlane.getDiagramElements().add(endEventShape);

            // create bounds for end event shape
            Bounds endEventBounds = modelInstance.newInstance(Bounds.class);
            endEventBounds.setHeight(36.0);
            endEventBounds.setWidth(36.0);
            endEventBounds.setX(718.0);
            endEventBounds.setY(312.0);
            endEventShape.setBounds(endEventBounds);


            
            for (ModelElementType type : types){
            	
            	
            }
            
            
            
            
            ModelBuilder bpmnModelBuilder = ModelBuilder.createInstance("BPMN Model");
            
            ModelElementTypeBuilder typeBuilder = bpmnModelBuilder.defineType(DiagramElement.class, DI_ELEMENT_DIAGRAM_ELEMENT).namespaceUri(DI_NS).abstractType();

            //            Class<? extends ModelElementInstance> instanceClass = (Class<? extends ModelElementInstance>) Class.forName("Edge");
            //            ModelElementType modelElementType = model.getType(instanceClass);

            typeBuilder = bpmnModelBuilder.defineType(BpmnDiagram.class, BPMNDI_ELEMENT_BPMN_DIAGRAM)
            	      .namespaceUri(BPMNDI_NS)
            	      .extendsType(Diagram.class)
            	      .instanceProvider(new ModelTypeInstanceProvider<BpmnDiagram>(){
            	        public BpmnDiagram newInstance(ModelTypeInstanceContext instanceContext) {
            	          return new BpmnDiagramImpl(instanceContext);
            	        }
            	      }); 
            ModelElementType dig = typeBuilder.build();
            
//            List<ModelElementType> types = dig.getAllChildElementTypes();
            
//            bpmnModelHelper.addChildElement(process, dig);
            
             System.out.println(types.toString());
            
            //            
//            Diagram diagram =  bpmnModelHelper.getElementByClass(Diagram.class);
//            
//            bpmnModelHelper.addChildElement(process, diagram);
            

            
        }
        
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
		
		OSworkflowModelAdaptorORG adaptor = new OSworkflowModelAdaptorORG();
		adaptor.initBpmnModel();
//	    Process process = createElement(definitions, "process-with-one-task", Process.class);
		adaptor.convertActionToBpmn("createExportVolumeSample.xml", true);
		
		
	}
}
