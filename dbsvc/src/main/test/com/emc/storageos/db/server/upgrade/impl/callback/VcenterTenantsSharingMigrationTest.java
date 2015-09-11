/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.NetworkVarrayIndexMigration;
import com.emc.storageos.db.client.upgrade.callbacks.VcenterTenantsSharingMigration;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * A migration test for the migrating the tenant information
 * to the vCenters acls and make the vCenter as a tenants sharable resources.
 *
 */
public class VcenterTenantsSharingMigrationTest extends DbSimpleMigrationTestBase {
    private Vcenter vcenter;
    private TenantOrg tenant;

    @BeforeClass
    public static void setup() throws IOException {

        customMigrationCallbacks.put("2.3", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;

            {
                add(new VcenterTenantsSharingMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#getSourceVersion()
     */
    @Override
    protected String getSourceVersion() {
        return "2.3";
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#getTargetVersion()
     */
    @Override
    protected String getTargetVersion() {
        return "2.4";
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#prepareData()
     */
    @Override
    protected void prepareData() {
        DbClient dbClient = getDbClient();

        tenant = new TenantOrg();
        tenant.setLabel("Tenant1");
        tenant.setId(URIUtil.createId(TenantOrg.class));
        dbClient.createObject(tenant);

        TenantOrg tenantObj = dbClient.queryObject(TenantOrg.class, tenant.getId());
        Assert.assertNotNull(tenantObj);
        Assert.assertNotNull(tenantObj.getId());

        vcenter = new Vcenter();
        vcenter.setLabel("Vcenter1");
        vcenter.setId(URIUtil.createId(Vcenter.class));
        vcenter.setTenant(tenantObj.getId());
        Assert.assertNotNull(vcenter.getTenant());

        dbClient.createObject(vcenter);
        Vcenter vcenterObj = dbClient.queryObject(Vcenter.class, vcenter.getId());
        Assert.assertNotNull(vcenterObj.getTenant());
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#verifyResults()
     */
    @Override
    protected void verifyResults() {
        DbClient dbClient = getDbClient();

        Vcenter vcenterObj = dbClient.queryObject(Vcenter.class, vcenter.getId());

        Assert.assertNotNull(vcenterObj);
        Assert.assertFalse(CollectionUtils.isEmpty(vcenterObj.getAcls()));
        Assert.assertTrue(vcenterObj.getTenantCreated());
        Assert.assertTrue(URIUtil.identical(NullColumnValueGetter.getNullURI(), vcenterObj.getTenant()));
        Assert.assertTrue(URIUtil.identical(tenant.getId(), BasePermissionsHelper.getTenant(vcenterObj.getAcls())));
        Assert.assertTrue(URIUtil.identical(tenant.getId(), vcenterObj.findVcenterTenant()));
    }
}
