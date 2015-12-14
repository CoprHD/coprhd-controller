/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.common.http;

import com.sun.jersey.api.client.ClientResponse;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MultivaluedMap;

// TODO: Auto-generated Javadoc
/**
 * The Interface RestClientItf.
 */
public interface RestClientItf {

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url) throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param cookies the cookies
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, List<Cookie> cookies) throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, String username, String password) throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param cookies the cookies
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, List<Cookie> cookies, String username, String password) throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param headers the headers
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, Map<String, String> headers) throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param headers the headers
     * @param cookies the cookies
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, Map<String, String> headers, List<Cookie> cookies) throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param headers the headers
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, Map<String, String> headers, String username, String password) throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param headers the headers
     * @param cookies the cookies
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, Map<String, String> headers, List<Cookie> cookies, String username, String password)
            throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param queryParams the query params
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, MultivaluedMap<String, String> queryParams) throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param queryParams the query params
     * @param cookies the cookies
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, List<Cookie> cookies) throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param queryParams the query params
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, String username, String password) throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param queryParams the query params
     * @param cookies the cookies
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, List<Cookie> cookies, String username, String password)
            throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param queryParams the query params
     * @param headers the headers
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers) throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param queryParams the query params
     * @param headers the headers
     * @param cookies the cookies
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers, List<Cookie> cookies)
            throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param queryParams the query params
     * @param headers the headers
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers, String username, String password)
            throws InternalException;

    /**
     * Gets the ClientResponse.
     *
     * @param url the url
     * @param queryParams the query params
     * @param headers the headers
     * @param cookies the cookies
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse get(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers, List<Cookie> cookies,
            String username, String password) throws InternalException;

    /**
     * Put.
     *
     * @param url the url
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse put(URI url, String body) throws InternalException;

    /**
     * Put.
     *
     * @param url the url
     * @param cookies the cookies
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse put(URI url, List<Cookie> cookies, String body) throws InternalException;

    /**
     * Put.
     *
     * @param url the url
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse put(URI url, String body, String username, String password) throws InternalException;

    /**
     * Put.
     *
     * @param url the url
     * @param cookies the cookies
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse put(URI url, List<Cookie> cookies, String body, String username, String password) throws InternalException;

    /**
     * Put.
     *
     * @param url the url
     * @param headers the headers
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse put(URI url, Map<String, String> headers, String body) throws InternalException;

    /**
     * Put.
     *
     * @param url the url
     * @param headers the headers
     * @param cookies the cookies
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse put(URI url, Map<String, String> headers, List<Cookie> cookies, String body) throws InternalException;

    /**
     * Put.
     *
     * @param url the url
     * @param headers the headers
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse put(URI url, Map<String, String> headers, String body, String username, String password) throws InternalException;

    /**
     * Put.
     *
     * @param url the url
     * @param headers the headers
     * @param cookies the cookies
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse put(URI url, Map<String, String> headers, List<Cookie> cookies, String body, String username, String password)
            throws InternalException;

    /**
     * Put.
     *
     * @param url the url
     * @param queryParams the query params
     * @param headers the headers
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers,
            String body) throws InternalException;

    /**
     * Put.
     *
     * @param url the url
     * @param queryParams the query params
     * @param headers the headers
     * @param cookies the cookies
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers,
            List<Cookie> cookies, String body) throws InternalException;

    /**
     * Put.
     *
     * @param url the url
     * @param queryParams the query params
     * @param headers the headers
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers,
            String body, String username, String password) throws InternalException;

    /**
     * Put.
     *
     * @param url the url
     * @param queryParams the query params
     * @param headers the headers
     * @param cookies the cookies
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse put(URI url, MultivaluedMap<String, String> queryParams, Map<String, String> headers,
            List<Cookie> cookies, String body, String username, String password) throws InternalException;

    /**
     * Post.
     *
     * @param url the url
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse post(URI url, String body) throws InternalException;

    /**
     * Post.
     *
     * @param url the url
     * @param cookies the cookies
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse post(URI url, List<Cookie> cookies, String body) throws InternalException;

    /**
     * Post.
     *
     * @param url the url
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse post(URI url, String body, String username, String password) throws InternalException;

    /**
     * Post.
     *
     * @param url the url
     * @param cookies the cookies
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse post(URI url, List<Cookie> cookies, String body, String username, String password) throws InternalException;

    /**
     * Post.
     *
     * @param url the url
     * @param headers the headers
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse post(URI url, Map<String, String> headers, String body) throws InternalException;

    /**
     * Post.
     *
     * @param url the url
     * @param headers the headers
     * @param cookies the cookies
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse post(URI url, Map<String, String> headers, List<Cookie> cookies, String body) throws InternalException;

    /**
     * Post.
     *
     * @param url the url
     * @param headers the headers
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse post(URI url, Map<String, String> headers, String body, String username, String password) throws InternalException;

    /**
     * Post.
     *
     * @param url the url
     * @param headers the headers
     * @param cookies the cookies
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse post(URI url, Map<String, String> headers, List<Cookie> cookies, String body, String username, String password)
            throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param cookies the cookies
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, List<Cookie> cookies) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, String body) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param cookies the cookies
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, List<Cookie> cookies, String body) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, String username, String password) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param cookies the cookies
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, List<Cookie> cookies, String username, String password) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, String body, String username, String password) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param cookies the cookies
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, List<Cookie> cookies, String body, String username, String password) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param headers the headers
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, Map<String, String> headers) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param headers the headers
     * @param cookies the cookies
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, Map<String, String> headers, List<Cookie> cookies) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param headers the headers
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, Map<String, String> headers, String body) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param headers the headers
     * @param cookies the cookies
     * @param body the body
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, Map<String, String> headers, List<Cookie> cookies, String body) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param headers the headers
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, Map<String, String> headers, String username, String password) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param headers the headers
     * @param cookies the cookies
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, Map<String, String> headers, List<Cookie> cookies, String username, String password)
            throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param headers the headers
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, Map<String, String> headers, String body, String username, String password) throws InternalException;

    /**
     * Delete.
     *
     * @param url the url
     * @param headers the headers
     * @param cookies the cookies
     * @param body the body
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse delete(URI url, Map<String, String> headers, List<Cookie> cookies, String body, String username, String password)
            throws InternalException;

    /**
     * Head.
     *
     * @param url the url
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse head(URI url) throws InternalException;

    /**
     * Head.
     *
     * @param url the url
     * @param cookies the cookies
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse head(URI url, List<Cookie> cookies) throws InternalException;

    /**
     * Head.
     *
     * @param url the url
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse head(URI url, String username, String password) throws InternalException;

    /**
     * Head.
     *
     * @param url the url
     * @param cookies the cookies
     * @param username the username
     * @param password the password
     * @return the client response
     * @throws InternalException the internal exception
     */
    ClientResponse head(URI url, List<Cookie> cookies, String username, String password) throws InternalException;

}
