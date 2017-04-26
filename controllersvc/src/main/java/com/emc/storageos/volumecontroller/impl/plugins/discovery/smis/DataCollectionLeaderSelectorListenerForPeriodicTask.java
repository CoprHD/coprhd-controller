package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.emc.storageos.coordinator.client.service.LeaderSelectorListenerForPeriodicTask;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;

public class DataCollectionLeaderSelectorListenerForPeriodicTask extends LeaderSelectorListenerForPeriodicTask {
	private static final Log log = LogFactory.getLog(DataCollectionLeaderSelectorListenerForPeriodicTask.class);
	private int poolCoreSize;
	private String poolName;
	
	public DataCollectionLeaderSelectorListenerForPeriodicTask(String poolName, int poolCoreSize) {
		super(null);
		this.poolName = poolName;
		this.poolCoreSize = poolCoreSize;
	}

	@Override
	protected void startLeadership() throws Exception {
		log.info("This node is selected as data collecion leader, starting scheduler");
		scheduler = new NamedScheduledThreadPoolExecutor(poolName, poolCoreSize);
		super.startLeadership();
	}

	@Override
	protected void stopLeadership() {
		log.info("Give up leader as data collection, stopping scheduler");
		super.stopLeadership();
		scheduler.shutdown();
		log.info("Scheduler has been shutdown");
	}
}
