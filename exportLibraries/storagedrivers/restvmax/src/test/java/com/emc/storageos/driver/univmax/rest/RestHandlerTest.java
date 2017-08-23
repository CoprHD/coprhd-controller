/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.AuthenticationInfo;
import com.emc.storageos.driver.univmax.rest.exception.NoResourceFoundException;
import com.emc.storageos.driver.univmax.rest.type.common.GenericResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.AddInitiatorParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.EditHostActionParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.EditHostParamType;
import com.google.gson.reflect.TypeToken;

/**
 * @author fengs5
 *
 */
public class RestHandlerTest {

    private static final Logger log = LoggerFactory.getLogger(RestHandlerTest.class);
    static RestHandler restHandler;
    static AuthenticationInfo authenticationInfo;

    @BeforeClass
    public static void setup() {
        String protocol = "https";
        // String host = "lglw7150.lss.emc.com";
        String host = "lglw9079.lss.emc.com";// NDM
        int port = 8443;
        String user = "smc";
        String password = "smc";
        // String sn = "000196801468";
        String sn = "000197800372";

        authenticationInfo = new AuthenticationInfo(protocol, host, port, user, password);
        authenticationInfo.setSn(sn);
        RestClient client = new RestClient(authenticationInfo.getProtocol(), authenticationInfo.getHost(), authenticationInfo.getPort(),
                authenticationInfo.getUserName(),
                authenticationInfo.getPassword());
        restHandler = new RestHandler(client);
    }

    List<String> genUrlFillersWithSn(String... fillers) {
        List<String> urlFillers = UrlGenerator.genUrlFillers(fillers);
        urlFillers.add(0, authenticationInfo.getSn());
        return urlFillers;
    }

    // @Test
    // public void testGetSuccessful() {
    //
    // String hostId = "stone_test_host_IG_3098";
    // String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST_ID, genUrlFillersWithSn(hostId));
    // Type responseClazzType = new TypeToken<GetHostResultType>() {
    // }.getType();
    // ResponseWrapper<GetHostResultType> responseWrapper = restHandler.get(endPoint, responseClazzType);
    // log.info("ResponseBean as :{}", responseWrapper.getResponseBean());
    // Assert.assertEquals(200, responseWrapper.getResponseBean().getHttpCode());
    // Assert.assertNull(responseWrapper.getException());
    // Assert.assertEquals(hostId, responseWrapper.getResponseBean().getHost().get(0).getHostId());
    //
    // }
    //
    // @Test
    // public void testGetNoResourceFound() {
    // String hostId = "stone_test_host_IG_3098-1";
    // String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST_ID, genUrlFillersWithSn(hostId));
    // Type responseClazzType = new TypeToken<GetHostResultType>() {
    // }.getType();
    // ResponseWrapper<GetHostResultType> responseWrapper = restHandler.get(endPoint, responseClazzType);
    // log.info("ResponseBean as :{}", responseWrapper.getResponseBean());
    // Assert.assertNull(responseWrapper.getResponseBean());
    // Assert.assertNotNull(responseWrapper.getException());
    // Assert.assertEquals(NoResourceFoundException.class, responseWrapper.getException().getClass());
    // }
    // @Test
    // public void testGetWrongPathError() {
    // String hostId = "stone_test_host_IG_3098";
    // String endPoint = UrlGenerator.genUrl("/sloprovisioning/symmetrix/%s/host11/%s", genUrlFillersWithSn(hostId));
    // Type responseClazzType = new TypeToken<GetHostResultType>() {
    // }.getType();
    // ResponseWrapper<GetHostResultType> responseWrapper = restHandler.get(endPoint, responseClazzType);
    // log.info("ResponseBean as :{}", responseWrapper.getResponseBean());
    // Assert.assertNull(responseWrapper.getResponseBean());
    // Assert.assertNotNull(responseWrapper.getException());
    // Assert.assertEquals(ServerException.class, responseWrapper.getException().getClass());
    // }

    // @Test
    // public void testPostSuccessful() {
    // String hostId = "stone_test_IG_0823001";
    // List<String> initiators = new ArrayList<>();
    // initiators.add("1010756071879159");
    //
    // CreateHostParamType param = new CreateHostParamType(hostId);
    // param.setInitiatorId(initiators);
    // String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST, genUrlFillersWithSn());
    // Type responseClazzType = new TypeToken<GenericResultType>() {
    // }.getType();
    // ResponseWrapper<GenericResultType> responseWrapper = restHandler.post(endPoint, param, responseClazzType);
    // log.info("ResponseBean as :{}", responseWrapper.getResponseBean());
    // Assert.assertNull(responseWrapper.getException());
    // Assert.assertNotNull(responseWrapper.getResponseBean());
    // Assert.assertEquals(200, responseWrapper.getResponseBean().getHttpCode());
    // }
    //
    // @Test
    // public void testPostWrongPathError() {
    // String hostId = "stone_test_IG_0823002";
    // List<String> initiators = new ArrayList<>();
    // initiators.add("1010756071879160");
    //
    // CreateHostParamType param = new CreateHostParamType(hostId);
    // param.setInitiatorId(initiators);
    // String endPoint = UrlGenerator.genUrl("/sloprovisioning/symmetrix/%s/host111", genUrlFillersWithSn());
    // Type responseClazzType = new TypeToken<GenericResultType>() {
    // }.getType();
    // ResponseWrapper<GenericResultType> responseWrapper = restHandler.post(endPoint, param, responseClazzType);
    // log.info("ResponseBean as :{}", responseWrapper.getResponseBean());
    // Assert.assertNull(responseWrapper.getResponseBean());
    // Assert.assertNotNull(responseWrapper.getException());
    // Assert.assertEquals(ServerException.class, responseWrapper.getException().getClass());
    // }

    // @Test
    // public void testPostDuplicateHost() {
    // String hostId = "stone_test_IG_0823001";
    // List<String> initiators = new ArrayList<>();
    // initiators.add("1010756071879159");
    //
    // CreateHostParamType param = new CreateHostParamType(hostId);
    // param.setInitiatorId(initiators);
    // String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST, genUrlFillersWithSn());
    // Type responseClazzType = new TypeToken<GenericResultType>() {
    // }.getType();
    // ResponseWrapper<GenericResultType> responseWrapper = restHandler.post(endPoint, param, responseClazzType);
    // log.info("ResponseBean as :{}", responseWrapper.getResponseBean());
    // Assert.assertNull(responseWrapper.getResponseBean());
    // Assert.assertNotNull(responseWrapper.getException());
    // Assert.assertEquals(ServerException.class, responseWrapper.getException().getClass());
    // }
    //
    // @Test
    // public void testPostSameInitiatorError() {
    // String hostId = "stone_test_IG_0823002";
    // List<String> initiators = new ArrayList<>();
    // initiators.add("1010756071879159");
    //
    // CreateHostParamType param = new CreateHostParamType(hostId);
    // param.setInitiatorId(initiators);
    // String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST, genUrlFillersWithSn());
    // Type responseClazzType = new TypeToken<GenericResultType>() {
    // }.getType();
    // ResponseWrapper<GenericResultType> responseWrapper = restHandler.post(endPoint, param, responseClazzType);
    // log.info("ResponseBean as :{}", responseWrapper.getResponseBean());
    // Assert.assertNull(responseWrapper.getResponseBean());
    // Assert.assertNotNull(responseWrapper.getException());
    // Assert.assertEquals(ServerException.class, responseWrapper.getException().getClass());
    // }

    // @Test
    // public void testPutSuccessful() {
    //
    // String hostId = "stone_test_IG_0823004";
    // List<String> initiators = new ArrayList<>();
    // initiators.add("1010756071879162");
    //
    // CreateHostParamType param = new CreateHostParamType(hostId);
    // param.setInitiatorId(initiators);
    // String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST, genUrlFillersWithSn());
    // Type responseClazzType = new TypeToken<GenericResultType>() {
    // }.getType();
    // ResponseWrapper<GenericResultType> responseWrapper = restHandler.post(endPoint, param, responseClazzType);
    // log.info("ResponseBean as :{}", responseWrapper.getResponseBean());
    // Assert.assertNull(responseWrapper.getException());
    // Assert.assertNotNull(responseWrapper.getResponseBean());
    // Assert.assertEquals(200, responseWrapper.getResponseBean().getHttpCode());
    //
    // EditHostParamType editParam = new EditHostParamType();
    // EditHostActionParamType editHostActionParam = new EditHostActionParamType();
    // editParam.setEditHostActionParam(editHostActionParam);
    // initiators = new ArrayList<>();
    // initiators.add("1010756071879163");
    // AddInitiatorParamType addInitiatorParam = new AddInitiatorParamType(initiators);
    // editHostActionParam.setAddInitiatorParam(addInitiatorParam);
    // endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST_ID, genUrlFillersWithSn(hostId));
    // responseClazzType = new TypeToken<GenericResultType>() {
    // }.getType();
    // ResponseWrapper<GenericResultType> responseWrapper2 = restHandler.put(endPoint, editParam, responseClazzType);
    //
    // log.info("ResponseBean as :{}", responseWrapper2.getResponseBean());
    // Assert.assertNull(responseWrapper2.getException());
    // Assert.assertNotNull(responseWrapper2.getResponseBean());
    // Assert.assertEquals(200, responseWrapper2.getResponseBean().getHttpCode());
    // }

    // @Test
    // public void testPutError() {
    //
    // String hostId = "stone_test_IG_0823004";
    //
    // EditHostParamType editParam = new EditHostParamType();
    // EditHostActionParamType editHostActionParam = new EditHostActionParamType();
    // editParam.setEditHostActionParam(editHostActionParam);
    // List<String> initiators = new ArrayList<>();
    // initiators.add("1010756071879163");
    // AddInitiatorParamType addInitiatorParam = new AddInitiatorParamType(initiators);
    // editHostActionParam.setAddInitiatorParam(addInitiatorParam);
    // String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST_ID, genUrlFillersWithSn(hostId));
    // Type responseClazzType = new TypeToken<GenericResultType>() {
    // }.getType();
    // ResponseWrapper<GenericResultType> responseWrapper2 = restHandler.put(endPoint, editParam, responseClazzType);
    //
    // log.info("ResponseBean as :{}", responseWrapper2.getResponseBean());
    // Assert.assertNotNull(responseWrapper2.getException());
    // Assert.assertNull(responseWrapper2.getResponseBean());
    // Assert.assertEquals(ServerException.class, responseWrapper2.getException().getClass());
    // }

    @Test
    public void testPutNonexistError() {

        String hostId = "stone_test_IG_08230011";

        EditHostParamType editParam = new EditHostParamType();
        EditHostActionParamType editHostActionParam = new EditHostActionParamType();
        editParam.setEditHostActionParam(editHostActionParam);
        List<String> initiators = new ArrayList<>();
        initiators.add("1010756071879163");
        AddInitiatorParamType addInitiatorParam = new AddInitiatorParamType(initiators);
        editHostActionParam.setAddInitiatorParam(addInitiatorParam);
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST_ID, genUrlFillersWithSn(hostId));
        Type responseClazzType = new TypeToken<GenericResultType>() {
        }.getType();
        ResponseWrapper<GenericResultType> responseWrapper2 = restHandler.put(endPoint, editParam, responseClazzType);

        log.info("ResponseBean as :{}", responseWrapper2.getResponseBean());
        Assert.assertNotNull(responseWrapper2.getException());
        Assert.assertNull(responseWrapper2.getResponseBean());
        Assert.assertEquals(NoResourceFoundException.class, responseWrapper2.getException().getClass());
    }

    // @Test
    // public void testDeleteSuccessful() {
    // String hostId = "stone_test_IG_0823004";
    // String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST_ID, genUrlFillersWithSn(hostId));
    // Type responseClazzType = new TypeToken<GenericResultType>() {
    // }.getType();
    // ResponseWrapper<GenericResultType> responseWrapper = restHandler.delete(endPoint, responseClazzType);
    // log.info("ResponseBean as :{}", responseWrapper.getResponseBean());
    // Assert.assertNull(responseWrapper.getException());
    // Assert.assertNotNull(responseWrapper.getResponseBean());
    // Assert.assertEquals(204, responseWrapper.getResponseBean().getHttpCode());
    //
    // }
    @Test
    public void testDeleteNonexistResource() {
        String hostId = "stone_test_IG_0823004";
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST_ID, genUrlFillersWithSn(hostId));
        Type responseClazzType = new TypeToken<GenericResultType>() {
        }.getType();
        ResponseWrapper<GenericResultType> responseWrapper = restHandler.delete(endPoint, responseClazzType);
        log.info("ResponseBean as :{}", responseWrapper.getResponseBean());
        Assert.assertNotNull(responseWrapper.getException());
        Assert.assertNull(responseWrapper.getResponseBean());
        Assert.assertEquals(NoResourceFoundException.class, responseWrapper.getException().getClass());

    }

    // @Test
    // public void testGetAuthError() {
    // String hostId = "stone_test_host_IG_3098";
    // String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST_ID, genUrlFillersWithSn(hostId));
    // Type responseClazzType = new TypeToken<GetHostResultType>() {
    // }.getType();
    // ResponseWrapper<GetHostResultType> responseWrapper = restHandler.get(endPoint, responseClazzType);
    // log.info("ResponseBean as :{}", responseWrapper.getResponseBean());
    // log.info("Exception as :{}", responseWrapper.getException());
    // Assert.assertNull(responseWrapper.getResponseBean());
    // Assert.assertNotNull(responseWrapper.getException());
    // Assert.assertEquals(ClientException.class, responseWrapper.getException().getClass());
    // }

    // @Test
    // public void testGetWrongServer() {
    // String hostId = "stone_test_host_IG_3098";
    // String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST_ID, genUrlFillersWithSn(hostId));
    // Type responseClazzType = new TypeToken<GetHostResultType>() {
    // }.getType();
    // ResponseWrapper<GetHostResultType> responseWrapper = restHandler.get(endPoint, responseClazzType);
    // log.info("ResponseBean as :{}", responseWrapper.getResponseBean());
    // log.info("Exception as :{}", responseWrapper.getException());
    // Assert.assertNull(responseWrapper.getResponseBean());
    // Assert.assertNotNull(responseWrapper.getException());
    // }

}
