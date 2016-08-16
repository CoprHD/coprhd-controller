package controllers.catalog;

import java.util.List;

import models.datatable.DiagramDataTable;
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
	
	public static void diagrams(){
		TenantSelector.addRenderArgs();
		renderArgs.put("dataTable", diagramsDataTable);
		render();
	}
	public static void diagramsJson(){
		TenantSelector.addRenderArgs();
		List<String> diagrams = DiagramDataTable.fetch();
        renderJSON(DataTablesSupport.createJSON(diagrams, params));
	}
	
	
}
