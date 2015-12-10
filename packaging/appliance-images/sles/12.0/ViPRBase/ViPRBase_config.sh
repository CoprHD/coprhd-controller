
#======================================
# Functions...
#--------------------------------------
test -f /.kconfig && . /.kconfig
test -f /.profile && . /.profile

# Calling the harden.pl script to run the default set of security
# hardening scripts provided by ADG. By default harden.pl will use
# the /opt/ADG/hardening/utils/defaultList file to determine what
# scripts to execute.  However, utilizing the "--file FILENAME"
# option will allow a user to specify their own file to process.
if [ -f /opt/ADG/hardening/utils/harden.pl ]
then
   perl /opt/ADG/hardening/utils/harden.pl
else
   echo Hardening script not found, adg-securty.rpm needs to be installed
fi

#######################################################
#Configure Firewall to allow ssh
#######################################################
sed -i 's/FW_SERVICES_EXT_TCP=""/FW_SERVICES_EXT_TCP="ssh"/' /etc/sysconfig/SuSEfirewall2
echo "Firewall configuration completed.."

#Enable sshd service
systemctl enable sshd

#Enable auditd service if installed
rpm -q audit >/dev/null
if [ $? -eq 0 ]; then
  systemctl enable auditd
fi

echo "Welcome to" `cat /etc/ImageVersion` > /etc/motd


#######################################################
#            Change swap /etc/fstab                   #
#######################################################

if [ ! -f /opt/ADG/firstboot/scripts/50SwapConfig.sh ]; then
  cat > /opt/ADG/firstboot/scripts/50SwapConfig.sh <<'VGSWAP'
#!/bin/bash
echo "Removing old mounts point from /etc/fstab"
sed -i "/LVswap/d" /etc/fstab

vgVar=`ls /dev/mapper/*-LVswap|cut -d"-" -f1|cut -d"/" -f4`
swapVar="/dev/mapper/$vgVar-LVswap"

echo "Adding new mount point $swapVar to /etc/fstab"
echo "$swapVar none swap defaults 0 0" >> /etc/fstab

umount -f /swap
mkswap -f $swapVar
swapon $swapVar
VGSWAP
  chmod 700 /opt/ADG/firstboot/scripts/50SwapConfig.sh
fi

#======================================
# Setup baseproduct link
#--------------------------------------
suseSetupProduct

#######################################################
# Starting to call funtions from configurations.sh    #
#######################################################

echo "Running common configurations" 
fix_users
vipr_svcuser
vipr_fix_svcuser_ssh
fix_data
fix_connectemc_data
adg_kernelMessage
adg_motd
fix_adg_security
fix_alias
fix_atop
fix_audit
fix_bootfs
fix_cacerts
fix_connectemc
fix_ifcfg
#fix_initd_connectemc
#fix_initd_network
fix_kiwi_storage_network
fix_network_config
fix_base_network_config
fix_patches
fix_permission
fix_rootdir
#LQ fix_rsyslog
fix_sshd_config
fix_symssl
fix_users
vipr_baseServices
vipr_removeUsers
vipr_removeGroups
vipr_removeUserwwnrun
vipr_RenamePasswd
#fix_kernel_modules
adg_bootClock
fix_enable_java_sslv3
vipr_fix_add_strongswan

#######################################################
# Ending to call funtions from configurations.sh      #
#######################################################

echo "Start: Creating the storageos directory"
cat > /usr/lib/systemd/system/varRunStorageos.service <<SERVICE
[Unit]
Description=Ensure /var/run/storageos exist after boot

[Service]
Type=oneshot
ExecStart=/usr/bin/mkdir -p /var/run/storageos
ExecStart=/usr/bin/chmod 0755 /var/run/storageos
ExecStart=/usr/bin/chown -Rh storageos:storageos /var/run/storageos
ExecStart=/usr/bin/systemctl disable varRunStorageos.service
ExecStart=/usr/bin/rm /usr/lib/systemd/system/varRunStorageos.service

[Install]
WantedBy=multi-user.target
SERVICE
systemctl enable varRunStorageos.service

echo "End: Creating the storageos directory"

# Disable services wickedd-dhcp4, wickedd-dhcp6:
systemctl disable wickedd-dhcp4
systemctl disable wickedd-dhcp6

# Removed the floppy 
echo "blacklist floppy" > /etc/modprobe.d/blacklist-floppy.conf

#======================================
# Umount kernel filesystems
#--------------------------------------
baseCleanMount

exit 0
