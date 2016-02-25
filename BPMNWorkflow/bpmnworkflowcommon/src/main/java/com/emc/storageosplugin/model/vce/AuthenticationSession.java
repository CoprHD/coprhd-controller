package com.emc.storageosplugin.model.vce;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

//import ch.dunes.util.EncryptHelper;
//import ch.dunes.vso.sdk.helper.SDKHelper;



import com.emc.storageos.model.user.UserInfo;
import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.ViPRCoreClient;

public class AuthenticationSession {

	

	private static final String pluginName = "EMC_ViPR";
	private static final String HOSTNAME = "emc.vipr.host.name";
	private static final String USERNAME = "emc.vipr.user.name";
	@SuppressWarnings("all")
	private static final String PASSWORD = "emc.vipr.password";
	private static final String PORT = "emc.vipr.port";

	
	private static ConcurrentHashMap<String, ViPRCoreClient> session = new ConcurrentHashMap<String,ViPRCoreClient>();
	private static Logger log = Logger.getLogger(ViPRClientUtils.class);
	
	
	private static long inActivityTimer =10;	// Will expire in 10 mins

//	public synchronized static boolean startSession() {
//		String path = SDKHelper.getConfigurationPathForPluginName(pluginName);
//		Properties prop = null;
//		String host = null;
//		Integer port = null;
//		String user = null;
//		String pwd = null;
//
//		try {
//			if (new File(path).exists()) {
//
//				prop = SDKHelper.loadPropertiesForPluginName(pluginName);
//				if (prop.getProperty(HOSTNAME) != null
//						&& prop.getProperty(USERNAME) != null
//						&& prop.getProperty(PASSWORD) != null) {
//
//					host = prop.getProperty(HOSTNAME);
//					user = prop.getProperty(USERNAME);
//					pwd = EncryptHelper.decrypt(prop.getProperty(PASSWORD));
//					port = Integer.parseInt(prop.getProperty(PORT));
//
//					return startSession(host, port, user, pwd);
//				}
//			}
//
//		} catch (IOException e) {
//			e.printStackTrace();
//			return false;
//		}
//		return false;
//	}
			
	public synchronized static boolean startSession(String host, Integer port, String userName, String userPassword) {

		ViPRCoreClient viprClient = null;
		if (host != null && port != null && userName != null
				&& userPassword != null) {

			String key = String.format("ViPRCoreClient.%s", host + port);
			viprClient = getClientSession(key);
			if (viprClient == null) {
				log.debug("Creating new ViPRCoreClient");
				ClientConfig clientConfig = new ClientConfig()
						.withHost(host).withRequestLoggingEnabled()
						.withHost(host)
						.withPort(port > 0 ? port : ClientConfig.DEFAULT_API_PORT)
						.withMaxRetries(10)
						.withIgnoringCertificates(true);

				viprClient = new ViPRCoreClient(clientConfig);
				String token = viprClient.auth().login(userName, userPassword);
				if (token != null) {
					log.info("Login successful for host :" + host);
					setClientSession(key,viprClient);
					return true;
				} else {
					log.info("Login failed for host :" + host);
					clearClientSession(key,viprClient);
					return false;
				}
			}
		}
		return false;
	}

	public static ViPRCoreClient getViprClient() {
		String key = null;
		ViPRCoreClient client = getClientSession(key);
		
		return client;
	}

	public synchronized static void endSession() {
		log.info("endSession : Logout ViPR Session" );

		if (getViprClient() != null)
			getViprClient().auth().logout();
		session.clear();
		if (inactivityTimerTask != null)
			inactivityTimerTask.cancel();
		inactivityTimerTask =null;

	}

	private synchronized static ViPRCoreClient getClientSession(String key) {
		if (key == null){
			//We maintain only one session , So return first entry for the session
			Iterator<Entry<String, ViPRCoreClient>> iter = session.entrySet().iterator();
			inActivityTimer=10;										//Reset inactivity timer after any activity
		    while (iter.hasNext()) {
		        Entry<String, ViPRCoreClient> keyValue = iter.next();
		        return keyValue.getValue();
		    }
		    return null;
		}
		ViPRCoreClient sessClient=session.get(key);
		return sessClient;
	}

	private static InactivityTimerTask inactivityTimerTask;
	
	private synchronized static void setClientSession(String key, ViPRCoreClient client) {
		session.clear();		//Maintain only one client session
		session.put(key, client);
		if (inactivityTimerTask != null)
			inactivityTimerTask.cancel();
		inactivityTimerTask =null;
		
		inactivityTimerTask = new InactivityTimerTask(key);

		Timer timer = new Timer();
    	timer.scheduleAtFixedRate(inactivityTimerTask, 60000,60000);
		

		
	}

	private static void clearClientSession(String key, ViPRCoreClient viprClient) {
		session.remove(key);
		if (inactivityTimerTask != null)
			inactivityTimerTask.cancel();
		inactivityTimerTask =null;
	}

	
	public synchronized static boolean isSessionActive() {
		boolean isActive = false;
		ViPRCoreClient viprClient = getClientSession(null);
		if (viprClient != null) {
			UserInfo userInfo = viprClient.getUserInfo();
			if (userInfo != null) {
				isActive = true;
			}
		}
		return isActive;
	}
	
	
	private synchronized static long getInActivityTimer() {
		return inActivityTimer;
	}

	private synchronized static void setInActivityTimer() {
		inActivityTimer--;
	}


	private static  class InactivityTimerTask extends TimerTask {
		
		//private String key;

		private InactivityTimerTask(String key) {
			//this.key=key;
		}


		@Override
		public void run() {

			isTimedOut();
	
		}

		// simulate a time consuming task
		private void isTimedOut() {
			long counter=AuthenticationSession.getInActivityTimer() ;
			log.info("isTimedOut : counter "+counter );				
			
			if (counter <= 0){
				log.info("isTimedOut : ViPR Session TimedOut" );				
				 AuthenticationSession.endSession();
			} else {
				setInActivityTimer();
			}

		}
	}

}
