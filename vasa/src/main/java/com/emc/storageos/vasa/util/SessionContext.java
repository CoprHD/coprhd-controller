/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* **********************************************************
 * Copyright 2010 VMware, Inc.  All rights reserved. -- VMware Confidential
 * **********************************************************/
package com.emc.storageos.vasa.util;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import com.emc.storageos.vasa.SOSManager;
import com.vmware.vim.vasa._1_0.StorageFault;
import com.vmware.vim.vasa._1_0.data.xsd.UsageContext;

/**
 * Track the indiviudal Sessions/Contexts connections to SampleVP
 */
public class SessionContext {
	public static long DEFAULT_SESSION_TIMEOUT = 300; // seconds
	public static String INVALID_SESSION_ID = "0";

	private static Random rand = new Random();
	private static Logger log = Logger.getLogger(SessionContext.class);
	//
	// the assignment to sessionContextList is considered atomic and does
	// not requiere that sessionContextListLock be held
	private static LinkedList<SessionContext> sessionContextList = new LinkedList<SessionContext>();
	private static Set<String> sessionIdList = new HashSet<String>();
	//
	// sessionContextListLock must be held while sessionContextList is
	// being manipulated.
	private static ReentrantLock sessionContextListLock = new ReentrantLock();
	private static int MAX_SESSION_ID = 1000000;

	private String id;
	private String clientAddress;
	private UsageContext context;
	private Timer timer;
	private SOSManager sosManager;

	/*
	 * If the SessionId has not been used in more than
	 * UsageContext.timeoutInSeconds, then void the Session and free the
	 * resources.
	 */
	class SessionTimeoutTask extends TimerTask {
		public void run() {
			removeFromList();
		}
	}

	@Override
	@SuppressWarnings("squid:S1206") //"equals(Object obj)" and "hashCode()" should be overridden in pairs 
	public boolean equals(Object o) { 
		SessionContext sc = (SessionContext) o;
		return this.getSessionId().equals(sc.getSessionId());
	}

	/*
	 * Cancel any existing timer task and start a new one.
	 */
	private boolean restartTimer() {
		if (this.timer != null) {
			this.timer.cancel();
		}
		this.timer = new Timer(true);

		try {
			long timeout = this.context.getSessionTimeoutInSeconds();
			if (timeout <= 0) {
				timeout = DEFAULT_SESSION_TIMEOUT;
			}
			this.timer.schedule(new SessionTimeoutTask(), timeout * 1000);
			return true;
		} catch (Exception e) {
			log.error("Could not restart session timer for SessionId " + id
					+ " e: " + e);
			return false;
		}
	}

	/**
	 * 
	 * @param sessionId
	 * @param alloateIfNotFound
	 * @param uc
	 * @param clientAddress
	 */
	private static SessionContext lookupSessionBySessionId(String sessionId,
			boolean allocateIfNotFound, UsageContext uc, String clientAddress)
			throws StorageFault {
		if ((sessionContextList != null) && (sessionId != null)
				&& (!sessionId.equals(INVALID_SESSION_ID))) {

			sessionContextListLock.lock();
			try {
				for (int i = 0; i < sessionContextList.size(); i++) {
					SessionContext sc = (SessionContext) sessionContextList
							.get(i);
					if ((sc != null) && (sc.getSessionId().equals(sessionId))) {
						sc.restartTimer();
						return sc;
					}

					if (sc == null) {
						log.trace("SessionContext instance " + i + " is null.");
						throw FaultUtil
								.StorageFault("Session List may be corrupted.");
					}
				}
			} finally {
				sessionContextListLock.unlock();
			}
		}

		if (allocateIfNotFound) {
			return new SessionContext(clientAddress, uc);
		}

		return null;
	}

	private static String generateRandomId() {
		/*
		 * pick a random integer evenly distributed between 0 and MAX_SESSION_ID
		 * -1
		 */
		int nextRandomInt = rand.nextInt(MAX_SESSION_ID);

		/*
		 * Add 1 to the random number since 0 is not a valid session id;
		 */
		return String.valueOf(nextRandomInt + 1);
	}

	private static String generateUniqueRandomId() {
		String randomId = generateRandomId();
		try {
			while (lookupSessionContextBySessionId(randomId) != null) {
				/*
				 * If this id is already being used, get a different id.
				 */
				randomId = generateRandomId();
			}
		} catch (Exception e) {
			// ignore the failure
			log.error("generateUniqueRandomId(): Exception : " + e);
		}
		return randomId;
	}

	/*
	 * SessionContext class Constructor
	 * 
	 * @param ca
	 * 
	 * @param us
	 */
	private SessionContext(String ca, UsageContext uc) {
		id = generateUniqueRandomId();
		
		log.trace("SessionId: " + id + "added");
		clientAddress = ca;
		context = uc;
		timer = null;

		sessionContextListLock.lock();
		try {
			sessionContextList.add(this);
			sessionIdList.add(id);
		} finally {
			sessionContextListLock.unlock();
		}

		restartTimer();
	}

	private static void removeFromList(SessionContext sc) {
		if ((sc != null) && (sc.timer != null)) {
			sc.timer.cancel();
		}

		if ((sessionContextList != null) && (sc != null)) {
			boolean removed = false;
			String id = sc.getSessionId();

			sessionContextListLock.lock();
			try {
				removed = sessionContextList.remove(sc);
			} finally {
				sessionContextListLock.unlock();
			}

			if (removed) {
				log.trace("Session " + id + " removed.");
			} else {
				log.trace("Session " + id
						+ " could not be removed. Not found in list.");
			}
		}
	}

	private void removeFromList() {
		log.trace("Session " + this.getSessionId() + " timed out.");
		removeFromList(this);
	}

	/* public methods */

	public String getSessionId() {
		return id;
	}

	public String getClientAddress() {
		return clientAddress;
	}

	public UsageContext getUsageContext() {
		return context;
	}
	
	public SOSManager getSosManager() {
		return sosManager;
	}

	public void setSosManager(SOSManager sosManager) {
		this.sosManager = sosManager;
	}

	/*
	 * Lookup SessionContext with SessionId. If a SessionContext exists, remove
	 * it. Create new SessionContext.
	 * 
	 * @param sessionId
	 * 
	 * @param uc
	 * 
	 * @param clientAddress
	 */
	public static SessionContext createSession(UsageContext uc,
			String clientAddress) throws StorageFault {
		/*
		 * Create new SessionContext
		 */
		return lookupSessionBySessionId(INVALID_SESSION_ID, true, uc,
				clientAddress);
	}

	/*
	 * Lookup SessionContext from SessionId. Do not create new SessionContext if
	 * SessionId is not found.
	 * 
	 * @param sessionId
	 */
	public static SessionContext lookupSessionContextBySessionId(
			String sessionId) throws StorageFault {
		return SessionContext.lookupSessionBySessionId(sessionId, false, null,
				null);
	}

	/*
	 * If any exist, free the resources for the SessionContext associated with
	 * the given sessionId.
	 * 
	 * @param sessionId
	 */
	public static void removeSession(String sessionId) throws StorageFault {
		removeFromList(SessionContext.lookupSessionBySessionId(sessionId,
				false, null, null));
	}
	@SuppressWarnings("squid:S00100") //("Suppressing Sonar violation for Method names should comply with a naming convention")
	public static boolean IsPreviouslyUsed(String sessionId) {
		return sessionIdList.contains(sessionId);
	}
}


