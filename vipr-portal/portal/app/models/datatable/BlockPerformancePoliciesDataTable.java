package models.datatable;

import java.net.URI;

import util.datatable.DataTable;

public class BlockPerformancePoliciesDataTable extends DataTable {

    public BlockPerformancePoliciesDataTable() {
        addColumn("name").setRenderFunction("renderLink");
        addColumn("description");
        sortAll();
        setDefaultSortField("name");
    }

    public static class BlockPerformancePoliciesModel {
        public URI id;
        public String name;
        public String description;
        public String autoTieringPolicyName;
        public Boolean compressionEnabled;
        public Boolean dedupCapable;
        public Boolean fastExpansion;
        public Integer hostIOLimitBandwidth;
        public Integer hostIOLimitIOPs;
        public Integer thinVolumePreAllocationPercentage;

        public BlockPerformancePoliciesModel(URI id, String name, String description,
                String autoTieringPolicyName, Boolean compressionEnabled, Boolean dedupCapable, Boolean fastExpansion,
                Integer hostIOLimitBandwidth, Integer hostIOLimitIOPs, Integer thinVolumePreAllocationPercentage) {
            super();
            this.id = id;
            this.name = name;
            this.description = description;
            this.autoTieringPolicyName = autoTieringPolicyName;
            this.compressionEnabled = compressionEnabled;
            this.dedupCapable = dedupCapable;
            this.fastExpansion = fastExpansion;
            this.hostIOLimitBandwidth = hostIOLimitBandwidth;
            this.hostIOLimitIOPs = hostIOLimitIOPs;
            this.thinVolumePreAllocationPercentage = thinVolumePreAllocationPercentage;
        }
    }

}