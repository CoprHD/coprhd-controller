/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.cassandra.serializers.BooleanSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.utils.UUIDs;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.google.common.collect.Sets;

/**
 * This migration handler is to fix issue COP-26680. Check CF FileShare and
 * Snapshot to detect whether it contain duplicated index mentioned in issue
 * COP-26680
 *
 */
public class RebuildIndexDuplicatedCFNameMigration extends BaseCustomMigrationCallback {

	private static final Logger log = LoggerFactory.getLogger(RebuildIndexDuplicatedCFNameMigration.class);
	private static final int REBUILD_INDEX_BATCH_SIZE = 200;
	private static final int DEFAULT_PAGE_SIZE = 100;
	private Set<Class<? extends DataObject>> scanClasses = Sets.newHashSet(FileShare.class, Snapshot.class);
	private Set<String> fieldNames = Sets.newHashSet("path", "mountPath");
	private AtomicInteger totalProcessedIndex = new AtomicInteger(0);
	private BlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<Runnable>(20);
	private ThreadPoolExecutor executor = new ThreadPoolExecutor(5, 10, 50, TimeUnit.MILLISECONDS, blockingQueue);

	@Override
	public void process() throws MigrationCallbackException {
		log.info("Begin to run migration handler RebuildIndexDuplicatedCFName");
		long beginTime = System.currentTimeMillis();

		for (Class<? extends DataObject> clazz : scanClasses) {
			try {
				handleDataObjectClass(clazz);
			} catch (Exception e) {
				log.error("Failed to detect/rebuild duplicated index for {}", clazz, e);
			}
		}

		executor.shutdown();
		try {
			if (!executor.awaitTermination(120, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (Exception e) {
			executor.shutdownNow();
		}

		log.info("Totally rebuild index count: {}", totalProcessedIndex);
		log.info("Finish run migration handler RebuildIndexDuplicatedCFName");
		log.info("Total seconds: {}", (System.currentTimeMillis() - beginTime) / 1000);
	}

	public void handleDataObjectClass(Class<? extends DataObject> clazz) throws Exception {
		log.info("proccess model class {}", clazz);

		DbClientImpl dbClient = (DbClientImpl) getDbClient();
		DataObjectType doType = TypeMap.getDoType(clazz);

		String queryString = String.format("select key, value from \"%s\" where column1='%s' ALLOW FILTERING",
				doType.getCF().getName(), DataObject.INACTIVE_FIELD_NAME);
		SimpleStatement queryStatement = new SimpleStatement(queryString);
		queryStatement.setFetchSize(DEFAULT_PAGE_SIZE);
		DbClientContext dbClientContext = dbClient.getDbClientContext(doType.getDataObjectClass());
		ResultSet resultSet = dbClientContext.getSession().execute(queryStatement);

		int totalCount = 0;
		List<URI> ids = new ArrayList<>(0);
		for (Row row : resultSet) {
			String key = row.getString(0);
			boolean inactive = BooleanSerializer.instance.deserialize(row.getBytes(1));

			if (inactive == true) {
				continue;
			}

			ids.add(URI.create(key));

			totalCount += ids.size();

			if (ids.size() >= REBUILD_INDEX_BATCH_SIZE) {
				Map<String, List<CompositeColumnName>> result = dbClient.queryRowsWithAllColumns(
						dbClient.getDbClientContext(doType.getDataObjectClass()), ids, doType.getCF().getName());
				try {
					executor.submit(new RebuildIndexTask(doType, result, dbClientContext));
					ids = new ArrayList<>(0);
				} catch (Exception e) {
					log.warn(
							"Failed to submit rebuild index task, this may be caused by thread pool is full, try in next round",
							e);
				}
			}

		}

		log.info("Total data object count is {} for model {}", totalCount, clazz.getName());
		return;
	}

	private void rebuildIndex(DataObjectType doType, ColumnField columnField, Object value, String rowKey,
			CompositeColumnName column, RowMutator rowMutator)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

		rowMutator.deleteRecordColumn(doType.getCF().getName(), rowKey, column);

		// TODO Java Driver
		// rowMutator.resetTimeUUIDStartTime(UUIDs.unixTimestamp(column.getTimeUUID()));
		DataObject dataObject = DataObject.createInstance(doType.getDataObjectClass(), URI.create(rowKey));
		dataObject.trackChanges();

		columnField.getPropertyDescriptor().getWriteMethod().invoke(dataObject, value);
		columnField.serialize(doType.getCF().getName(), dataObject, rowMutator);
	}

	public int getTotalProcessedIndexCount() {
		return totalProcessedIndex.get();
	}

	class RebuildIndexTask implements Runnable {
		private DataObjectType doType;
		private Map<String, List<CompositeColumnName>> rows;
		private DbClientContext dbClientContext;

		public RebuildIndexTask(DataObjectType doType, Map<String, List<CompositeColumnName>> rows,
				DbClientContext dbClientContext) {
			this.doType = doType;
			this.rows = rows;
			this.dbClientContext = dbClientContext;
		}

		@Override
		public void run() {
			int totalCleanupCount = 0;
			RowMutator rowMutator = new RowMutator(dbClientContext, false);
			for (String rowKey : rows.keySet()) {
				try {
					List<CompositeColumnName> columnList = rows.get(rowKey);
					Map<FieldValueTimeUUIDPair, CompositeColumnName> valueColumnMap = new HashMap<FieldValueTimeUUIDPair, CompositeColumnName>();
					DataObject dataObject = DataObject.createInstance(doType.getDataObjectClass(), URI.create(rowKey));

					for (CompositeColumnName column : columnList) {
						if (!fieldNames.contains(column.getOne())) {
							continue;
						}

						ColumnField columnField = doType.getColumnField(column.getOne());
						columnField.deserialize(column, dataObject);
						Object valueObject = columnField.getPropertyDescriptor().getReadMethod().invoke(dataObject,
								null);

						if (valueObject == null || column.getTimeUUID() == null) {
							continue;
						}

						FieldValueTimeUUIDPair key = null;
						key = new FieldValueTimeUUIDPair(valueObject, column.getTimeUUID());

						if (valueColumnMap.containsKey(key)) {
							totalCleanupCount++;
							rebuildIndex(doType, columnField, valueObject, rowKey, column, rowMutator);
						} else {
							valueColumnMap.put(key, column);
						}
					}
				} catch (Exception e) {
					log.error("Failed to proccess Data Object: ", e);
				}
			}
			
			if (totalCleanupCount > 0) {
				rowMutator.execute();
			}

			totalProcessedIndex.getAndAdd(totalCleanupCount);
		}
	}

	class FieldValueTimeUUIDPair {
		private Object fieldValue;
		private UUID timeUUID;

		public FieldValueTimeUUIDPair(Object fieldValue, UUID timeUUID) {
			this.fieldValue = fieldValue;
			this.timeUUID = timeUUID;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((fieldValue == null) ? 0 : fieldValue.hashCode());
			result = prime * result + ((timeUUID == null) ? 0 : timeUUID.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			FieldValueTimeUUIDPair other = (FieldValueTimeUUIDPair) obj;
			if (fieldValue == null) {
				if (other.fieldValue != null)
					return false;
			} else if (!fieldValue.equals(other.fieldValue))
				return false;
			if (timeUUID == null) {
				if (other.timeUUID != null)
					return false;
			} else if (!timeUUID.equals(other.timeUUID))
				return false;
			return true;
		}
	}
}