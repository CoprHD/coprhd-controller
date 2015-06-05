import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

public class XMLUnMarshalTest {

	public static void main(String[] args) throws Exception {
		File f = new File("C:\\Users\\kanchg\\Desktop\\ViPR\\ExportUpdate.xml");

		 JAXBContext jaxbContext = JAXBContext.newInstance(FSExportUpdateParams.class);
		 Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		 FSExportUpdateParams menu =
		 (FSExportUpdateParams)jaxbUnmarshaller.unmarshal(f);
		 System.out.println(menu.getExportRulesToAdd().size());
//
//		 FSExportUpdateParams menu = new FSExportUpdateParams();
//		 ExportRules rules = new ExportRules();
//		 ExportRule rule = new ExportRule();
		 
//		 EndPoint1 point = new EndPoint1();
//		 point.setEndPoint("Test1");
//		 
//		 EndPoint1 point1 = new EndPoint1();
//		 point1.setEndPoint("Test2");
//		 
//		 List endpoints = new ArrayList();endpoints.add(point);endpoints.add(point1);
//		 
//		 rule.setReadOnlyHosts(endpoints);
//		 
//		 List exportRules = new ArrayList();exportRules.add(rule);exportRules.add(rule);
//		 
//		 rules.setExportRules(exportRules);
//		 menu.setExportRulesToAdd(exportRules);
		 
		 
		 ExportRule rule1 = new ExportRule();
		 ExportRule rule2 = new ExportRule();
		 ExportRule rule3 = new ExportRule();
		 
		 List<ExportRule> list = new ArrayList<>();
		 list.add(rule1);
		 list.add(rule2);
		 list.add(rule3);
		 
		 ExportRulesAPIResponseModel rules = new ExportRulesAPIResponseModel();
		 rules.setExportRules(list);
		 
		Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
		jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		jaxbMarshaller.marshal(rules, System.out);
	}
}


@XmlRootElement(name = "filesystem_export_update")
class FSExportUpdateParams {

	private List<ExportRules> exportRulesToAdd;
	private List<ExportRules> exportRulesToDelete;
	private List<ExportRules> exportRulesToModify;

	/**
	 * Default Constructor
	 */
	public FSExportUpdateParams() {
	}

	@XmlElement(name = "add", required = false)
	public List<ExportRules> getExportRulesToAdd() {
		exportRulesToAdd = isEmpty(exportRulesToAdd);
		return exportRulesToAdd;
	}

	/**
	 * List of exportRules to be added
	 * 
	 * @param exportRulesToAdd
	 */

	public void setExportRulesToAdd(List<ExportRules> exportRulesToAdd) {
		this.exportRulesToAdd = exportRulesToAdd;
	}

	@XmlElement(name = "delete", type = ExportRules.class)
	public List<ExportRules> getExportRulesToDelete() {
		exportRulesToAdd = isEmpty(exportRulesToAdd);
		return exportRulesToDelete;
	}

	/**
	 * List of exportRules to be deleted
	 * 
	 * @param exportRulesToAdd
	 */
	public void setExportRulesToDelete(List<ExportRules> exportRulesToDelete) {
		this.exportRulesToDelete = exportRulesToDelete;
	}

	@XmlElement(name = "modify", required = false)
	public List<ExportRules> getExportRulesToModify() {
		exportRulesToAdd = isEmpty(exportRulesToAdd);
		return exportRulesToModify;
	}

	/**
	 * List of exportRules to be modified
	 * 
	 * @param exportRulesToAdd
	 */
	public void setExportRulesToModify(List<ExportRules> exportRulesToModify) {
		this.exportRulesToModify = exportRulesToModify;
	}

	private List<ExportRules> isEmpty(List<ExportRules> type) {
		return (type != null) ? type : new ArrayList<ExportRules>();
	}

	@Override
	public String toString() {

		StringBuilder sb = new StringBuilder();
		sb.append("Add Requests : ").append(
				(exportRulesToAdd != null) ? exportRulesToAdd.size() : 0);
		sb.append("Delete Requests : ").append(
				(exportRulesToDelete != null) ? exportRulesToDelete.size() : 0);
		sb.append("Modify Requests : ").append(
				(exportRulesToModify != null) ? exportRulesToModify.size() : 0);

		return sb.toString();

	}

}
