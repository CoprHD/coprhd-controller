/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package storageapi;

/**
 * A URL Factory that gives out the same address everytime
 * 
 * @author dmaddison
 */
public class StaticApiUrlFactory implements ApiUrlFactory {
    private String apiUrl;

    public StaticApiUrlFactory(String url) {
        this.apiUrl = url;
    }

    @Override
    public String getUrl() {
        return apiUrl;
    }
}
