package controllers.catalog;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Scanner;

import models.datatable.DiagramDataTable;
import models.datatable.DiagramDataTable.Diagram;
import play.mvc.Controller;
import play.mvc.With;
import util.datatable.DataTablesSupport;
import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN") })
public class CustomWorkflow extends Controller {
	
	private static DiagramDataTable diagramsDataTable = new DiagramDataTable();

	public static void canvas() {
		TenantSelector.addRenderArgs();
		render();
	}
	
	public static void diagram(String fileName) {
		TenantSelector.addRenderArgs();
		//renderArgs.put("filePath", "C:\\Users\\hariks\\Desktop\\bpmn"+fileName);
		String content = "";
		try {
			content = new Scanner(new File("/opt/storageos/portal/conf/diagrams/"+fileName)).useDelimiter("\\Z").next();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		renderArgs.put("diagramXML", content);
		render();
	}
	
	public static void diagrams(){
		TenantSelector.addRenderArgs();
		renderArgs.put("dataTable", diagramsDataTable);
		render();
	}
	public static void diagramsJson(){
		TenantSelector.addRenderArgs();
		List<Diagram> diagrams = DiagramDataTable.fetch();
        renderJSON(DataTablesSupport.createJSON(diagrams, params));
	}
	
	public static void saveDiagram(String fileName, String xmlData){
		
		
			String a= fileName;
			String b = xmlData;
		
		diagrams();
		
	}
	
}
