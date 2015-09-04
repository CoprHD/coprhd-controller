/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 *  software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.auth;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.apache.commons.codec.binary.Base64;

import com.emc.storageos.auth.impl.CassandraTokenManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BaseToken;
import com.emc.storageos.db.client.model.ProxyToken;
import com.emc.storageos.db.client.model.RequestedTokenMap;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Token;
import com.emc.storageos.db.client.model.GenericSerializer;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.security.authentication.Base64TokenEncoder;
import com.emc.storageos.security.authentication.CassandraTokenValidator;
import com.emc.storageos.security.authentication.TokenKeyGenerator;
import com.emc.storageos.security.authentication.Base64TokenEncoder.SignedToken;
import com.emc.storageos.security.authentication.TokenKeyGenerator.TokenKeysBundle;
import com.emc.storageos.security.authentication.TokenMaxLifeValuesHolder;
import com.emc.storageos.security.authentication.TokenOnWire;
import com.emc.storageos.security.geo.InterVDCTokenCacheHelper;
import com.emc.storageos.security.geo.RequestedTokenHelper;
import com.emc.storageos.security.geo.RequestedTokenHelper.Operation;
import com.emc.storageos.security.geo.TokenResponseBuilder.TokenResponseArtifacts;
import com.emc.storageos.svcs.errorhandling.resources.UnauthorizedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map.Entry;

/**
 * Tests for TokenManager and TokenValidator
 */
public class TokenManagerTests extends DbsvcTestBase {

    private static final Logger log = LoggerFactory.getLogger(TokenManagerTests.class);

    private final CassandraTokenManager _tokenManager = new CassandraTokenManager();
    private final Base64TokenEncoder _encoder = new Base64TokenEncoder();
    private final TokenKeyGenerator _tokenKeyGenerator = new TokenKeyGenerator();
    private final DbClient _dbClient = getDbClient();
    private final CoordinatorClient _coordinator = new TestCoordinator();
    private final TokenMaxLifeValuesHolder _holder = new TokenMaxLifeValuesHolder();
    private GenericSerializer _serializer = new GenericSerializer();
    private final RequestedTokenHelper _requestedTokenMapHelper = new RequestedTokenHelper();

    // this method gets called at the beginning of each test that does not use multiple "nodes"
    private void commonDefaultSetupForSingleNodeTests() throws Exception {
        _holder.setMaxTokenIdleTimeInMins(2);
        _holder.setMaxTokenLifeTimeInMins(4);
        _holder.setTokenIdleTimeGraceInMins(1);
        _tokenManager.setTokenMaxLifeValuesHolder(_holder);
        _tokenManager.setDbClient(_dbClient);
        _tokenManager.setCoordinator(_coordinator);
        _requestedTokenMapHelper.setDbClient(_dbClient);
        _requestedTokenMapHelper.setCoordinator(_coordinator);
        _tokenManager.setTokenMapHelper(_requestedTokenMapHelper);
        _encoder.setCoordinator(_coordinator);
        _tokenKeyGenerator.setTokenMaxLifeValuesHolder(_holder);
        _encoder.setTokenKeyGenerator(_tokenKeyGenerator);
        _encoder.managerInit();
        _tokenManager.setTokenEncoder(_encoder);
    }

    /**
     * Delete all tokens for user1 before each test.
     */
    @Before
    public void cleanUpDb() {
        CassandraTokenManager tokenManagerCleanup = new CassandraTokenManager();
        tokenManagerCleanup.setDbClient(_dbClient);
        tokenManagerCleanup.setCoordinator(_coordinator);
        RequestedTokenHelper requestedTokenMapHelper = new RequestedTokenHelper();
        requestedTokenMapHelper.setDbClient(_dbClient);
        requestedTokenMapHelper.setCoordinator(_coordinator);
        tokenManagerCleanup.setTokenMapHelper(requestedTokenMapHelper);
        tokenManagerCleanup.deleteAllTokensForUser("user1", true);
    }

    /**
     * main set of tests for tokens
     */
    @Test
    public void testTokens() throws Exception {
        commonDefaultSetupForSingleNodeTests();

        // Test - new ticket issue
        StorageOSUserDAO userDAO = new StorageOSUserDAO();
        userDAO.setUserName("user1");
        userDAO.setIsLocal(true);

        long now = System.currentTimeMillis() / (60 * 1000);
        final String token = _tokenManager.getToken(userDAO);
        Assert.assertNotNull(token);
        TokenOnWire tw1 = _encoder.decode(token);

        Token tokenObj = _dbClient.queryObject(Token.class, tw1.getTokenId());
        Assert.assertNotNull(tokenObj);
        Assert.assertNotNull(tokenObj.getUserId());
        Assert.assertTrue(tokenObj.getExpirationTime() >= (now + 4));
        final URI userId = tokenObj.getUserId();
        // verify token
        StorageOSUserDAO gotUser = _tokenManager.validateToken(token);
        Assert.assertNotNull(gotUser);
        Assert.assertEquals(userId, gotUser.getId());

        // Test - update user info, reuse token
        StringSet groups = new StringSet();
        groups.add("gr1");
        groups.add("gr2");
        userDAO.setGroups(groups);
        StringSet attributes = new StringSet();
        attributes.add("atrr1");
        attributes.add("attr2");
        userDAO.setAttributes(attributes);

        String token2 = _tokenManager.getToken(userDAO);
        Assert.assertFalse(token.equals(token2)); // different tokens for same user record
        TokenOnWire tw2 = _encoder.decode(token2);
        Assert.assertFalse(tw1.getTokenId().equals(tw2.getTokenId()));
        tokenObj = _dbClient.queryObject(Token.class, tw2.getTokenId());
        Assert.assertNotNull(tokenObj);
        Assert.assertNotNull(tokenObj.getUserId());
        Assert.assertEquals(userId, tokenObj.getUserId());
        StorageOSUserDAO userInfo = _dbClient.queryObject(StorageOSUserDAO.class, userId);
        Assert.assertNotNull(userInfo);
        Assert.assertEquals(userId, userInfo.getId());
        Assert.assertFalse(userInfo.getInactive());
        Assert.assertEquals(groups.size(), userInfo.getGroups().size());
        Assert.assertEquals(attributes.size(), userInfo.getAttributes().size());
        Assert.assertTrue(userInfo.getIsLocal());
        // verify token
        gotUser = _tokenManager.validateToken(token2);
        Assert.assertNotNull(gotUser);
        Assert.assertEquals(userId, gotUser.getId());

        // Test - update user info, new token
        userDAO = new StorageOSUserDAO();
        userDAO.setUserName("user1");
        groups = new StringSet();
        groups.add("gr1");
        userDAO.setGroups(groups);
        attributes = new StringSet();
        attributes.add("atrr1");
        attributes.add("attr2");
        attributes.add("attr3");
        userDAO.setAttributes(attributes);

        // new token
        final String token3 = _tokenManager.getToken(userDAO);
        Assert.assertFalse(token2.equals(token3));
        TokenOnWire tw3 = _encoder.decode(token3);
        tokenObj = _dbClient.queryObject(Token.class, tw3.getTokenId());
        Assert.assertNotNull(tokenObj);
        Assert.assertNotNull(tokenObj.getUserId());
        Assert.assertEquals(userId, tokenObj.getUserId());
        userInfo = _dbClient.queryObject(StorageOSUserDAO.class, userId);
        Assert.assertNotNull(userInfo);
        Assert.assertEquals(userId, userInfo.getId());
        Assert.assertFalse(userInfo.getInactive());
        Assert.assertEquals(groups.size(), userInfo.getGroups().size());
        Assert.assertEquals(attributes.size(), userInfo.getAttributes().size());
        Assert.assertTrue(userInfo.getIsLocal());
        // verify token
        gotUser = _tokenManager.validateToken(token3);
        Assert.assertNotNull(gotUser);
        Assert.assertEquals(userId, gotUser.getId());

        // Test - idle time timeout
        tokenObj = _dbClient.queryObject(Token.class, tw1.getTokenId());
        // extend expiration by 10min, so that will not happen
        now = (System.currentTimeMillis() / (60 * 1000));
        tokenObj.setLastAccessTime(now);
        tokenObj.setExpirationTime(now + 5);
        _dbClient.persistObject(tokenObj);
        int count = 8;
        while (count-- > 0) {
            Thread.sleep(30 * 1000); // validate every 30 sec, for the next 4 min
            gotUser = _tokenManager.validateToken(token);
            Assert.assertNotNull(gotUser);
        }
        // set last access time back
        tokenObj = _dbClient.queryObject(Token.class, tw1.getTokenId());
        tokenObj.setLastAccessTime((System.currentTimeMillis() / (60 * 1000)) - 3);
        _dbClient.persistObject(tokenObj);
        // validate token on the old token - should fail
        gotUser = _tokenManager.validateToken(token);
        Assert.assertNull(gotUser);
        // token object should be deleted from db,
        // but user info should not be effected because we have another token pointing to it
        tokenObj = _dbClient.queryObject(Token.class, tw1.getTokenId());
        Assert.assertNull(tokenObj);
        userInfo = _dbClient.queryObject(StorageOSUserDAO.class, userId);
        Assert.assertNotNull(userInfo);
        Assert.assertFalse(userInfo.getInactive());

        // Test - deletion of token
        // should set userinfo inactive - because this is the last token pointing to it
        _tokenManager.deleteToken(token2);
        _tokenManager.deleteToken(token3);
        userInfo = _dbClient.queryObject(StorageOSUserDAO.class, userId);
        Assert.assertNotNull(userInfo);
        Assert.assertTrue(userInfo.getInactive());

        // Test - with inactive user info - new token request
        // new token and new user info created - with possible race condition to create more than one each
        int numThreads = 5;
        final List<String> tokens = Collections.synchronizedList(new ArrayList<String>());
        final List<URI> userIds = Collections.synchronizedList(new ArrayList<URI>());
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch wait = new CountDownLatch(numThreads);
        for (int index = 0; index < numThreads; index++) {
            executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    wait.countDown();
                    wait.await();
                    StorageOSUserDAO userDAO = new StorageOSUserDAO();
                    userDAO.setUserName("user1");
                    String token4 = _tokenManager.getToken(userDAO);
                    TokenOnWire tw4 = _encoder.decode(token4);
                    Assert.assertFalse(token3.equals(token4));
                    Assert.assertFalse(token.equals(token4));
                    Token tokenObj = _dbClient.queryObject(Token.class, tw4.getTokenId());
                    Assert.assertNotNull(tokenObj);
                    Assert.assertNotNull(tokenObj.getUserId());
                    Assert.assertFalse(userId.equals(tokenObj.getUserId()));
                    StorageOSUserDAO userInfo = _dbClient.queryObject(StorageOSUserDAO.class, tokenObj.getUserId());
                    Assert.assertNotNull(userInfo);
                    Assert.assertEquals(userDAO.getUserName(), userInfo.getUserName());
                    Assert.assertFalse(userInfo.getInactive());
                    Assert.assertFalse(userInfo.getIsLocal());
                    tokens.add(token4);
                    userIds.add(userInfo.getId());
                    return null;
                }
            });
        }
        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        Assert.assertTrue(tokens.size() >= 1);
        Assert.assertTrue(userIds.size() >= 1);

        // Test - delete all tokens
        _tokenManager.deleteAllTokensForUser(userDAO.getUserName(), true);
        List<URI> tokensURIs = new ArrayList<URI>();
        for (String rawToken : tokens) {
            tokensURIs.add(_encoder.decode(rawToken).getTokenId());
        }
        List<Token> allTokens = _dbClient.queryObject(Token.class, tokensURIs);
        Assert.assertTrue(allTokens.isEmpty());
        List<StorageOSUserDAO> users = _dbClient.queryObject(StorageOSUserDAO.class, userIds);
        for (StorageOSUserDAO user : users) {
            Assert.assertTrue(user.getInactive());
            URIQueryResultList tokensForUser = new URIQueryResultList();
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getUserIdTokenConstraint(userId), tokensForUser);
            Assert.assertFalse(tokensForUser.iterator().hasNext());
        }

        // Test - expired token deleting
        userDAO = new StorageOSUserDAO();
        userDAO.setUserName("user1");
        String dt1 = _tokenManager.getToken(userDAO);
        TokenOnWire twdt1 = _encoder.decode(dt1);
        tokenObj = _dbClient.queryObject(Token.class, twdt1.getTokenId());
        Assert.assertNotNull(tokenObj);
        Assert.assertNotNull(tokenObj.getUserId());
        URI du1 = tokenObj.getUserId();

        userDAO = new StorageOSUserDAO();
        userDAO.setUserName("user2");
        String dt2 = _tokenManager.getToken(userDAO);
        TokenOnWire twdt2 = _encoder.decode(dt2);
        tokenObj = _dbClient.queryObject(Token.class, twdt2.getTokenId());
        Assert.assertNotNull(tokenObj);
        Assert.assertNotNull(tokenObj.getUserId());
        URI du2 = tokenObj.getUserId();

        Thread.sleep(3 * 60 * 1000);
        _tokenManager.runCleanupNow();
        tokenObj = _dbClient.queryObject(Token.class, twdt1.getTokenId());
        Assert.assertNull(tokenObj);
        tokenObj = _dbClient.queryObject(Token.class, twdt2.getTokenId());
        Assert.assertNull(tokenObj);
        userDAO = _dbClient.queryObject(StorageOSUserDAO.class, du1);
        Assert.assertTrue(userDAO.getInactive());
        userDAO = _dbClient.queryObject(StorageOSUserDAO.class, du2);
        Assert.assertTrue(userDAO.getInactive());

        // test limits
        userDAO = new StorageOSUserDAO();
        userDAO.setUserName("user1");
        for (int i = 0; i < 100; i++) {
            dt1 = _tokenManager.getToken(userDAO);
            twdt1 = _encoder.decode(dt1);
            tokenObj = _dbClient.queryObject(Token.class, twdt1.getTokenId());
            Assert.assertNotNull(tokenObj);
            Assert.assertNotNull(tokenObj.getUserId());
        }
        // next get, will throw limit exception
        try {
            dt1 = _tokenManager.getToken(userDAO);
            Assert.fail("The token limit is exceeded. The token for user1 should not be generated.");
        } catch (UnauthorizedException ex) {
            // this exception is an expected one.
            Assert.assertTrue(true);
        }
    }

    /**
     * Token tests for verify and resolve
     */
    @Test
    public void testVerifyAndResolveTokens() throws Exception {
        commonDefaultSetupForSingleNodeTests();

        // Test - new ticket issue
        StorageOSUserDAO userDAO = new StorageOSUserDAO();
        userDAO.setUserName("root");
        userDAO.setIsLocal(true);
        StringSet groups = new StringSet();
        groups.add("gr1");
        groups.add("gr2");
        userDAO.setGroups(groups);
        StringSet attributes = new StringSet();
        attributes.add("atrr1");
        attributes.add("attr2");
        userDAO.setAttributes(attributes);

        final String token = _tokenManager.getToken(userDAO);
        Assert.assertNotNull(token);

        Token tokenVerif = (Token) _tokenManager.verifyToken(token);
        Assert.assertNotNull(tokenVerif);
        StorageOSUserDAO gotUser = _tokenManager.resolveUser(tokenVerif);
        Assert.assertTrue(gotUser.getIsLocal());
        Assert.assertEquals(userDAO.getUserName(), gotUser.getUserName());
        Assert.assertEquals(gotUser.getGroups().size(), groups.size());
        Assert.assertEquals(gotUser.getAttributes().size(), attributes.size());

        // Try with a non local user, make sure local flag is preserved
        StorageOSUserDAO userDAO2 = new StorageOSUserDAO();
        userDAO2.setUserName("user@domain.com");
        userDAO2.setIsLocal(false);
        final String token2 = _tokenManager.getToken(userDAO2);
        Assert.assertNotNull(token2);

        Token tokenVerif2 = (Token) _tokenManager.verifyToken(token2);
        Assert.assertNotNull(tokenVerif2);
        // make sure the is local flag checks out
        StorageOSUserDAO gotUser2 = _tokenManager.resolveUser(tokenVerif2);
        Assert.assertFalse(gotUser2.getIsLocal());

    }

    /**
     * Basic tests for proxy tokens
     */
    @Test
    public void testProxyTokens() throws Exception {
        commonDefaultSetupForSingleNodeTests();

        // Create a regular token
        StorageOSUserDAO userDAO = new StorageOSUserDAO();
        userDAO.setUserName("user111");
        userDAO.setIsLocal(true);
        userDAO.addAttribute("attr1=val1");
        userDAO.addGroup("group1");
        userDAO.setId((URIUtil.createId(StorageOSUserDAO.class)));
        _dbClient.persistObject(userDAO);
        final String token = _tokenManager.getToken(userDAO);
        Assert.assertNotNull(token);
        TokenOnWire tw = _encoder.decode(token);
        Token tokenObj = _dbClient.queryObject(Token.class, tw.getTokenId());
        Assert.assertNotNull(tokenObj);
        // Check that the system knows it as a non-proxy token
        Assert.assertFalse(BaseToken.isProxyToken(tokenObj));

        // Do the same with a proxy token
        final String proxyToken = _tokenManager.getProxyToken(userDAO);
        Assert.assertNotNull(proxyToken);
        TokenOnWire ptw = _encoder.decode(proxyToken);
        ProxyToken proxyTokenObj = _dbClient.queryObject(ProxyToken.class, ptw.getTokenId());
        Assert.assertNotNull(proxyTokenObj);
        Assert.assertTrue(BaseToken.isProxyToken(proxyTokenObj));
        Assert.assertTrue(ptw.isProxyToken());
        Assert.assertNotNull(proxyTokenObj.getLastKnownIds());

        // Check that user fetched from the id in the proxy token
        // matches the user's properties of the userDAO that created the proxytoken
        URI userId = proxyTokenObj.peekLastKnownId();
        StorageOSUserDAO userFromProxyToken = _dbClient.queryObject(StorageOSUserDAO.class, userId);
        Assert.assertNotNull(userFromProxyToken);
        Assert.assertEquals(userFromProxyToken.getUserName(), userDAO.getUserName());
        Assert.assertEquals(userFromProxyToken.getAttributes().size(), userDAO.getAttributes().size());
        Assert.assertTrue(userFromProxyToken.getAttributes().containsAll(userDAO.getAttributes()));
        Assert.assertEquals(userFromProxyToken.getGroups().size(), userDAO.getGroups().size());
        Assert.assertTrue(userFromProxyToken.getGroups().containsAll(userDAO.getGroups()));

        StorageOSUserDAO userFromProxyTokenValidation = _tokenManager.validateToken(proxyToken);
        Assert.assertNotNull(userFromProxyTokenValidation);
        Assert.assertEquals(userFromProxyTokenValidation.getUserName(), userDAO.getUserName());
        Assert.assertEquals(userFromProxyTokenValidation.getAttributes().size(), userDAO.getAttributes().size());
        Assert.assertTrue(userFromProxyTokenValidation.getAttributes().containsAll(userDAO.getAttributes()));
        Assert.assertEquals(userFromProxyTokenValidation.getGroups().size(), userDAO.getGroups().size());
        Assert.assertTrue(userFromProxyTokenValidation.getGroups().containsAll(userDAO.getGroups()));

        // Make sure that once a proxy token is created for a user, it gets reused from then on
        final String proxyToken2 = _tokenManager.getProxyToken(userDAO);
        Assert.assertNotNull(proxyToken2);
        Assert.assertEquals(proxyToken2, proxyToken);

        // simulate logout by deleting the authtoken that we created earlier
        _tokenManager.deleteToken(token.toString());
        StorageOSUserDAO deletedUser = _tokenManager.validateToken(token);
        Assert.assertNull(deletedUser);
        StorageOSUserDAO userIsStillThere = _tokenManager.validateToken(proxyToken2);
        Assert.assertNotNull(userIsStillThere);
        Assert.assertEquals(userIsStillThere.getUserName(), userDAO.getUserName());
        Assert.assertFalse(userIsStillThere.getInactive());

        // Relogin. Get new auth and proxy tokens. Force expiration of auth token.
        // Test that proxy token still works.
        StorageOSUserDAO userDAO2 = new StorageOSUserDAO();
        userDAO2.setUserName("user222");
        userDAO2.setIsLocal(true);
        userDAO2.setId((URIUtil.createId(StorageOSUserDAO.class)));
        _dbClient.persistObject(userDAO2);
        final String shortLivedTokenRaw = _tokenManager.getToken(userDAO2);
        Assert.assertNotNull(shortLivedTokenRaw);
        TokenOnWire sltw = _encoder.decode(shortLivedTokenRaw);
        final String proxyToken3 = _tokenManager.getProxyToken(userDAO2);
        Assert.assertNotNull(proxyToken3);
        Token shortLivedToken = _dbClient.queryObject(Token.class, sltw.getTokenId());
        Assert.assertNotNull(shortLivedToken);
        shortLivedToken.setLastAccessTime((System.currentTimeMillis() / (60 * 1000)) - 3);
        _dbClient.persistObject(shortLivedToken);
        // validate that auth token is gone
        deletedUser = _tokenManager.validateToken(shortLivedTokenRaw);
        Assert.assertNull(deletedUser);
        // validate that proxy token still works.
        userIsStillThere = _tokenManager.validateToken(proxyToken3);
        Assert.assertNotNull(userIsStillThere);
        Assert.assertEquals(userIsStillThere.getUserName(), userDAO2.getUserName());
        Assert.assertFalse(userIsStillThere.getInactive());

        // Test that after proxy token gets deleted, the userDao is gone or marked inactive
        // (its auth token has been expired above so proxy token was the last token)
        _tokenManager.deleteAllTokensForUser(userDAO2.getUserName(), true);
        StorageOSUserDAO inactiveUser = _dbClient.queryObject(StorageOSUserDAO.class, userDAO2.getId());
        Assert.assertTrue(inactiveUser == null || inactiveUser.getInactive() == true);

        // case sensitive username and proxy token optional deletion tests
        StorageOSUserDAO userDAOBlah = new StorageOSUserDAO();
        userDAOBlah.setUserName("user-blah");
        userDAOBlah.setIsLocal(true);
        final String blahToken = _tokenManager.getToken(userDAOBlah);
        Assert.assertNotNull(blahToken);
        TokenOnWire decoded = _encoder.decode(blahToken);
        final String blahProxyToken = _tokenManager.getProxyToken(userDAOBlah);
        Assert.assertNotNull(blahProxyToken);

        userDAOBlah = new StorageOSUserDAO();
        userDAOBlah.setUserName("User-Blah");
        userDAOBlah.setIsLocal(true);
        final String blahToken2 = _tokenManager.getToken(userDAOBlah);
        Assert.assertNotNull(blahToken2);
        TokenOnWire decoded2 = _encoder.decode(blahToken2);
        final String blahProxyToken2 = _tokenManager.getProxyToken(userDAOBlah);
        Assert.assertNotNull(blahProxyToken2);

        Token blahTokenObj = _dbClient.queryObject(Token.class, decoded.getTokenId());
        Token blahTokenObj2 = _dbClient.queryObject(Token.class, decoded2.getTokenId());
        Assert.assertEquals(blahTokenObj.getUserId(), blahTokenObj2.getUserId());
        Assert.assertEquals(blahProxyToken, blahProxyToken2);
        _tokenManager.deleteAllTokensForUser("user-BLAH", false);
        blahTokenObj = _dbClient.queryObject(Token.class, decoded.getTokenId());
        blahTokenObj2 = _dbClient.queryObject(Token.class, decoded2.getTokenId());
        Assert.assertNull(blahTokenObj);
        Assert.assertNull(blahTokenObj2);
        Assert.assertNull(_tokenManager.validateToken(blahToken));
        Assert.assertNotNull(_tokenManager.validateToken(blahProxyToken));
        _tokenManager.deleteAllTokensForUser("user-BLAH", true);
        Assert.assertNull(_tokenManager.validateToken(blahProxyToken));
    }

    /**
     * 
     * proxy token locking tests with multiple threads
     */
    @Test
    public void testTokenLocking() throws Exception {
        commonDefaultSetupForSingleNodeTests();

        // Mix 3 threads that get a proxy token for root, 3 threads that get a proxy token
        // for proxyuser, and 2 threads that delete for root, and 2 for proxuyuser.

        int numThreadsUserA = 3;
        int numThreadsUserB = 3;
        int numThreadsDeleteRoot = 2;
        int numThreadsDeleteProxyUser = 2;
        int totalThreads = numThreadsUserA + numThreadsUserB + numThreadsDeleteRoot + numThreadsDeleteProxyUser;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        final CountDownLatch waitA = new CountDownLatch(numThreadsUserA);
        for (int index = 0; index < numThreadsUserA; index++) {
            executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    waitA.countDown();
                    waitA.await();
                    StorageOSUserDAO userDAO = new StorageOSUserDAO();
                    userDAO.setUserName("userA");
                    userDAO.setIsLocal(true);
                    userDAO.setId((URIUtil.createId(StorageOSUserDAO.class)));
                    _dbClient.persistObject(userDAO);
                    final String proxyToken = _tokenManager.getProxyToken(userDAO);
                    Assert.assertNotNull(proxyToken);
                    return null;
                }
            });
        }
        final CountDownLatch waitB = new CountDownLatch(numThreadsUserB);
        for (int index = 0; index < numThreadsUserB; index++) {
            executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    waitB.countDown();
                    waitB.await();
                    StorageOSUserDAO userDAO = new StorageOSUserDAO();
                    userDAO.setUserName("userB");
                    userDAO.setIsLocal(true);
                    userDAO.setId((URIUtil.createId(StorageOSUserDAO.class)));
                    _dbClient.persistObject(userDAO);
                    final String proxyToken = _tokenManager.getProxyToken(userDAO);
                    Assert.assertNotNull(proxyToken);
                    return null;
                }
            });
        }

        final CountDownLatch waitC = new CountDownLatch(numThreadsDeleteRoot);
        for (int index = 0; index < numThreadsDeleteRoot; index++) {
            executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    waitC.countDown();
                    waitC.await();
                    StorageOSUserDAO userDAO = new StorageOSUserDAO();
                    userDAO.setUserName("userA");
                    _tokenManager.deleteAllTokensForUser(userDAO.getUserName(), true);
                    return null;
                }
            });
        }
        final CountDownLatch waitD = new CountDownLatch(numThreadsDeleteProxyUser);
        for (int index = 0; index < numThreadsDeleteProxyUser; index++) {
            executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    waitD.countDown();
                    waitD.await();
                    StorageOSUserDAO userDAO = new StorageOSUserDAO();
                    userDAO.setUserName("userB");
                    _tokenManager.deleteAllTokensForUser(userDAO.getUserName(), true);
                    return null;
                }
            });
        }

        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        Assert.assertTrue(getProxyTokenCountForUser("root") <= 1);
        Assert.assertTrue(getProxyTokenCountForUser("proxyuser") <= 1);

    }

    /**
     * Basic rotation functionality is tested here using overridden rotation interval values
     * 
     * @throws Exception
     */
    @Test
    public void testBasicTokenKeysRotation() throws Exception {
        TokenMaxLifeValuesHolder holder = new TokenMaxLifeValuesHolder();
        holder.setMaxTokenIdleTimeInMins(2);
        holder.setMaxTokenLifeTimeInMins(4);
        holder.setTokenIdleTimeGraceInMins(1);
        holder.setKeyRotationIntervalInMSecs(5000);
        CassandraTokenManager tokenManager = new CassandraTokenManager();
        Base64TokenEncoder encoder = new Base64TokenEncoder();
        TokenKeyGenerator tokenKeyGenerator = new TokenKeyGenerator();
        DbClient dbClient = getDbClient();
        CoordinatorClient coordinator = new TestCoordinator();
        tokenManager.setTokenMaxLifeValuesHolder(holder);
        tokenManager.setDbClient(dbClient);
        tokenManager.setCoordinator(coordinator);
        encoder.setCoordinator(coordinator);
        tokenKeyGenerator.setTokenMaxLifeValuesHolder(holder);
        encoder.setTokenKeyGenerator(tokenKeyGenerator);
        encoder.managerInit();
        tokenManager.setTokenEncoder(encoder);

        StorageOSUserDAO userDAO = new StorageOSUserDAO();
        userDAO.setUserName("user1");
        userDAO.setIsLocal(true);

        // get a regular token
        final String token = tokenManager.getToken(userDAO);
        Assert.assertNotNull(token);
        TokenOnWire tw1 = encoder.decode(token);
        Token tokenObj = dbClient.queryObject(Token.class, tw1.getTokenId());
        Assert.assertNotNull(tokenObj);
        // verify token
        StorageOSUserDAO gotUser = tokenManager.validateToken(token);
        Assert.assertNotNull(gotUser);

        // get a proxy token
        final String proxyToken = tokenManager.getProxyToken(gotUser);
        Assert.assertNotNull(proxyToken);

        // wait 6 seconds, this next token request will triggers a rotation
        Thread.sleep(6000);
        final String token2 = tokenManager.getToken(userDAO);
        Assert.assertNotNull(token2);

        // at this point, the first token should still be usable
        gotUser = tokenManager.validateToken(token);
        Assert.assertNotNull(gotUser);

        // wait another 6 seconds, trigger another rotation.
        Thread.sleep(6000);
        final String token3 = tokenManager.getToken(userDAO);
        Assert.assertNotNull(token3);

        // at this point, the first token should not be usable. The key it has been encoded with,
        // has been rotated out from the current, then previous spot. It is gone.
        try {
            gotUser = tokenManager.validateToken(token);
            Assert.fail("The token should not be usable.");

        } catch (UnauthorizedException ex) {
            // this exception is an expected one.
            Assert.assertTrue(true);
        }
        // after several rotations, proxy token should be unaffected
        gotUser = tokenManager.validateToken(proxyToken);
        Assert.assertNotNull(gotUser);
    }

    /**
     * tests for token signature manipulation
     * 
     * @throws Exception
     */
    @Test
    public void testTokenKeysSignature() throws Exception {
        commonDefaultSetupForSingleNodeTests();

        StorageOSUserDAO userDAO = new StorageOSUserDAO();
        userDAO.setUserName("user1");
        userDAO.setIsLocal(true);

        final String token = _tokenManager.getToken(userDAO);
        Assert.assertNotNull(token);
        TokenOnWire tw1 = _encoder.decode(token);
        // verify token
        StorageOSUserDAO gotUser = _tokenManager.validateToken(token);
        Assert.assertNotNull(gotUser);

        // base64 decode the token, just to look at the version field and
        // make sure it is set to what we think.
        byte[] decoded = Base64.decodeBase64(token.getBytes("UTF-8"));
        SignedToken stOffTheWire = (SignedToken) _serializer.fromByteArray(SignedToken.class, decoded);
        Assert.assertEquals(stOffTheWire.getTokenEncodingVersion(),
                Base64TokenEncoder.VIPR_ENCODING_VERSION);

        // Re-encode the valid token, using a bad signature. Try to validate that.
        byte[] reserialized = _serializer.toByteArray(TokenOnWire.class, tw1);
        SignedToken st = new SignedToken(reserialized, "badsignature");
        byte[] serializedSignedToken = _serializer.toByteArray(SignedToken.class, st);
        byte[] forgedToken = Base64.encodeBase64(serializedSignedToken);
        // Resulting token should fail validation even though the embedded token data is good
        try {
            gotUser = _tokenManager.validateToken(new String(forgedToken, "UTF-8"));
            Assert.fail("Resulting token should fail validation");
        } catch (UnauthorizedException ex) {
            // This is an expected exception
            Assert.assertTrue(true);
        }
        try {
            gotUser = _tokenManager.validateToken("somethingthatwontevendecode");
            Assert.fail("Arbitrary token should not be validated.");
        } catch (UnauthorizedException ex) {
            // This is an expected exception.
            Assert.assertTrue(true);
        }
    }

    /**
     * Tests out of sync cache behavior with multiple nodes.
     * 
     * @throws Exception
     */
    @Test
    public void testMultiNodesCacheUpdates() throws Exception {
        // For this test, we need our custom setup, with several
        // tokenManagers sharing a common TestCoordinator. This will
        // simulate shared zookeeper data on the cluster. And the different
        // tokenManagers/KeyGenerators will simulate the different nodes with
        // out of sync caches.
        final long ROTATION_INTERVAL_MSECS = 5000;
        DbClient dbClient = getDbClient();
        CoordinatorClient coordinator = new TestCoordinator();

        // Node 1
        CassandraTokenManager tokenManager1 = new CassandraTokenManager();
        Base64TokenEncoder encoder1 = new Base64TokenEncoder();
        TokenKeyGenerator tokenKeyGenerator1 = new TokenKeyGenerator();
        TokenMaxLifeValuesHolder holder1 = new TokenMaxLifeValuesHolder();
        holder1.setKeyRotationIntervalInMSecs(ROTATION_INTERVAL_MSECS); // means that once a token is created,
        // if the next token being requested happens 5 seconds later or more, the keys will
        // rotate. This is to test the built in logic that triggers rotation.
        tokenManager1.setTokenMaxLifeValuesHolder(holder1);
        tokenManager1.setDbClient(dbClient);
        tokenManager1.setCoordinator(coordinator);
        encoder1.setCoordinator(coordinator);
        tokenKeyGenerator1.setTokenMaxLifeValuesHolder(holder1);
        encoder1.setTokenKeyGenerator(tokenKeyGenerator1);
        encoder1.managerInit();
        tokenManager1.setTokenEncoder(encoder1);

        // Node 2
        CassandraTokenManager tokenManager2 = new CassandraTokenManager();
        Base64TokenEncoder encoder2 = new Base64TokenEncoder();
        TokenKeyGenerator tokenKeyGenerator2 = new TokenKeyGenerator();
        TokenMaxLifeValuesHolder holder2 = new TokenMaxLifeValuesHolder();
        holder2.setKeyRotationIntervalInMSecs(ROTATION_INTERVAL_MSECS);
        tokenManager2.setTokenMaxLifeValuesHolder(holder2);
        tokenManager2.setDbClient(dbClient);
        tokenManager2.setCoordinator(coordinator);
        encoder2.setCoordinator(coordinator);
        tokenKeyGenerator2.setTokenMaxLifeValuesHolder(holder2);
        encoder2.setTokenKeyGenerator(tokenKeyGenerator2);
        encoder2.managerInit();
        tokenManager2.setTokenEncoder(encoder2);

        // We do not need to use multi threads for these tests. We are using
        // a determined sequence of events to cause caches to be out of sync and
        // see how the keyGenerators react.

        // SCENARIO 1 -----------------------------------------------------------------
        // Cause a rotation on node1, then go with that token to node 2 to validate the
        // token. Node2 should update the cache automatically to find the new key and
        // validate the token successfully.
        resetCoordinatorData(coordinator, tokenManager1, tokenManager2, encoder1, encoder2,
                tokenKeyGenerator1, tokenKeyGenerator2);

        // cause the rotation
        Thread.sleep((ROTATION_INTERVAL_MSECS) + 1000);
        StorageOSUserDAO userDAO = new StorageOSUserDAO();
        userDAO.setUserName("user1");
        // get a new token from node 1 (it will be encoded with a new key)
        final String token3 = tokenManager1.getToken(userDAO);
        Assert.assertNotNull(token3);
        // validate it on node 2
        StorageOSUserDAO gotUser = tokenManager2.validateToken(token3);
        Assert.assertNotNull(gotUser);

        // SCENARIO 2 -----------------------------------------------------------------
        // Create a token with the current key on node 1. Cause 2 rotations on node1, then go with that
        // token to node 2 to validate. At that point, node 2 still has the token's key in cache. But
        // that key is now 2 rotations old and should not be accepted. We want to test that node 2
        // appropriately updates its cache, then refuses the key, rejects the token.

        // reset coordinator data, start from scratch with fresh keys.
        resetCoordinatorData(coordinator, tokenManager1, tokenManager2, encoder1, encoder2,
                tokenKeyGenerator1, tokenKeyGenerator2);

        final String token4 = tokenManager1.getToken(userDAO);
        Assert.assertNotNull(token4);
        Thread.sleep((ROTATION_INTERVAL_MSECS + 1000));
        final String token5 = tokenManager1.getToken(userDAO);
        Assert.assertNotNull(token5);
        Thread.sleep((ROTATION_INTERVAL_MSECS + 1000));
        final String token6 = tokenManager1.getToken(userDAO);
        Assert.assertNotNull(token6);
        try {
            gotUser = tokenManager2.validateToken(token4);
            Assert.fail("The token validation should fail because of the token rotation.");
        } catch (UnauthorizedException ex) {
            // This exception is an expected one.
            Assert.assertTrue(true);
        }

        // SCENARIO 3 -----------------------------------------------------------------
        // Cause a rotation on node 1. Then go to node 2 to get a new token. Node 2 should realize
        // that the key it is about to use for signing is not the latest and refresh its cache. It should
        // not however cause a rotation, because it already just happened.
        resetCoordinatorData(coordinator, tokenManager1, tokenManager2, encoder1, encoder2,
                tokenKeyGenerator1, tokenKeyGenerator2);

        // cause a rotation
        Thread.sleep((ROTATION_INTERVAL_MSECS + 1000));
        final String token7 = tokenManager1.getToken(userDAO);
        Assert.assertNotNull(token7);
        TokenOnWire tw7 = encoder1.decode(token7);
        String key7 = tw7.getEncryptionKeyId();
        final String token8 = tokenManager2.getToken(userDAO);
        Assert.assertNotNull(token8);
        TokenOnWire tw8 = encoder1.decode(token8);
        String key8 = tw8.getEncryptionKeyId();
        // see that the key id that was used to encode both tokens are the same.
        Assert.assertEquals(key7, key8);
    }

    /**
     * Utility class to hold key ids during multithreaded tests, used for checking
     * the presence of unique or non unique key ids
     */
    final class KeyIdsHolder {
        HashSet<String> _keys = new HashSet<String>();

        public synchronized void addToSet(String id) {
            _keys.add(id);
        }

        public int getSetSize() {
            return _keys.size();
        }
    }

    /**
     * Test that when 15 nodes launch their globalInit, at the end of the day,
     * there is one unique agreed upon current key id. This tests the locking in KeyGenerator.globalInit()
     * 
     * @throws Exception
     */
    @Test
    public void testMultiNodesGlobalInits() throws Exception {
        // For this test, we need our custom setup, with several
        // tokenManagers sharing a common TestCoordinator. This will
        // simulate shared zookeeper data on the cluster. And the different
        // tokenManagers/KeyGenerators will simulate the different nodes.
        DbClient dbClient = getDbClient();
        CoordinatorClient coordinator = new TestCoordinator();

        int numThreads = 15;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch waiter = new CountDownLatch(numThreads);

        final class InitTester implements Callable {

            CoordinatorClient _coordinator = null;
            DbClient _client = null;
            KeyIdsHolder _holder = null;

            public InitTester(CoordinatorClient coord, DbClient client, KeyIdsHolder holder) {
                _coordinator = coord;
                _client = client;
                _holder = holder;
            }

            @Override
            public Object call() throws Exception {
                // create node artifacts
                CassandraTokenManager tokenManager1 = new CassandraTokenManager();
                Base64TokenEncoder encoder1 = new Base64TokenEncoder();
                TokenKeyGenerator tokenKeyGenerator1 = new TokenKeyGenerator();
                tokenManager1.setDbClient(_client);
                tokenManager1.setCoordinator(_coordinator);
                TokenMaxLifeValuesHolder holder = new TokenMaxLifeValuesHolder();
                tokenManager1.setTokenMaxLifeValuesHolder(holder);
                encoder1.setCoordinator(_coordinator);
                tokenKeyGenerator1.setTokenMaxLifeValuesHolder(holder);
                encoder1.setTokenKeyGenerator(tokenKeyGenerator1);
                tokenManager1.setTokenEncoder(encoder1);

                // synchronize all threads
                waiter.countDown();
                waiter.await();

                // every thread calls init at the same time
                encoder1.managerInit();

                // then get a token and save the key for later
                StorageOSUserDAO userDAO = new StorageOSUserDAO();
                userDAO.setUserName("user1");
                final String token = tokenManager1.getToken(userDAO);
                Assert.assertNotNull(token);
                TokenOnWire tw = encoder1.decode(token);
                _holder.addToSet(tw.getEncryptionKeyId());
                return null;
            }

        }

        KeyIdsHolder holder = new KeyIdsHolder();
        for (int i = 0; i < numThreads; i++) {
            executor.submit(new InitTester(coordinator, dbClient, holder));
        }

        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

        // after all is said and done, all tokens created in all 15 threads, should have been
        // created with the same key id.
        Assert.assertEquals(1, holder.getSetSize());
    }

    /**
     * Have 15 threads attempt a rotation. 14 should realize that rotation have already happen
     * and not do anything. At the end, the current key id should be uniform across all 15 threads.
     * Additionally, the previous key id should still be valid.
     */
    @Test
    public void testConcurrentRotations() throws Exception {
        // For this test, we need our custom setup, with several
        // tokenManagers sharing a common TestCoordinator. This will
        // simulate shared zookeeper data on the cluster. And the different
        // tokenManagers/KeyGenerators will simulate the different nodes.
        DbClient dbClient = getDbClient();
        CoordinatorClient coordinator = new TestCoordinator();
        final int ROTATION_INTERVAL_MSECS = 5000;

        int numThreads = 15;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch waiter = new CountDownLatch(numThreads);

        final class InitTester implements Callable {

            CoordinatorClient _coordinator = null;
            DbClient _client = null;
            KeyIdsHolder _holder = null;

            public InitTester(CoordinatorClient coord, DbClient client, KeyIdsHolder holder) {
                _coordinator = coord;
                _client = client;
                _holder = holder;
            }

            @Override
            public Object call() throws Exception {
                // create node artifacts
                CassandraTokenManager tokenManager1 = new CassandraTokenManager();
                Base64TokenEncoder encoder1 = new Base64TokenEncoder();
                TokenKeyGenerator tokenKeyGenerator1 = new TokenKeyGenerator();
                tokenManager1.setDbClient(_client);
                tokenManager1.setCoordinator(_coordinator);
                encoder1.setCoordinator(_coordinator);
                tokenManager1.setTokenEncoder(encoder1);
                TokenMaxLifeValuesHolder holder = new TokenMaxLifeValuesHolder();
                tokenManager1.setTokenMaxLifeValuesHolder(holder);
                tokenKeyGenerator1.setTokenMaxLifeValuesHolder(holder);
                encoder1.setTokenKeyGenerator(tokenKeyGenerator1);
                holder.setKeyRotationIntervalInMSecs(ROTATION_INTERVAL_MSECS);
                encoder1.managerInit();

                // synchronize all threads
                waiter.countDown();
                waiter.await();

                // everybody gets a token using the key before the rotation
                StorageOSUserDAO userDAO = new StorageOSUserDAO();
                userDAO.setUserName("user1");
                final String token = tokenManager1.getToken(userDAO);
                Assert.assertNotNull(token);
                TokenOnWire tw = encoder1.decode(token);
                String previousKey = tw.getEncryptionKeyId();

                // cause the rotation
                Thread.sleep((ROTATION_INTERVAL_MSECS + 1000));

                final String token2 = tokenManager1.getToken(userDAO);
                Assert.assertNotNull(token2);
                TokenOnWire tw2 = encoder1.decode(token2);
                // save the new key in the set to check later that all threads agree
                // this is the new key
                _holder.addToSet(tw2.getEncryptionKeyId());

                // validate token created with the previous key to make sure
                // rotation didn't mess up the previous key
                StorageOSUserDAO gotUser = tokenManager1.validateToken(token);
                Assert.assertNotNull(gotUser);

                return null;
            }

        }

        KeyIdsHolder holder = new KeyIdsHolder();
        for (int i = 0; i < numThreads; i++) {
            executor.submit(new InitTester(coordinator, dbClient, holder));
        }

        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        // after all is said and done, all tokens created in all 15 threads, should have been
        // created with the same key id.
        Assert.assertEquals(1, holder.getSetSize());
    }

    /**
     * testConcurrentIntraVDCTokenCaching
     * Tests that multiple nodes in a single foreign VDC can cache the same token without collision
     * 
     * @throws Exception
     */
    @Test
    public void testConcurrentIntraVDCTokenCaching() throws Exception {
        // common setup and create a token
        commonDefaultSetupForSingleNodeTests();
        VirtualDataCenter localVdc = VdcUtil.getLocalVdc();
        localVdc.setShortId("externalVDCId");
        _dbClient.persistObject(localVdc);
        VdcUtil.invalidateVdcUrnCache();

        StorageOSUserDAO userDAO = new StorageOSUserDAO();
        userDAO.setUserName("user1@domain.com");
        userDAO.setIsLocal(false);
        String token = _tokenManager.getToken(userDAO);
        Assert.assertNotNull(token);
        TokenOnWire tw1 = _encoder.decode(token);
        final Token tokenObj = _dbClient.queryObject(Token.class, tw1.getTokenId());
        Assert.assertNotNull(tokenObj);
        URI userId = tokenObj.getUserId();
        Assert.assertNotNull(userId);
        final StorageOSUserDAO gotUser = _tokenManager.validateToken(token);
        Assert.assertNotNull(gotUser);

        // because we are running this on the same "db" as opposed to 2 different VDCs,
        // there will be a conflict when caching the token, since the original is already there
        // with the same id. So we are changing the token id and user record id for this
        // purpose.
        tokenObj.setId(URIUtil.createId(Token.class));
        gotUser.setId(URIUtil.createId(StorageOSUserDAO.class));
        tokenObj.setUserId(gotUser.getId());
        TokenOnWire tokenToBeCached = TokenOnWire.createTokenOnWire(tokenObj);
        // this re-encoded alternate token is the token that will be cached and validated
        // from cache.
        final String newEncoded = _encoder.encode(tokenToBeCached);

        final DbClient dbClient = getDbClient();

        // note: the same coordinator is being used in all threads. This means that
        // token keys will be present in this simulated foreign vdc eventhough we didn't
        // explicitly cache them. This should normally fail since we don't have the keys
        // but to focus this test on just the token validation from cache, we leave this be.
        // A separate test will deal with multiple TestCoordinator() representing different
        // zk, in other words true multiple VDCs.
        final CoordinatorClient coordinator = new TestCoordinator();

        // change it back to vdc1, so that it will not match the vdcid in the token
        // created earlier and therefore will be considered a foreign token.
        localVdc.setShortId("vdc1");
        _dbClient.persistObject(localVdc);
        VdcUtil.invalidateVdcUrnCache();

        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch waiter = new CountDownLatch(numThreads);

        final class InitTester implements Callable {
            @Override
            public Object call() throws Exception {

                // create node artifacts
                TokenMaxLifeValuesHolder holder = new TokenMaxLifeValuesHolder();
                holder.setForeignTokenCacheExpirationInMins(1);

                InterVDCTokenCacheHelper cacheHelper = new InterVDCTokenCacheHelper();
                cacheHelper.setCoordinator(coordinator);
                cacheHelper.setDbClient(dbClient);
                cacheHelper.setMaxLifeValuesHolder(holder);

                TokenKeyGenerator tokenKeyGenerator1 = new TokenKeyGenerator();
                tokenKeyGenerator1.setTokenMaxLifeValuesHolder(holder);

                Base64TokenEncoder encoder1 = new Base64TokenEncoder();
                encoder1.setCoordinator(coordinator);
                encoder1.setInterVDCTokenCacheHelper(cacheHelper);
                encoder1.setTokenKeyGenerator(tokenKeyGenerator1);
                encoder1.managerInit();

                CassandraTokenManager tokenManager1 = new CassandraTokenManager();
                tokenManager1.setDbClient(dbClient);
                tokenManager1.setCoordinator(coordinator);
                tokenManager1.setTokenMaxLifeValuesHolder(holder);
                tokenManager1.setInterVDCTokenCacheHelper(cacheHelper);
                tokenManager1.setTokenEncoder(encoder1);

                TokenResponseArtifacts artifacts = new TokenResponseArtifacts(gotUser, tokenObj, null);

                // synchronize all threads
                waiter.countDown();
                waiter.await();

                // Cache the token artifacts. Each thread will try at the same time
                // End result is, the token/user values will all be the same anyway
                // but the important is there is no concurrency issue between the first
                // thread that will try to add to the cache, and the others that will simply
                // update it.
                cacheHelper.cacheForeignTokenAndKeys(artifacts, null);

                // First validation should work. It validates from the cache.
                StorageOSUserDAO userFromDB = tokenManager1.validateToken(newEncoded);
                Assert.assertNotNull(userFromDB);
                Assert.assertEquals(userFromDB.getUserName(), gotUser.getUserName());

                // wait longer than cache expiration (longer than 1 minute in our case)
                // token's cache expiration should be expired
                Thread.sleep((holder.getForeignTokenCacheExpirationInMins() + 1) * 60000);
                userFromDB = tokenManager1.validateToken(newEncoded);
                Assert.assertNull(userFromDB);
                return null;
            }

        }

        for (int i = 0; i < numThreads; i++) {
            executor.submit(new InitTester());
        }

        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(180, TimeUnit.SECONDS));
    }

    /**
     * testCrossVDCTokenValidation
     * Tests that a token from VDC2 and VDC3 can both be validated in VDC1
     * given that VDC1's cache has these tokens and keys available.
     * 
     * @throws Exception
     */
    @Test
    public void testCrossVDCTokenValidation() throws Exception {
        commonDefaultSetupForSingleNodeTests();

        TokenMaxLifeValuesHolder holder = new TokenMaxLifeValuesHolder();
        // VDC1 (validator)
        CoordinatorClient coordinatorVDC1 = new TestCoordinator();
        InterVDCTokenCacheHelper cacheHelperVDC1 = new InterVDCTokenCacheHelper();
        cacheHelperVDC1.setCoordinator(coordinatorVDC1);
        cacheHelperVDC1.setDbClient(_dbClient);
        cacheHelperVDC1.setMaxLifeValuesHolder(holder);
        TokenKeyGenerator tokenKeyGeneratorVDC1 = new TokenKeyGenerator();
        tokenKeyGeneratorVDC1.setTokenMaxLifeValuesHolder(holder);
        Base64TokenEncoder encoderVDC1 = new Base64TokenEncoder();
        encoderVDC1.setCoordinator(coordinatorVDC1);
        encoderVDC1.setInterVDCTokenCacheHelper(cacheHelperVDC1);
        encoderVDC1.setTokenKeyGenerator(tokenKeyGeneratorVDC1);
        encoderVDC1.managerInit();
        CassandraTokenManager tokenManagerVDC1 = new CassandraTokenManager();
        tokenManagerVDC1.setDbClient(_dbClient);
        tokenManagerVDC1.setCoordinator(coordinatorVDC1);
        tokenManagerVDC1.setInterVDCTokenCacheHelper(cacheHelperVDC1);
        tokenManagerVDC1.setTokenEncoder(encoderVDC1);
        tokenManagerVDC1.setTokenMaxLifeValuesHolder(holder);

        // VDC2 (creator of token)
        CoordinatorClient coordinatorVDC2 = new TestCoordinator();
        TokenKeyGenerator tokenKeyGeneratorVDC2 = new TokenKeyGenerator();
        tokenKeyGeneratorVDC2.setTokenMaxLifeValuesHolder(holder);
        Base64TokenEncoder encoderVDC2 = new Base64TokenEncoder();
        encoderVDC2.setCoordinator(coordinatorVDC2);
        encoderVDC2.setTokenKeyGenerator(tokenKeyGeneratorVDC2);
        encoderVDC2.managerInit();
        CassandraTokenManager tokenManagerVDC2 = new CassandraTokenManager();
        tokenManagerVDC2.setDbClient(_dbClient);
        tokenManagerVDC2.setCoordinator(coordinatorVDC2);
        tokenManagerVDC2.setTokenEncoder(encoderVDC2);
        tokenManagerVDC2.setTokenMaxLifeValuesHolder(holder);

        // VDC3 (creator of token)
        CoordinatorClient coordinatorVDC3 = new TestCoordinator();
        TokenKeyGenerator tokenKeyGeneratorVDC3 = new TokenKeyGenerator();
        tokenKeyGeneratorVDC3.setTokenMaxLifeValuesHolder(holder);
        Base64TokenEncoder encoderVDC3 = new Base64TokenEncoder();
        encoderVDC3.setCoordinator(coordinatorVDC3);
        encoderVDC3.setTokenKeyGenerator(tokenKeyGeneratorVDC3);
        encoderVDC3.managerInit();
        CassandraTokenManager tokenManagerVDC3 = new CassandraTokenManager();
        tokenManagerVDC3.setDbClient(_dbClient);
        tokenManagerVDC3.setCoordinator(coordinatorVDC3);
        tokenManagerVDC3.setTokenEncoder(encoderVDC3);
        tokenManagerVDC3.setTokenMaxLifeValuesHolder(holder);

        // VDC2 create a token
        // set VdcUtil localvdcid to vdc2 to resulting token is identified as such
        VirtualDataCenter localVdc = VdcUtil.getLocalVdc();
        localVdc.setShortId("vdc2");
        _dbClient.persistObject(localVdc);
        VdcUtil.invalidateVdcUrnCache();
        StorageOSUserDAO userDAOVDC2 = new StorageOSUserDAO();
        userDAOVDC2.setUserName("user1@domain.com");
        userDAOVDC2.setIsLocal(false);
        String tokenVDC2 = tokenManagerVDC2.getToken(userDAOVDC2);
        Assert.assertNotNull(tokenVDC2);
        TokenOnWire twVDC2 = encoderVDC2.decode(tokenVDC2);
        final Token tokenObjVDC2 = _dbClient.queryObject(Token.class, twVDC2.getTokenId());
        Assert.assertNotNull(tokenObjVDC2);
        URI userIdVDC2 = tokenObjVDC2.getUserId();
        Assert.assertNotNull(userIdVDC2);
        final StorageOSUserDAO gotUserVDC2 = tokenManagerVDC2.validateToken(tokenVDC2);
        Assert.assertNotNull(gotUserVDC2);
        // because we are running this on the same "db" as opposed to 2 different VDCs,
        // there will be a conflict when caching the token, since the original is already there
        // with the same id. So we are changing the token id and user record id for this
        // purpose.
        tokenObjVDC2.setId(URIUtil.createId(Token.class));
        gotUserVDC2.setId(URIUtil.createId(StorageOSUserDAO.class));
        tokenObjVDC2.setUserId(gotUserVDC2.getId());
        TokenOnWire tokenToBeCachedVDC2 = TokenOnWire.createTokenOnWire(tokenObjVDC2);
        // this re-encoded alternate token is the token that will be cached and validated
        // from cache.
        final String newEncodedVDC2 = encoderVDC2.encode(tokenToBeCachedVDC2);

        // VDC3 create a token
        // set VdcUtil localvdcid to vdc3 to resulting token is identified as such
        localVdc.setShortId("vdc3");
        _dbClient.persistObject(localVdc);
        VdcUtil.invalidateVdcUrnCache();
        StorageOSUserDAO userDAOVDC3 = new StorageOSUserDAO();
        userDAOVDC3.setUserName("user2@domain.com");
        userDAOVDC3.setIsLocal(false);
        String tokenVDC3 = tokenManagerVDC3.getToken(userDAOVDC3);
        Assert.assertNotNull(tokenVDC3);
        TokenOnWire twVDC3 = encoderVDC3.decode(tokenVDC3);
        final Token tokenObjVDC3 = _dbClient.queryObject(Token.class, twVDC3.getTokenId());
        Assert.assertNotNull(tokenObjVDC3);
        URI userIdVDC3 = tokenObjVDC3.getUserId();
        Assert.assertNotNull(userIdVDC3);
        final StorageOSUserDAO gotUserVDC3 = tokenManagerVDC3.validateToken(tokenVDC3);
        Assert.assertNotNull(gotUserVDC3);
        tokenObjVDC3.setId(URIUtil.createId(Token.class));
        gotUserVDC3.setId(URIUtil.createId(StorageOSUserDAO.class));
        tokenObjVDC3.setUserId(gotUserVDC3.getId());
        TokenOnWire tokenToBeCachedVDC3 = TokenOnWire.createTokenOnWire(tokenObjVDC3);
        // this re-encoded alternate token is the token that will be cached and validated
        // from cache.
        final String newEncodedVDC3 = encoderVDC3.encode(tokenToBeCachedVDC3);

        // Cache VDC2 &3's tokens and keys in VDC1.cache
        TokenKeysBundle bundleVDC2 = tokenKeyGeneratorVDC2.readBundle();
        TokenKeysBundle bundleVDC3 = tokenKeyGeneratorVDC3.readBundle();
        TokenResponseArtifacts artifactsVDC2 = new TokenResponseArtifacts(gotUserVDC2, tokenObjVDC2, bundleVDC2);
        TokenResponseArtifacts artifactsVDC3 = new TokenResponseArtifacts(gotUserVDC3, tokenObjVDC3, bundleVDC3);

        cacheHelperVDC1.cacheForeignTokenAndKeys(artifactsVDC2, "vdc2");
        cacheHelperVDC1.cacheForeignTokenAndKeys(artifactsVDC3, "vdc3");

        Assert.assertEquals(2, cacheHelperVDC1.getAllCachedBundles().size());

        // Validate both tokens using VDC1
        // set VdcUtil localvdcid to vdc1 to resulting token is identified as such
        localVdc.setShortId("vdc1");
        _dbClient.persistObject(localVdc);
        VdcUtil.invalidateVdcUrnCache();
        StorageOSUserDAO userValidate = tokenManagerVDC1.validateToken(newEncodedVDC2);
        Assert.assertNotNull(userValidate);
        Assert.assertEquals(userValidate.getUserName(), userDAOVDC2.getUserName());
        StorageOSUserDAO userValidate2 = tokenManagerVDC1.validateToken(newEncodedVDC3);
        Assert.assertNotNull(userValidate2);
        Assert.assertEquals(userValidate2.getUserName(), userDAOVDC3.getUserName());

    }

    /**
     * Here, we test that in one node of a VDC (one cache), multiple threads
     * can add various tokenkeys bundle from 5 other vdcs at the same time
     * and the result is a consistent 5 entries in the cache
     * 
     * @throws Exception
     */
    @Test
    public void concurrentTokenKeyBundleMapUpdatesSingleCache() throws Exception {

        // Create 10 distinct bundles (recreating a new TestCoordinator each time
        // to simulate 10 vdcs
        final HashMap<String, TokenKeysBundle> verifyingMap = new HashMap<String, TokenKeysBundle>();

        for (int i = 0; i < 10; i++) {
            CoordinatorClient coordinator = new TestCoordinator();
            TokenMaxLifeValuesHolder holder = new TokenMaxLifeValuesHolder();
            TokenKeyGenerator tokenKeyGenerator1 = new TokenKeyGenerator();
            tokenKeyGenerator1.setTokenMaxLifeValuesHolder(holder);
            Base64TokenEncoder encoder1 = new Base64TokenEncoder();
            encoder1.setCoordinator(coordinator);
            encoder1.setTokenKeyGenerator(tokenKeyGenerator1);
            encoder1.managerInit();
            TokenKeysBundle bundle = tokenKeyGenerator1.readBundle();
            verifyingMap.put(String.format("vdc%d", i), bundle);
        }

        // 1 db, 1 coordinator, 1 cache. Shared across 10 threads
        // We are simulating the various services of a node all wanting to
        // cache the same stuff at the same time
        final DbClient sharedDbClient = getDbClient();
        final CoordinatorClient sharedCoordinator = new TestCoordinator();
        final InterVDCTokenCacheHelper sharedCacheHelper = new InterVDCTokenCacheHelper();
        sharedCacheHelper.setCoordinator(sharedCoordinator);
        sharedCacheHelper.setDbClient(sharedDbClient);
        TokenMaxLifeValuesHolder holder = new TokenMaxLifeValuesHolder();
        sharedCacheHelper.setMaxLifeValuesHolder(holder);

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch waiter = new CountDownLatch(numThreads);

        final class InitTester implements Callable {
            @Override
            public Object call() throws Exception {
                // synchronize all threads
                waiter.countDown();
                waiter.await();
                for (int i = 0; i < verifyingMap.size(); i++) {
                    String vdc = String.format("vdc%d", i);
                    TokenResponseArtifacts rspArtifacts = new TokenResponseArtifacts(null, null, verifyingMap.get(vdc));
                    sharedCacheHelper.cacheForeignTokenAndKeys(rspArtifacts, vdc);
                }
                return null;
            }
        }

        for (int i = 0; i < numThreads; i++) {
            executor.submit(new InitTester());
        }

        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        if (verifyingMap.size() != sharedCacheHelper.getAllCachedBundles().size()) {
            log.error("Mismatched cache and verifying map size: ");
            for (Entry<String, TokenKeysBundle> e : sharedCacheHelper.getAllCachedBundles().entrySet()) {
                log.error("vdc entry: {}", e.getKey());
            }
        }

        Assert.assertEquals(verifyingMap.size(), sharedCacheHelper.getAllCachedBundles().size());

        for (int i = 0; i < verifyingMap.size(); i++) {
            String vdc = String.format("vdc%d", i);
            TokenKeysBundle fromCache = sharedCacheHelper.getTokenKeysBundle(vdc);
            Assert.assertNotNull(fromCache);
            Assert.assertTrue(fromCache.getKeyEntries().size() == verifyingMap.get(vdc).getKeyEntries().size() &&
                    fromCache.getKeyEntries().get(0).equals(verifyingMap.get(vdc).getKeyEntries().get(0)));
        }

    }

    /**
     * This test updates two objects in the RequestedTokenMap CF. One for token1,
     * one for token2. Each one is initially loaded with 10 entries:
     * token1: vdc1, vdc2, vdc3 ...
     * token2: vdc1, vdc2, vdc3 ...
     * 10 adder threads are adding 10 more entries to each token. vdc-11, vdc-12 ...
     * 10 remover threads are removing the 10 entries created initially
     * The result is each token should end up with 10 entries, from the adder threads.
     * 
     * @throws Exception
     */
    @Test
    public void concurrentRequestedTokenMapUpdates() throws Exception {

        final DbClient sharedDbClient = getDbClient();
        final CoordinatorClient sharedCoordinator = new TestCoordinator();
        final RequestedTokenHelper requestedTokenMap = new RequestedTokenHelper();
        requestedTokenMap.setDbClient(sharedDbClient);
        requestedTokenMap.setCoordinator(sharedCoordinator);

        // pre load the map with 10 entries for a given token
        for (int i = 0; i < 10; i++) {
            requestedTokenMap.addOrRemoveRequestingVDC(Operation.ADD_VDC, "token1",
                    String.format("vdc%d", i));
        }
        // pre load the map with 10 entries for another token
        for (int i = 0; i < 10; i++) {
            requestedTokenMap.addOrRemoveRequestingVDC(Operation.ADD_VDC, "token2",
                    String.format("vdc%d", i));
        }

        int numAdderThreads = 10;
        int numRemoverThreads = 10;
        int numThreads = numAdderThreads + numRemoverThreads;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch waiter = new CountDownLatch(numThreads);

        final class Adders implements Callable {
            @Override
            public Object call() throws Exception {
                // synchronize all threads
                waiter.countDown();
                waiter.await();
                for (int i = 0; i < 10; i++) {
                    requestedTokenMap.addOrRemoveRequestingVDC(Operation.ADD_VDC, "token1",
                            String.format("vdc-1%d", i));
                }
                for (int i = 0; i < 10; i++) {
                    requestedTokenMap.addOrRemoveRequestingVDC(Operation.ADD_VDC, "token2",
                            String.format("vdc-2%d", i));
                }
                return null;
            }
        }

        final class Removers implements Callable {
            @Override
            public Object call() throws Exception {
                // synchronize all threads
                waiter.countDown();
                waiter.await();
                // pre load the map with 10 entries for a given token
                for (int i = 0; i < 10; i++) {
                    requestedTokenMap.addOrRemoveRequestingVDC(Operation.REMOVE_VDC, "token1",
                            String.format("vdc%d", i));
                }
                // pre load the map with 10 entries for another token
                for (int i = 0; i < 10; i++) {
                    requestedTokenMap.addOrRemoveRequestingVDC(Operation.REMOVE_VDC, "token2",
                            String.format("vdc%d", i));
                }

                return null;
            }
        }

        for (int i = 0; i < numAdderThreads; i++) {
            executor.submit(new Adders());
        }

        for (int i = 0; i < numRemoverThreads; i++) {
            executor.submit(new Removers());
        }

        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

        // Verification. We should be left with just 10 entries for each token
        // The 10 entries that were added by the adders.
        RequestedTokenMap mapToken1 = requestedTokenMap.getTokenMap("token1");
        Assert.assertEquals(10, mapToken1.getVDCIDs().size());
        StringSet entries = mapToken1.getVDCIDs();
        ArrayList<String> checkList = new ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            checkList.add(String.format("vdc-1%d", i));
        }
        Assert.assertTrue(entries.containsAll(checkList));
        RequestedTokenMap mapToken2 = requestedTokenMap.getTokenMap("token2");
        Assert.assertEquals(10, mapToken2.getVDCIDs().size());
        StringSet entries2 = mapToken2.getVDCIDs();
        ArrayList<String> checkList2 = new ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            checkList2.add(String.format("vdc-2%d", i));
        }
        Assert.assertTrue(entries2.containsAll(checkList2));
    }

    /**
     * This test checks that when the TokenManager's cleanup thread is called,
     * it deletes not only expired tokens but also their related RequestedTokenMap
     * entry if it exists (and doesn't crash if there isn't one).
     */
    @Test
    public void testRequestedTokenMapCleanup() throws Exception {
        commonDefaultSetupForSingleNodeTests();

        // create a token
        StorageOSUserDAO userDAO = new StorageOSUserDAO();
        userDAO.setUserName("user1");
        userDAO.setIsLocal(true);

        final String token = _tokenManager.getToken(userDAO);
        Assert.assertNotNull(token);
        TokenOnWire tw1 = _encoder.decode(token);

        Token tokenObj = _dbClient.queryObject(Token.class, tw1.getTokenId());
        Assert.assertNotNull(tokenObj);
        RequestedTokenMap map = new RequestedTokenMap(); // add a requested map for this token
        map.setId(URIUtil.createId(RequestedTokenMap.class));
        map.setTokenID(tokenObj.getId().toString());
        map.addVDCID("vdc1");
        _dbClient.persistObject(map);

        // create a second token, no requested map entry this time.
        final String token2 = _tokenManager.getToken(userDAO);
        Assert.assertNotNull(token2);
        TokenOnWire tw2 = _encoder.decode(token2);

        Token tokenObj2 = _dbClient.queryObject(Token.class, tw2.getTokenId());
        Assert.assertNotNull(tokenObj2);

        Thread.sleep(3 * 60 * 1000);
        _tokenManager.runCleanupNow();

        Assert.assertNull(_dbClient.queryObject(Token.class, tw1.getTokenId()));
        Assert.assertNull(_requestedTokenMapHelper.getTokenMap(tw1.getTokenId().toString()));
        Assert.assertNull(_dbClient.queryObject(RequestedTokenMap.class, map.getId()));

        Assert.assertNull(_dbClient.queryObject(Token.class, tw2.getTokenId()));
        Assert.assertNull(_requestedTokenMapHelper.getTokenMap(tw2.getTokenId().toString()));

    }

    /**
     * Convenience function to reset the coordinator data, call init on the two involved nodes,
     * and check they agree on the curent key id.
     * 
     * @param coordinator
     * @param tokenManager1
     * @param tokenManager2
     * @param encoder1
     * @param encoder2
     * @throws Exception
     */
    private void resetCoordinatorData(CoordinatorClient coordinator, CassandraTokenManager tokenManager1,
            CassandraTokenManager tokenManager2, Base64TokenEncoder encoder1,
            Base64TokenEncoder encoder2, TokenKeyGenerator tokenKeyGenerator1,
            TokenKeyGenerator tokenKeyGenerator2) throws Exception {
        final long ROTATION_INTERVAL_MSECS = 5000;
        DbClient dbClient = getDbClient();
        coordinator = new TestCoordinator();

        // Node 1
        tokenManager1 = new CassandraTokenManager();
        encoder1 = new Base64TokenEncoder();
        tokenKeyGenerator1 = new TokenKeyGenerator();
        TokenMaxLifeValuesHolder holder1 = new TokenMaxLifeValuesHolder();
        holder1.setKeyRotationIntervalInMSecs(ROTATION_INTERVAL_MSECS); // means that once a token is created,
        // if the next token being requested happens 5 seconds later or more, the keys will
        // rotate. This is to test the built in logic that triggers rotation.
        tokenManager1.setTokenMaxLifeValuesHolder(holder1);
        tokenManager1.setDbClient(dbClient);
        tokenManager1.setCoordinator(coordinator);
        encoder1.setCoordinator(coordinator);
        tokenKeyGenerator1.setTokenMaxLifeValuesHolder(holder1);
        encoder1.setTokenKeyGenerator(tokenKeyGenerator1);
        encoder1.managerInit();
        tokenManager1.setTokenEncoder(encoder1);

        // Node 2
        tokenManager2 = new CassandraTokenManager();
        encoder2 = new Base64TokenEncoder();
        tokenKeyGenerator2 = new TokenKeyGenerator();
        TokenMaxLifeValuesHolder holder2 = new TokenMaxLifeValuesHolder();
        holder2.setKeyRotationIntervalInMSecs(ROTATION_INTERVAL_MSECS);
        tokenManager2.setTokenMaxLifeValuesHolder(holder2);
        tokenManager2.setDbClient(dbClient);
        tokenManager2.setCoordinator(coordinator);
        encoder2.setCoordinator(coordinator);
        tokenKeyGenerator2.setTokenMaxLifeValuesHolder(holder2);
        encoder2.setTokenKeyGenerator(tokenKeyGenerator2);
        encoder2.managerInit();
        tokenManager2.setTokenEncoder(encoder2);

        StorageOSUserDAO userDAO = new StorageOSUserDAO();
        userDAO.setUserName("user1");
        // first, verify both managers are starting with the same key.
        final String token1 = tokenManager1.getToken(userDAO);
        Assert.assertNotNull(token1);
        TokenOnWire tw1 = encoder1.decode(token1);
        String key1 = tw1.getEncryptionKeyId();
        final String token2 = tokenManager2.getToken(userDAO);
        Assert.assertNotNull(token2);
        TokenOnWire tw2 = encoder2.decode(token2);
        String key2 = tw2.getEncryptionKeyId();
        Assert.assertEquals(key1, key2);
    }

    /**
     * returns number of current proxytokens for username
     * 
     * @param username
     * @return
     */
    private int getProxyTokenCountForUser(String username) throws IOException {
        URIQueryResultList tokens = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint
                .Factory.getProxyTokenUserNameConstraint(username), tokens);
        List<URI> uris = new ArrayList<URI>();
        for (Iterator<URI> it = tokens.iterator(); it.hasNext();) {
            uris.add(it.next());
        }

        List<ProxyToken> toReturn = _dbClient.queryObject(ProxyToken.class, uris);
        if (toReturn == null) {
            return 0;
        }
        return toReturn.size();
    }

    @Test
    public void testLegitimateTokenKeyIdRange() throws Exception {
        CoordinatorClient coordinator = new TestCoordinator();
        TokenMaxLifeValuesHolder holder = new TokenMaxLifeValuesHolder();
        TokenKeyGenerator tokenKeyGenerator1 = new TokenKeyGenerator();
        tokenKeyGenerator1.setTokenMaxLifeValuesHolder(holder);
        Base64TokenEncoder encoder1 = new Base64TokenEncoder();
        encoder1.setCoordinator(coordinator);
        encoder1.setTokenKeyGenerator(tokenKeyGenerator1);
        encoder1.managerInit();
        TokenKeysBundle bundle = tokenKeyGenerator1.readBundle();
        InterVDCTokenCacheHelper cacheHelper = new InterVDCTokenCacheHelper();
        cacheHelper.setCoordinator(coordinator);
        cacheHelper.setMaxLifeValuesHolder(holder);

        // assumption, there should be one key to start with:
        Assert.assertEquals(1, bundle.getKeyEntries().size());

        // POS try with the exact key that is in the bundle
        Assert.assertTrue(cacheHelper.
                sanitizeRequestedKeyIds(bundle, bundle.getKeyEntries().get(0)));

        // POS try with a key that is a little in the future
        String inputKeyStr = bundle.getCurrentKeyEntry();
        Long inputKey = Long.parseLong(inputKeyStr);
        inputKey += 3000;
        Assert.assertTrue(cacheHelper.
                sanitizeRequestedKeyIds(bundle, inputKey.toString()));

        // NEG try with a key that is more than a rotation +20 minutes in the future
        inputKeyStr = bundle.getCurrentKeyEntry();
        inputKey = Long.parseLong(inputKeyStr);
        inputKey += tokenKeyGenerator1.getKeyRotationIntervalInMSecs() +
                CassandraTokenValidator.FOREIGN_TOKEN_KEYS_BUNDLE_REFRESH_RATE_IN_MINS * 60 * 1000
                + 3000;
        Assert.assertFalse(cacheHelper.
                sanitizeRequestedKeyIds(bundle, inputKey.toString()));

        // NEG try with a key that is in the past
        inputKeyStr = bundle.getCurrentKeyEntry();
        inputKey = Long.parseLong(inputKeyStr);
        inputKey -= 1000;
        Assert.assertFalse(cacheHelper.
                sanitizeRequestedKeyIds(bundle, inputKey.toString()));

        // rotate the keys
        tokenKeyGenerator1.rotateKeys();
        bundle = tokenKeyGenerator1.readBundle();
        // assumption, there should be 2 keys now:
        Assert.assertEquals(2, bundle.getKeyEntries().size());

        // POS try with the previous and current key
        Assert.assertTrue(cacheHelper.
                sanitizeRequestedKeyIds(bundle, bundle.getKeyEntries().get(0)));
        Assert.assertTrue(cacheHelper.
                sanitizeRequestedKeyIds(bundle, bundle.getKeyEntries().get(1)));

        // POS try with a key that is a little bit in the future of the current key
        inputKeyStr = bundle.getCurrentKeyEntry();
        inputKey = Long.parseLong(inputKeyStr);
        inputKey += 3000;
        Assert.assertTrue(cacheHelper.
                sanitizeRequestedKeyIds(bundle, inputKey.toString()));

        // NEG try with a key that is more than a rotation + 20 minutes in the future
        inputKeyStr = bundle.getCurrentKeyEntry();
        inputKey = Long.parseLong(inputKeyStr);
        inputKey += tokenKeyGenerator1.getKeyRotationIntervalInMSecs() +
                CassandraTokenValidator.FOREIGN_TOKEN_KEYS_BUNDLE_REFRESH_RATE_IN_MINS * 60 * 1000
                + 3000;
        Assert.assertFalse(cacheHelper.
                sanitizeRequestedKeyIds(bundle, inputKey.toString()));

        // NEG try with a key that is in the recent past of the current key,
        // so in between low and high bounds without being any of them
        inputKeyStr = bundle.getCurrentKeyEntry();
        inputKey = Long.parseLong(inputKeyStr);
        inputKey -= 1000;
        Assert.assertFalse(cacheHelper.
                sanitizeRequestedKeyIds(bundle, inputKey.toString()));

        // POS try with a key that is one rotation in the future of the current key
        inputKeyStr = bundle.getCurrentKeyEntry();
        inputKey = Long.parseLong(inputKeyStr);
        inputKey += tokenKeyGenerator1.getKeyRotationIntervalInMSecs();
        Assert.assertTrue(cacheHelper.
                sanitizeRequestedKeyIds(bundle, inputKey.toString()));

        // NEG try with a key that is in the past of previous key
        inputKeyStr = bundle.getKeyEntries().get(0);
        inputKey = Long.parseLong(inputKeyStr);
        inputKey -= 1000;
        Assert.assertFalse(cacheHelper.
                sanitizeRequestedKeyIds(bundle, inputKey.toString()));

    }

}
