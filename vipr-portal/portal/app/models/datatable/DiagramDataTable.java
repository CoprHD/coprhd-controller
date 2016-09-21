package models.datatable;

import java.io.File;
import java.util.List;

import util.datatable.DataTable;

import com.google.common.collect.Lists;

public class DiagramDataTable extends DataTable  {

	private static final String DIAGRAM_PATH = "/opt/storageos/portal/conf/diagrams/";
	
	public DiagramDataTable(){
		addColumn("name").setRenderFunction("renderLink");;
		sortAll();
        setDefaultSort("name", "asc");
	}
	
	public static List<Diagram> fetch(){
		List<Diagram> diagrams = Lists.newArrayList();
		File folder = new File(DIAGRAM_PATH); 
		try {
			for (final File fileEntry : folder.listFiles()) {
			    if (fileEntry.isFile()) {
			    	diagrams.add(new Diagram(fileEntry.getName())); 
			    }
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return diagrams;
	}
	
	public static class Diagram{
		  public String name;
		  public String id;
		 
		  public Diagram(String name){
			  this.name = name;
			  this.id = name;
		  }
	 }
	
}
