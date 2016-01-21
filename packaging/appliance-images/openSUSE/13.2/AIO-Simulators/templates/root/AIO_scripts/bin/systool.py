import os, subprocess, sys
from subprocess import PIPE,Popen
from xml.dom.minidom import parseString

configFile = "/etc/sysconfig/network/ovf-network.tmp"
filelocation = "/etc/ovf-env.properties"


def findXmlSection(dom, sectionName):
   sections = dom.getElementsByTagName(sectionName)
   return sections[0]

def getPropertyMap(ovfEnv):
   dom = parseString(ovfEnv)
   section = findXmlSection(dom, "PropertySection")
   propertyMap = {}
   for property in section.getElementsByTagName("Property"):
      key   = property.getAttribute("oe:key")
      value = property.getAttribute("oe:value")
      propertyMap[key] = value
   dom.unlink()
   return propertyMap

def main():
    global configFile
    global filelocation

    if (len(sys.argv) > 3):
       print "Usage: systool2 [OPTION]"
    elif(len(sys.argv) == 1):
        enc = sys.stdout.encoding
        proc = Popen(["/usr/bin/vmware-rpctool \"info-get guestinfo.ovfEnv\""], shell = True, stdout = PIPE)
        ovfEnv = proc.communicate()[0]

        if(not("Property" in ovfEnv)):
            print ovfEnv
            print "Using ", filelocation, " instead."
            os.system("/usr/bin/cp " + filelocation + " " + configFile)
            return

        propertyMap = getPropertyMap(ovfEnv)

        ip      = propertyMap["ip0.AIO"]
        ip2     = propertyMap["ip1.AIO"]
        if (ip2 == "0.0.0.0"):
            ip2 = ""
        netmask = propertyMap["netmask.AIO"]
        gateway = propertyMap["net_gateway.AIO"]
        dns1    = propertyMap["DNS.AIO"]
        hostname    = propertyMap["hostname.AIO"]

        print "network_gateway=" + gateway
        print "network_nameservers=" + hostname
        print "network_netmask=" + netmask
        print "network_standalone_ipaddr=" + ip
        print "network_standalone_ipaddr2=" + ip2

        f=file(configFile, "w")
        f.write("network_gateway=" + gateway + "\n")
        f.write("network_nameservers=" + hostname + "\n")
        f.write("network_netmask=" + netmask + "\n")
        f.write("network_standalone_ipaddr=" + ip + "\n")
        f.write("network_standalone_ipaddr2=" + ip2 + "\n")
        f.close()

    elif (sys.argv[1] == "--configure-network"):
        if(len (sys.argv) == 3):
            configFile = sys.argv[2]
        proc = Popen(["/sbin/ifconfig -a | grep Ethernet | awk '{print $1}' | head -1"], shell = True, stdout = PIPE)
        interface = proc.communicate()[0].strip()
        os.system("/sbin/ifdown " + interface + " > /dev/null")

        f = file(configFile, "r")
        props = dict(line[:-1].split("=") for line in f)
        f.close()

        f = file("/etc/sysconfig/network/ifcfg-" + interface + "", "w")
        f.write("DEVICE='" + interface + "'\nSTARTMODE=auto\nBOOTPROTO=static\n")
        f.write("IPADDR='%s'\n" % props["network_standalone_ipaddr"])
        f.write("NETMASK='%s'\n" % props["network_netmask"])
        f.write("USERCONTROL=no\nFIREWALL=no\n")
        if( props["network_standalone_ipaddr2"] != ""):
            f.write("IPADDR_0='%s'\n" % props["network_standalone_ipaddr2"])
            f.write("NETMASK_0='%s'\n" % props["network_netmask"])
            f.write("LABEL_0='0'")
        f.close()


        f = file("/etc/sysconfig/network/routes", "w")
        f.write("default %s - -" % props["network_gateway"])
        f.close()

        os.system("/sbin/ifup " + interface + " > /dev/null")


if __name__ == "__main__":
    main()
