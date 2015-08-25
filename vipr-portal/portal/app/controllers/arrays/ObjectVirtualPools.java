package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ObjectVirtualPoolRestRep;
import com.google.common.collect.Lists;

import play.data.binding.As;
import play.mvc.With;
import util.VirtualPoolUtils;
import util.datatable.DataTablesSupport;
import controllers.Common;
import controllers.arrays.FileVirtualPools.DeactivateOperation;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;
import models.datatable.VirtualPoolDataTable;
import models.datatable.VirtualPoolDataTable.VirtualPoolInfo;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class ObjectVirtualPools extends ViprResourceController{
	protected static final String SAVED_SUCCESS = "VirtualPools.save.success";
	 protected static final String SAVED_ERROR = "VirtualPools.save.error";
	 protected static final String DELETED_SUCCESS = "VirtualPools.delete.success";
	 protected static final String DELETED_ERROR = "VirtualPools.delete.error";
	 protected static final String UNKNOWN = "VirtualPools.unknown";	 
	 
	 private static void backToReferrer() {
	        String referrer = Common.getReferrer();
	        if (StringUtils.isNotBlank(referrer)) {
	            redirect(referrer);
	        }
	        else {
	            list();
	        }
	    }

	    public static void list() {
	        VirtualPoolDataTable dataTable = createVirtualPoolDataTable();
	        render(dataTable);
	    }

	    private static VirtualPoolDataTable createVirtualPoolDataTable() {
	        VirtualPoolDataTable dataTable = new VirtualPoolDataTable();
	        dataTable.alterColumn("provisionedAs").hidden();
	        return dataTable;
	    }

	    public static void listJson() {
	        List<VirtualPoolInfo> items = Lists.newArrayList();
	        for (ObjectVirtualPoolRestRep virtualPool : VirtualPoolUtils.getObjectVirtualPools()) {
	            items.add(new VirtualPoolInfo(virtualPool));
	        }
	        renderJSON(DataTablesSupport.createJSON(items, params));
	    }

	    public static void create() {
	       list();
	    }

	    public static void duplicate(String ids) {
	       list();
	    }

	    public static void edit(String id) {
	       list();
	    }

//	    private static void edit(FileVirtualPoolForm vpool) {
////	        addStaticOptions();
////	        addDynamicOptions(vpool);
////	        renderArgs.put("storagePoolsDataTable", createStoragePoolDataTable());
////	        render("@edit", vpool);
//	    	list();
//	    }

	    public static void delete(@As(",") String[] ids) {
	        delete(uris(ids));
	    }

	    private static void delete(List<URI> ids) {
	        performSuccessFail(ids, new DeactivateOperation(), DELETED_SUCCESS, DELETED_ERROR);
	        backToReferrer();
	    }
}
