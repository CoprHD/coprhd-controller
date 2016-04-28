
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
# Functions;  custom functions
#######################################################
function setupSystemService
{
  chkconfig $1 $2
}

function applyConfiguration
{
  [ -x $1 ] && $1
}

#######################################################
# Initialize service states
#######################################################
setupSystemService mysql on
setupSystemService apache2 on
setupSystemService rabbitmq-server on
setupSystemService memcached on
setupSystemService openstack-cinder-api on
setupSystemService openstack-cinder-scheduler on
setupSystemService openstack-cinder-volume on

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

#======================================
# Setup baseproduct link
#--------------------------------------
suseSetupProduct

#######################################################
# Starting to call funtions from configurations.sh    #
#######################################################

echo "Running common configurations" 
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
fix_rootdir
adg_bootClock
fix_enable_java_sslv3
vipr_fix_add_strongswan

# Removed the floppy 
echo "blacklist floppy" > /etc/modprobe.d/blacklist-floppy.conf

#======================================
# Umount kernel filesystems
#--------------------------------------
baseCleanMount

exit 0
