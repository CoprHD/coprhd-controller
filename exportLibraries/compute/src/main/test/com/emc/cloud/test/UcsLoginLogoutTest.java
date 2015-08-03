/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * 
 */
package com.emc.cloud.test;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import com.emc.cloud.platform.clientlib.ClientGeneralException;
import com.emc.cloud.platform.clientlib.ClientHttpMethods;
import com.emc.cloud.platform.clientlib.ClientHttpMethodsFactory;
import com.emc.cloud.platform.ucs.in.model.ConfigFindDnsByClassId;
import com.emc.cloud.platform.ucs.in.model.ConfigResolveDns;
import com.emc.cloud.platform.ucs.in.model.NamingClassId;
import com.emc.cloud.platform.ucs.in.model.ObjectFactory;
import com.emc.cloud.platform.ucs.out.model.ConfigSet;
import com.emc.cloud.platform.ucs.out.model.DnSet;
import com.emc.cloud.platform.ucs.out.model.DnSet.Dn;

/**
 * @author prabhj
 * 
 */

@ContextConfiguration(locations = { "classpath:applicationContext.xml" })
public class UcsLoginLogoutTest extends
        AbstractTestNGSpringContextTests {

    @Autowired
    ClientHttpMethodsFactory httpMethodsFactory;

    ClientHttpMethods clientHttpMethods;
    ObjectFactory factory = new ObjectFactory();

    @Test(groups = "runByDefault")
    public void testLogin() throws ClientGeneralException, MalformedURLException {

        clientHttpMethods = httpMethodsFactory.createClientHttpMethods(new URL("http", "10.247.84.170", 80, "/nuova").toString(),
                "ucs-glo\\prabhj", "Danger0us1");

    }

    private DnSet dnSet;

    @Test(groups = "runByDefault", dependsOnMethods = "testLogin")
    public void testGetBladeDNs() throws ClientGeneralException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        ConfigFindDnsByClassId findDnsByClassId = new ConfigFindDnsByClassId();
        findDnsByClassId.setClassId(NamingClassId.COMPUTE_ITEM);

        com.emc.cloud.platform.ucs.out.model.ConfigFindDnsByClassId configFindDnsByClassId = clientHttpMethods.postEntity(
                factory.createConfigFindDnsByClassId(findDnsByClassId), com.emc.cloud.platform.ucs.out.model.ConfigFindDnsByClassId.class);

        if (configFindDnsByClassId != null) {
            System.out.println(BeanUtils.describe(configFindDnsByClassId));
            if (configFindDnsByClassId.getContent() != null && !configFindDnsByClassId.getContent().isEmpty()) {

                for (Object object : configFindDnsByClassId.getContent()) {
                    if (object instanceof JAXBElement<?>) {
                        dnSet = ((JAXBElement<DnSet>) object).getValue();
                        if (dnSet != null) {
                            for (DnSet.Dn dn : dnSet.getDn()) {
                                System.out.println(BeanUtils.describe(dn));
                            }
                        }
                    }
                }
            }
        }

    }

    private ConfigSet configSet = null;

    @Test(groups = "runByDefault", dependsOnMethods = "testGetBladeDNs")
    public void getBlades() throws ClientGeneralException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        ConfigResolveDns configResolveDns = new ConfigResolveDns();
        configResolveDns.setInHierarchical("false");
        com.emc.cloud.platform.ucs.in.model.DnSet inDnSet = new com.emc.cloud.platform.ucs.in.model.DnSet();
        for (Dn dn : this.dnSet.getDn()) {
            com.emc.cloud.platform.ucs.in.model.DnSet.Dn inDn = new com.emc.cloud.platform.ucs.in.model.DnSet.Dn();
            inDn.setValue(dn.getValue());
            inDnSet.getDn().add(inDn);
        }

        configResolveDns.getContent().add(
                new JAXBElement<com.emc.cloud.platform.ucs.in.model.DnSet>(new QName("inDns"),
                        com.emc.cloud.platform.ucs.in.model.DnSet.class, inDnSet));

        com.emc.cloud.platform.ucs.out.model.ConfigResolveDns configResolveDnsOut = clientHttpMethods.postEntity(
                factory.createConfigResolveDns(configResolveDns), com.emc.cloud.platform.ucs.out.model.ConfigResolveDns.class);
        System.out.println(BeanUtils.describe(configResolveDnsOut));
        if (configResolveDnsOut.getContent() != null && !configResolveDnsOut.getContent().isEmpty()) {

            for (Object object : configResolveDnsOut.getContent()) {
                if (object instanceof JAXBElement<?>) {
                    if (!(((JAXBElement) object).getValue() instanceof ConfigSet)) {
                        continue;
                    }
                    configSet = ((JAXBElement<ConfigSet>) object).getValue();
                    if (configSet != null && configSet.getManagedObject() != null && !configSet.getManagedObject().isEmpty()) {
                        for (JAXBElement<?> managedObject : configSet.getManagedObject()) {
                            System.out.println("\t\t" + BeanUtils.describe(managedObject.getValue()));
                        }
                    }

                }
            }
        }

    }

    @Test(groups = "runByDefault", dependsOnMethods = "getBlades", ignoreMissingDependencies = true)
    public void testLogout() throws ClientGeneralException {

        httpMethodsFactory.closeClientHttpMethods(clientHttpMethods);
    }

}