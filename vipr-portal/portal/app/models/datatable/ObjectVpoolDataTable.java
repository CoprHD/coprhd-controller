package models.datatable;

import java.util.Collection;

import util.datatable.DataTable;

import com.emc.storageos.model.vpool.ObjectVirtualPoolRestRep;

public class ObjectVpoolDataTable extends DataTable {

	 public ObjectVpoolDataTable() {
	        addColumn("storageType").hidden();
	        addColumn("name");
	        addColumn("writeTraffic");
	        addColumn("readTraffic");
	        addColumn("userDataPendingRepl");
	        addColumn("metaDataPendingRepl");
	        addColumn("dataPendingXor");
	        setDefaultSort("name", "asc");
	    }

	    public static class ObjectVirtualPoolInfo {
	        public String id;
	        public String name;
	        public String writeTraffic;
	        public String readTraffic;
	        public String userDataPendingRepl;
	        public String metaDataPendingRepl;
	        public String dataPendingXor;
	       

	        public ObjectVirtualPoolInfo() {
	        }

	        public ObjectVirtualPoolInfo(ObjectVirtualPoolRestRep vsp) {
	            this.id = vsp.getId().toString();
	            this.name = vsp.getName();
	            this.writeTraffic = vsp.getWriteTraffic();
	            this.readTraffic = vsp.getReadTraffic();
	            this.userDataPendingRepl = vsp.getUserDataPendingRepl();
	            this.metaDataPendingRepl = vsp.getMetaDataPendingRepl();
	            this.dataPendingXor = vsp.getDataPendingXor();
	        }

	        private static int size(Collection<?> collection) {
	            return collection != null ? collection.size() : 0;
	        }

	        private int defaultInt(Integer value) {
	            return value != null ? value : 0;
	        }
	    }
}
