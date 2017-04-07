package com.emc.storageos.db.client.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.SimpleStatement;
import com.emc.storageos.db.client.model.DataObject;

public class DBDataObjectCleaner {
	private static final Logger log = LoggerFactory.getLogger(DBDataObjectCleaner.class);
	private final static String CQL_DELETE = "delete from \"%s\" where key='%s'";
	
	private RowMutator rowMutator;
	
	public DBDataObjectCleaner(DbClientContext clientContext, boolean retryFailedWriteWithLocalQuorum) {
		rowMutator = new RowMutator(clientContext, retryFailedWriteWithLocalQuorum);
	}
	
	public void deleteObjects(DataObjectType doType, DataObject... objects) {
		for (DataObject object : objects) {
			SimpleStatement statement = new SimpleStatement(String.format(CQL_DELETE, doType.getCF().getName(), object.getId()));
			rowMutator.addCqlStatement(statement);
		}
		
		rowMutator.execute();
	}
}
