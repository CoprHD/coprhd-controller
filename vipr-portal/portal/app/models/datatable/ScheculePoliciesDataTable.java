package models.datatable;

import util.datatable.DataTable;

import com.emc.storageos.model.schedulepolicy.SchedulePolicyRestRep;

public class ScheculePoliciesDataTable extends DataTable{
    
    public ScheculePoliciesDataTable() {
        addColumn("policyName").setRenderFunction("renderLink");
        addColumn("policyType");
        sortAll();
        setDefaultSortField("policyName");
    }

    public static class ScheculePolicy {
        public String id;
        public String policyName;
        public String policyType;

        public ScheculePolicy(SchedulePolicyRestRep policy) {
            id = policy.getPolicyId().toString();
            policyName = policy.getPolicyName();
            policyType = policy.getPolicyType();
        }
    }

}
