package com.emc.vipr.client

import org.apache.log4j.Logger
import org.apache.log4j.Level

Logger.getRootLogger().setLevel(Level.INFO)

host = args.length > 0 ? args[0] : "localhost"
config = new ClientConfig().withHost(host).withIgnoringCertificates(true)
token = new AuthClient(config).login("root", "ChangeMe")
client = new ViPRCoreClient(config).withAuthToken(token)
catalog = new ViPRCatalogClient2(config).withAuthToken(token)
portal = new ViPRPortalClient(config).withAuthToken(token)
sys = new ViPRSystemClient(config).withAuthToken(token)

def console = new groovy.ui.Console()
console.setVariable("client", client)
console.setVariable("catalog", catalog)
console.setVariable("portal", portal)
console.setVariable("sys", sys);
console.run()
