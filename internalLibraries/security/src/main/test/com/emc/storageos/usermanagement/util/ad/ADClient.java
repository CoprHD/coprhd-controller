/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.util.ad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.directory.*;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.util.Hashtable;

public class ADClient {

    private static Logger logger = LoggerFactory.getLogger(ADClient.class);

    private LdapContext ctx;
    private String domainName;
    private String userOU = "users";

    public ADClient(String serverURL, String bindDN, String password, String domain) throws Exception {

        Hashtable env = new Hashtable();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, bindDN);
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put(Context.SECURITY_PROTOCOL, "ssl");
        env.put(Context.PROVIDER_URL, serverURL);
        env.put("java.naming.ldap.factory.socket", "com.emc.storageos.usermanagement.util.ad.MySSLSocketFactory");
        ctx = new InitialLdapContext(env, null);
        this.domainName = domain;
    }

    public void createGroup(String gName, String OU) throws Exception {

        Attributes attrs = new BasicAttributes(true);

        attrs.put("objectClass", "group");
        attrs.put("samAccountName", gName);
        attrs.put("cn", gName);
        attrs.put("description", "Added group" + gName);
        // group types
        int ADS_GROUP_TYPE_UNIVERSAL_GROUP = 0x0008;
        int ADS_GROUP_TYPE_SECURITY_ENABLED = 0x80000000;

        attrs.put("groupType", Integer.toString(ADS_GROUP_TYPE_UNIVERSAL_GROUP + ADS_GROUP_TYPE_SECURITY_ENABLED));
        String DN = getDN(gName, OU);
        ctx.createSubcontext(DN, attrs);

    }

    public void createUser(String userName, String passWord, String attributeKey, String attributeValue) throws Exception {

        int UF_PASSWD_CANT_CHANGE = 0x0040;
        int UF_NORMAL_ACCOUNT = 0x0200;
        int UF_DONT_EXPIRE_PASSWD = 0x10000;

        String DN = getDN(userName, userOU);

        Attributes attrs = new BasicAttributes(true);

        attrs.put("objectClass", "user");
        attrs.put("samAccountName", userName);
        attrs.put("cn", userName);
        attrs.put("userPrincipalName", userName + "@" + domainName);
        attrs.put("distinguishedName", DN);

        if (attributeKey != null && attributeValue != null) {
            attrs.put(attributeKey, attributeValue);
        }

        // Create user
        ctx.createSubcontext(DN, attrs);
        // Set password
        ModificationItem[] mods = new ModificationItem[2];
        String quotedPassword = "\"" + passWord + "\"";
        byte[] unicodePassword = quotedPassword.getBytes("UTF-16LE");

        mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("unicodePwd", unicodePassword));
        mods[1] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("userAccountControl",
                Integer.toString(UF_NORMAL_ACCOUNT + UF_PASSWD_CANT_CHANGE + UF_DONT_EXPIRE_PASSWD)));
        ctx.modifyAttributes(DN, mods);

        logger.info("created user in AD server: " + userName + "@" + domainName);
    }

    public void deleteUser(String userName) throws Exception {
        String DN = getDN(userName, userOU);
        ctx.destroySubcontext(DN);
        logger.info("delete user from AD server: " + userName + "@" + domainName);
    }

    public void addUserToGroup(String userDN, String groupDN) throws Exception {
        ModificationItem[] mods = new ModificationItem[1];
        mods[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE,
                new BasicAttribute("member", userDN));

        ctx.modifyAttributes(groupDN, mods);
    }

    public void removeUserFromGroup(String userDN, String groupDN) throws Exception {
        ModificationItem[] mods = new ModificationItem[1];
        mods[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                new BasicAttribute("member", userDN));

        ctx.modifyAttributes(groupDN, mods);
    }

    public String getDN(String userName, String OU) {
        String DN = "";
        String[] parts = domainName.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            DN = DN + ",DC=" + parts[i];
        }
        return "CN=" + userName + ",CN=" + OU + DN;

    }

    public String getDomainName() {
        return domainName;
    }

}
