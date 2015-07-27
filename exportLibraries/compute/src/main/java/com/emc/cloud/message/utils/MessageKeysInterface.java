/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.message.utils;

public interface MessageKeysInterface {
    public int getErrorCode();
    public String getMessageKey();
    public String getDecodedMessage();
    public String getDecodedMessage(String[] params);

}
