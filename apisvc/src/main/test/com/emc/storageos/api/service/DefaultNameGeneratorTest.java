/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service;

import com.emc.storageos.db.client.util.DefaultNameGenerator;
import org.junit.Assert;
import org.junit.Test;

public class DefaultNameGeneratorTest {

    @Test
    public void testTenantNameShorterThanResourceName() {
        String expected;
        String actual;
        DefaultNameGenerator nameGenerator = new DefaultNameGenerator();
        actual = nameGenerator.generate("GMC", "2012 Q3", "urn:storageos:Volume:d683ac10-5d2c-4462-9c79-6156cdaa74e7:", '_', 255);
        expected = "GMC_2012Q3_d683ac10-5d2c-4462-9c79-6156cdaa74e7";
        Assert.assertEquals(expected, actual);

        actual = nameGenerator.generate("GMC", " QuarterlyReport", "urn:storageos:TenantOrg:8d998de4-507d-448d-bbb6-6d4f3abe49b6:", '/', 255);
        expected = "GMC/QuarterlyReport/8d998de4-507d-448d-bbb6-6d4f3abe49b6";
        Assert.assertEquals(expected, actual);

        actual = nameGenerator.generate("GMC", " This Name *()&& Shouldn't Have Special Characters", "urn:storageos:TenantOrg:8d998de4-507d-448d-bbb6-6d4f3abe49b6:", '/', 255);
        expected = "GMC/ThisNameShouldntHaveSpecialCharacters/8d998de4-507d-448d-bbb6-6d4f3abe49b6";
        Assert.assertEquals(expected, actual);

        actual = nameGenerator.generate("GMC", "abcdefghijklmnopqrstuvwxyz", "urn:storageos:TenantOrg:8d998de4-507d-448d-bbb6-6d4f3abe49b6:", '_', 64);
        expected = "GMC_abcdefghijklmnopqrstuvw_8d998de4-507d-448d-bbb6-6d4f3abe49b6";
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTenantNameLongerThanResourceName() {
        String expected;
        String actual;
        DefaultNameGenerator nameGenerator = new DefaultNameGenerator();
        actual = nameGenerator.generate("tenant-abcdefghijklmnopqrstuvwxyz", "volume", "urn:storageos:TenantOrg:8d998de4-507d-448d-bbb6-6d4f3abe49b6:", '_', 64);
        expected = "tenantabcdefghijklmn_volume_8d998de4-507d-448d-bbb6-6d4f3abe49b6";
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTenantNameSameLengthAsResourceName() {
        String expected;
        String actual;
        DefaultNameGenerator nameGenerator = new DefaultNameGenerator();
        actual = nameGenerator.generate("tenant-abcdefghijklmnopqrstuvwxyz", "volume-abcdefghijklmnopqrstuvwxyz", "urn:storageos:TenantOrg:8d998de4-507d-448d-bbb6-6d4f3abe49b6:", '_', 64);
        expected = "tenantabcdefg_volumeabcdefg_8d998de4-507d-448d-bbb6-6d4f3abe49b6";
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTenantNameAndResourceNameLongerThanMaxSize() {
        String expected;
        String actual;
        DefaultNameGenerator nameGenerator = new DefaultNameGenerator();
        actual = nameGenerator.generate("tenant-abcdefghijklmnopqrstuvwxyz0000000000000000000000000000000000000000000", "MSTMT_I_1016_100_1694066731_347033891PI3_596075894_V725287082_23sadfadfasdfasdfadsfadfadsfasdfasdf", "urn:storageos:TenantOrg:8d998de4-507d-448d-bbb6-6d4f3abe49b6:", '_', 64);
        expected = "tenantabcdefg_MSTMTI1016100_8d998de4-507d-448d-bbb6-6d4f3abe49b6";
        Assert.assertEquals(expected, actual);

        actual = nameGenerator.generate("tenant-abcdefghijklmnopqrstuvwxyz0000000000000000000000000000000000000000000", "MSTMT_I_10", "urn:storageos:TenantOrg:8d998de4-507d-448d-bbb6-6d4f3abe49b6:", '_', 64);
        expected = "tenantabcdefg_MSTMTI10_8d998de4-507d-448d-bbb6-6d4f3abe49b6";
        Assert.assertEquals(expected, actual);

        actual = nameGenerator.generate("tenantA", "MSTMT_I_1016_100_1694066731_347033891PI3_596075894_V725287082_23sadfadfasdfasdfadsfadfadsfasdfasdf", "urn:storageos:TenantOrg:8d998de4-507d-448d-bbb6-6d4f3abe49b6:", '_', 64);
        expected = "tenantA_MSTMTI1016100_8d998de4-507d-448d-bbb6-6d4f3abe49b6";
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTenantNameLengthEqResourceNameLength() {
        String expected;
        String actual;
        DefaultNameGenerator nameGenerator = new DefaultNameGenerator();
        actual = nameGenerator.generate("ProviderTenant", "VNXBlockSanity", "urn:storageos:ExportGroup:8d998de4-507d-448d-bbb6-6d4f3abe49b6:", '_', 60);
        expected = "ProviderTen_VNXBlockSan_8d998de4-507d-448d-bbb6-6d4f3abe49b6";
        Assert.assertEquals(expected, actual);
        Assert.assertTrue(actual.length() <= 60);
    }
}
