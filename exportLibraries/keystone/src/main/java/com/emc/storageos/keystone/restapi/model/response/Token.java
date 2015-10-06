/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.keystone.restapi.model.response;


/*
 *  "token": {
            "issued_at": "2014-01-30T15:30:58.819584",
            "expires": "2014-01-31T15:30:58Z",
            "id": "aaaaa-bbbbb-ccccc-dddd",
            "tenant": {
                "description": null,
                "enabled": true,
                "id": "fc394f2ab2df4114bde39905f800dc57",
                "name": "demo"
            }
        },
 * */

public class Token 
{
	private String issued_at;
	private String expires;
	private String id;
	
	private Tenant tenant;

	public String getIssued_at() {
		return issued_at;
	}

	public void setIssued_at(String issued_at) {
		this.issued_at = issued_at;
	}

	public String getExpires() {
		return expires;
	}

	public void setExpires(String expires) {
		this.expires = expires;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Tenant getTenant() {
		return tenant;
	}

	public void setTenant(Tenant tenant) {
		this.tenant = tenant;
	}

}
