package com.emc.storageos.api.service.impl.response;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Alerts;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.model.ResourceTypeEnum;

public  class SNMPTrapReceiver extends TaskResourceService implements CommandResponder{

      private MultiThreadedMessageDispatcher dispatcher;
      private Snmp snmp = null;
      private Address listenAddress;
      private ThreadPool threadPool;
      private int n = 0;
      private long start = -1;
      private static final Logger _log = LoggerFactory.getLogger(SNMPTrapReceiver.class);

      public SNMPTrapReceiver() {
      }

      public static void main(String[] args) {
            System.out.println("Starting");
            new SNMPTrapReceiver().run();
      }

     

      private void run() {
            try {
                  init();
                  snmp.addCommandResponder(this);
            } catch (Exception ex) {
                  ex.printStackTrace();
            }
      }

      private void init() throws UnknownHostException, IOException {
            threadPool = ThreadPool.create("Trap", 10);
            dispatcher = new MultiThreadedMessageDispatcher(threadPool,
                        new MessageDispatcherImpl());
            listenAddress = GenericAddress.parse(System.getProperty(
                        "snmp4j.listenAddress", "udp:0.0.0.0/9000"));
            TransportMapping<?> transport;
            if (listenAddress instanceof UdpAddress) {
                  transport = new DefaultUdpTransportMapping(
                              (UdpAddress) listenAddress);
            } else {
                  transport = new DefaultTcpTransportMapping(
                              (TcpAddress) listenAddress);
            }
            USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(
                        MPv3.createLocalEngineID()), 0);
            usm.setEngineDiscoveryEnabled(true);

            snmp = new Snmp(dispatcher, transport);
            snmp.getMessageDispatcher().addMessageProcessingModel(new MPv1());
            snmp.getMessageDispatcher().addMessageProcessingModel(new MPv2c());
            snmp.getMessageDispatcher().addMessageProcessingModel(new MPv3(usm));
            SecurityModels.getInstance().addSecurityModel(usm);
            snmp.getUSM().addUser(
                        new OctetString("MD5DES"),
                        new UsmUser(new OctetString("MD5DES"), AuthMD5.ID,
                                    new OctetString("UserName"), PrivDES.ID,
                                    new OctetString("PasswordUser")));
            snmp.getUSM().addUser(new OctetString("MD5DES"),
                        new UsmUser(new OctetString("MD5DES"), null, null, null, null));

            snmp.listen();
      }

      public void processPdu(CommandResponderEvent event) {
    	  String affectedResourceId = null;
    	  String problemDescription = null;
            if (start < 0) {
                  start = System.currentTimeMillis() - 1;
            }

            n++;
            if ((n % 100 == 1)) {
                  System.out.println("Processed "
                              + (n / (double) (System.currentTimeMillis() - start))
                              * 1000 + "/s, total=" + n);
            }

            StringBuffer msg = new StringBuffer();
            System.out.println(event.toString());
            Vector<? extends VariableBinding> varBinds = event.getPDU()
                        .getVariableBindings();
            if (varBinds != null && !varBinds.isEmpty()) {
            //    Iterator<? extends VariableBinding> varIter = varBinds.iterator();
            //    while (varIter.hasNext()) {
                  OID partOid = new OID("1.3.6.1.4.1.11970.1.1.2.7");
                  affectedResourceId = event.getPDU().getBindingList(partOid).get(0).getVariable().toString();
                  OID msgOid = new OID("1.3.6.1.4.1.11970.1.1.2.12");
                  
                  msg.append(event.getPDU().getBindingList(msgOid).get(0).getVariable());
                  problemDescription = event.getPDU().getBindingList(msgOid).get(0).getVariable().toString();
              //    }
            }
            System.out.println("Details: " + msg.toString());
            _log.info("Details: " + msg.toString() + "\n");
            Alerts trapInfo = new Alerts();
            trapInfo.setId(URIUtil.createId(Alerts.class));
            trapInfo.setAffectedResourceId(affectedResourceId);
            trapInfo.setProblemDescription(problemDescription);
            _log.info("Before persisting to Db");
            
            _dbClient.createObject(trapInfo);
            
           
            _log.info("Persisted to Db");
            
            //OID oid = new OID("1.3.6.1.6.3.1.1.4.1.0");
            //System.out.println("Trap oid:" + event.getPDU().getBindingList(oid).get(0).getVariable());
      }

	@Override
	protected DataObject queryResource(URI id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected URI getTenantOwner(URI id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected ResourceTypeEnum getResourceType() {
		// TODO Auto-generated method stub
		return null;
	}
}
