/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.util;

/**
 * Callback handler for processing items from resources that produce a large stream of items.
 * 
 * @param <T>
 *        the type of the item.
 */
public interface ItemProcessor<T> {
    /**
     * Called before any items are processed.
     * 
     * @throws Exception
     *         if an error occurs.
     */
    public void startItems() throws Exception;

    /**
     * Called after all items are processed.
     * 
     * @throws Exception
     *         if an error occurs.
     */
    public void endItems() throws Exception;

    /**
     * Processes a single item.
     * 
     * @param item
     *        the item to process.
     * @throws Exception
     *         if an error occurs.
     */
    public void processItem(T item) throws Exception;
}
