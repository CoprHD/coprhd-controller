/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.setup;



import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.usermanagement.util.ad.ADClient;
import com.emc.storageos.usermanagement.util.ViPRClientHelper;
import com.emc.storageos.usermanagement.util.XmlUtil;
import com.emc.vipr.client.ViPRCoreClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;




import java.io.InputStream;
import java.util.Properties;

public class ADMode extends LocalUserMode {

    private static Logger logger = LoggerFactory.getLogger(ADMode.class);
    private static String adFile = "/lglw1197-AD.xml";
    private static boolean bAuthnProviderExisted;

    protected static AuthnProviderRestRep authnProviderRestRep;
    protected static ADClient adClient;
    protected static String PASSWORD = EnvConfig.get("sanity", "ad.manager.password");
    protected static String superUser;
    protected static String superUserPassword;
    protected static ViPRCoreClient superUserClient;

    @BeforeClass
    public synchronized static void setup_ADModeBaseClass() throws Exception {

        //get super user from parameter, better be AD user
        superUser = System.getProperty("SUPER_USER");
        superUserPassword = System.getProperty("SUPER_USER_PASSWORD");
        if(superUser == null || superUserPassword == null){
            Properties properties = new Properties();
            properties.load(ClassLoader.class.getResourceAsStream("/test-env.conf"));
            superUser = properties.getProperty("SUPER_USER");
            superUserPassword = properties.getProperty("SUPER_USER_PASSWORD");
        }
        logger.info("security admin: " + superUser + "/" + superUserPassword);
        superUserClient = new ViPRCoreClient(controllerNodeEndpoint, true)
                .withLogin(superUser, superUserPassword);

        ViPRClientHelper helper = new ViPRClientHelper(superUserClient);
        InputStream adFileInputStream = ClassLoader.class.getResourceAsStream(adFile);
        AuthnCreateParam input = XmlUtil.unmarshal(adFileInputStream, AuthnCreateParam.class);

        // for future cleanup, if not exit before the test.
        bAuthnProviderExisted = helper.isAuthnProviderExisted(input);

        // createAuthnProvider will skip creating one, if it already existed
        authnProviderRestRep = helper.createAuthnProvider(input);

        // construct ldapClient, which will be used for creating users on AD server.
        String serverUrl = (String)input.getServerUrls().toArray()[0];
        String domain = (String)input.getDomains().toArray()[0];
        adClient = new ADClient(serverUrl,
                input.getManagerDn(),
                input.getManagerPassword(),
                domain);

    }

    @AfterClass
    public synchronized static void teardown_ADModeBaseClass() throws Exception {
        adClient = null;

        if (!bAuthnProviderExisted) {
            superUserClient.authnProviders().delete(authnProviderRestRep.getId());
        }
    }
}
