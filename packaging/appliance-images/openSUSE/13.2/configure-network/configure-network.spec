Name: configure-network
Version: %{product_version}
Release: %{product_release}
Summary: Network configuration for kiwi built appliances
Vendor: EMC
Group: emc/ionix/adg/tools
License: EMC IMG
Source0: configure-network.spec
Source1: parseOvfEnv.py
Source2: setNetwork
Source3: getOvfEnv
Source4: ovf.properties.example
Source5: README.configure-network.txt
URL: http://www.emc.com
BuildArch: noarch
BuildRoot: /rpm_build/%{name}

%description
Network configuration for kiwi built appliances

%prep
#  Remove build location in case prior build failed.
if [ -d ${RPM_BUILD_ROOT} ] ; then
    rm -rf ${RPM_BUILD_ROOT}
fi

dest_dir="/opt/ADG"
#  Copy files to local staging directory
mkdir -p ${RPM_BUILD_ROOT}${dest_dir}/bin ${RPM_BUILD_ROOT}${dest_dir}/etc/init.d ${RPM_BUILD_ROOT}${dest_dir}/conf
cp %SOURCE1 ${RPM_BUILD_ROOT}${dest_dir}/bin/parseOvfEnv.py
cp %SOURCE2 ${RPM_BUILD_ROOT}${dest_dir}/bin/setNetwork
cp %SOURCE3 ${RPM_BUILD_ROOT}${dest_dir}/etc/init.d/getOvfEnv
cp %SOURCE4 ${RPM_BUILD_ROOT}${dest_dir}/conf/ovf.properties.example
cp %SOURCE5 ${RPM_BUILD_ROOT}${dest_dir}/conf/README.configure-network.txt

mkdir -p ${RPM_BUILD_ROOT}/etc/init.d
ln -sf ${dest_dir}/etc/init.d/getOvfEnv ${RPM_BUILD_ROOT}/etc/init.d/boot.getOvfEnv

%pre

%post
insserv boot.getOvfEnv
#Repopulate network related file and restart network service ONLY FOR UPGRADE
if [ $1 -gt 1 ]; then
  for i in $(ifconfig -s | awk '{print $1}'| grep eth | sed s/eth//); do
    /bin/bash /opt/ADG/bin/setNetwork $i
  done
  service network restart
fi

%clean
rm -rf ${RPM_BUILD_ROOT}
rm -rf ${RPM_BUILD_DIR}

%files
%defattr(-,root,root,-)
/opt/ADG/bin/parseOvfEnv.py
/opt/ADG/bin/setNetwork
/opt/ADG/conf/ovf.properties.example
/opt/ADG/conf/README.configure-network.txt
/opt/ADG/etc/init.d/getOvfEnv
/etc/init.d/boot.getOvfEnv

%changelog
 * Wed Jan 6 2016 Rodrigo Oshiro <ApplianceDevelopmentGroup@emc.com> 0:3.5-1
 - Adding ovf.properties.example for manual network configuration

 * Thu Dec 3 2015 Rodrigo Oshiro <ApplianceDevelopmentGroup@emc.com> 0:3.4-3
 - Reorganizing SPEC to release it open source

 * Mon Aug 3 2015 Rodrigo Oshiro <ApplianceDevelopmentGroup@emc.com> 0:3.4-2
 - Fixing a bug to get the location of ifconfig card in single OVF mode

 * Wed Jul 15 2015 Rodrigo Oshiro <ApplianceDevelopmentGroup@emc.com> 0:3.4-1
 - Adding support to dynamic device names and dual NIC IPs

 * Wed Feb 11 2015 Padmakumar G Pillai <ApplianceDevelopmentGroup@emc.com> 0:3.3-1
 - When a non-emc OVF/VM is added to a vApp and the vApp is rebooted, the ovf parser script will die
 - ungracefully, if the VM does not have the node "vm.vmname" in the new entity in the ovf xml file.
 - Hence, changes were made to the parseOvfEnv.py and getOvfEnv scripts, so that when it encounters
 - a VM without the vm.vmname property, it skips that entity while doing the network configuration
 - and logs the appropriate error message with the name of the entity in the /var/log/boot.msg file.
 - (ADS-1944)

 * Thu Dec 18 2014 Padmakumar G Pillai <ApplianceDevelopmentGroup@emc.com> 0:3.2-1
 - Setting the short name of the host (without domain name as the suffix) as the hostname during the firstboot (ADS-1571)

 * Tue Nov 4 2014 Rodrigo Oshiro <ApplianceDevelopmentGroup@emc.com> 0:3.1-2
 - Fixing NIC size when dual OVFs are used.

 * Thu Oct 16 2014 Rodrigo Oshiro <ApplianceDevelopmentGroup@emc.com> 0:3.1-1
 - Updating netmask and prefixlen conflict when setting network.

 * Wed Jun 18 2014 Padmakumar G Pillai <ApplianceDevelopmentGroup@emc.com> 0:3.0-1
 - Added replaceChars() function to parseOvfEnv.py.
 - Added a logic to add a new property in the ovf.properties file called key_vmname.
 - key_vmname holds the vm_vmname value with "." and "-" replaced by "_".
 - Used the key_vmname property as the suffix for all the property names in the setNetwork script.

 * Thu Apr 17 2014 Padmakumar G Pillai <ApplianceDevelopmentGroup@emc.com> 0:2.2-1
 - Removed unused function getVmName() from parseOvfEnv.py
 - Added function indexVmName() in parseOvfEnv.py
 - Certain changes w.r.t ADG Naming Conventions and logic to parse the vApp OVF Environment.

 * Fri Apr 11 2014 Padmakumar G Pillai <ApplianceDevelopmentGroup@emc.com> 0:2.1-1
 - Updated the version to 2.1
 - Modified the parseOvfEnv.py to parse vApp OVF Environment.

 * Tue Feb 25 2014 Amulya <ApplianceDevelopmentGroup@emc.com> 0:2.0-1
 - Modified the setNetwork script for doing the Single and Dual stacked IP4 and IPv6 configuration

 * Fri Feb 11 2014 Raghav <ApplianceDevelopmentGroup@emc.com> 0:1.4-1
 - setNetwork script for setting loopback address in /etc/hosts

 * Tue Feb 04 2014 Padmakumar G Pillai <ApplianceDevelopmentGroup@emc.com> 0:1.3-1
 - Updated the version to 1.3
 - Modified the getOvfEnv file to check the existence of ovf-env.xml file once the CD-ROM is mounted successfully.

 * Fri Oct 18 2013 Mike <ApplianceDevelopmentGroup@emc.com> 0:1.2-1
 - Removed SNAPSHOT from version since this is now being used for production
 - Updated file names to have generic look and feel
 - Moved actual source files around in Subversion

 * Fri Oct 04 2013 Mike <ApplianceDevelopmentGroup@emc.com> 0:1.1-1.SNAPSHOT
 - Revamped version scheme
 - Content change in adg_set_network for variable parsing

 * Sun Sep 15 2013 Amulya <ApplianceDevelopmentGroup@emc.com> 0:1.0.0.0-3.SNAPSHOT
 - Added code to install the boot.getOvfEnv service

 * Tue Sep 03 2013 Amulya <ApplianceDevelopmentGroup@emc.com> 0:1.0.0.0-2.SNAPSHOT
 - Included the parseOvfEnv.py, adg_set_network.sh and getOvfEnv scripts.
 - Added code to create the symlink for the boot.getOvfEnv service.

 * Thu Aug 29 2013 Amulya <ApplianceDevelopmentGroup@emc.com> 0:1.0.0.0-1.SNAPSHOT
 - Installs the Network configuration files for the Kiwi built appliance.
