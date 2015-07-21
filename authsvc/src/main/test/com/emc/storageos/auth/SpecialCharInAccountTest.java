/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth;


import com.emc.storageos.auth.ldap.LdapFilterUtil;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpecialCharInAccountTest {

    @Test
    public void StringReplacementWith$In() {

        String username = "f$red@secqe.com";
        String filter1 = "userPrincipalName=%u";
        String filter2 = "uid=%U";
        String expected1 = "(&(userPrincipalName=f$red@secqe.com)(objectClass=person))";
        String expected2 = "(&(uid=f$red)(objectClass=person))";

        String result = LdapFilterUtil.getPersonFilterWithValues(filter1,username);
        Assert.assertTrue(result.equals(expected1));

        result = LdapFilterUtil.getPersonFilterWithValues(filter2, username);
        Assert.assertTrue(result.equals(expected2));
    }
}
