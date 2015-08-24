package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ObjectVirtualPoolRestRep;
import com.google.common.collect.Lists;

import play.data.binding.As;
import play.i18n.Messages;
import play.mvc.With;
import util.MessagesUtils;
import util.VirtualPoolUtils;
import util.datatable.DataTablesSupport;
import controllers.Common;
import controllers.arrays.FileVirtualPools.DeactivateOperation;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.ViprResourceController;
import models.datatable.ObjectVpoolDataTable;
import models.datatable.ObjectVpoolDataTable.ObjectVirtualPoolInfo;
import models.virtualpool.FileVirtualPoolForm;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class ObjectVirtualPools extends ViprResourceController{
	protected static final String SAVED_SUCCESS = "VirtualPools.save.success";
	 protected static final String SAVED_ERROR = "VirtualPools.save.error";
	 protected static final String DELETED_SUCCESS = "VirtualPools.delete.success";
	 protected static final String DELETED_ERROR = "VirtualPools.delete.error";
	 protected static final String UNKNOWN = "VirtualPools.unknown";	 
	 
	public static void edit(String id) {
        
        list();
        //edit("test");
    }
	
	public static void create() {
       // edit("test");
        list();
    }
	public static void list() {
		ObjectVpoolDataTable dataTable = createVirtualPoolDataTable();
        render(dataTable);
    }
	private static ObjectVpoolDataTable createVirtualPoolDataTable() {
		ObjectVpoolDataTable dataTable = new ObjectVpoolDataTable();
        return dataTable;
    }

    public static void listJson() {
        List<ObjectVirtualPoolInfo> items = Lists.newArrayList();
        for (ObjectVirtualPoolRestRep virtualPool : VirtualPoolUtils.getObjectVirtualPools()) {
            items.add(new ObjectVirtualPoolInfo(virtualPool));
        }
        renderJSON(DataTablesSupport.createJSON(items, params));
    }
    
    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        performSuccessFail(ids, new DeactivateOperation(), DELETED_SUCCESS, DELETED_ERROR);
        list();
    }
    
    public static void duplicate(String ids) {
        FileVirtualPoolRestRep targetVpool = VirtualPoolUtils.getFileVirtualPool(ids);
        if (targetVpool == null) {
            flash.error(MessagesUtils.get(UNKNOWN, ids));
            list();
        }
        FileVirtualPoolForm copy = new FileVirtualPoolForm();
        copy.load(targetVpool);
        copy.id = null;
        copy.name = Messages.get("virtualPools.duplicate.name", copy.name);
        // Target VPool could have resources, set resources to 0 on the new Copy VPool so user can modify form
        copy.numResources = 0;
        list();
    }


}
