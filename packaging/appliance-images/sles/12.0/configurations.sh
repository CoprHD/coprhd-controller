
#!/bin/bash
#
# Copyright (c) 2015, EMC Corporation. All Rights Reserved.
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.
# This software is protected, without limitation, by copyright law and
# international treaties.
# Use of this software and the intellectual property contained therein
# is expressly limited to the terms and conditions of the License
# Agreement under which it is provided by or on behalf of EMC.
#

#======================================
# Helper functions
#--------------------------------------
fatal() {
    echo "$0: Error: $*" >&2; exit 1
}

xsed() {
    local file=${1}
    local temp='temp' # path to a temporaty file
    shift
    [ -f "${file}" ] || fatal "${file}: No such file"
    echo "*** Fixing ${file}" >&2
    sed "${@}" <"${file}" >"${temp}" && cat "${temp}" >"${file}" && rm "${temp}" || fatal "Internal error: ${FUNCNAME[1]}"
}

xcat() {
    local file=${1}
    [ -f "${file}" ] || fatal "${file}: No such file"
    echo "*** Fixing ${file}" >&2
    echo "${2}" >"${file}"
}

setUserBinPath() {
    local RUSER=$1
    local RPROF=/home/$1/.profile

    if [ ! -f $RPROF ] ; then
        echo "ERROR: User ${RUSER} either does not exist or profile is missing."
        return 1
    fi

    if grep "PATH.*/home/${RUSER}/bin" $RPROF
    then
        :; # already completed
    else
        echo >> $RPROF
        echo "PATH=\$PATH:/home/${RUSER}/bin" >> $RPROF
    fi
}


#======================================
# Fix the boot.clock start up
#--------------------------------------
adg_bootClock() {
    echo "*** Modifying /etc/adjtime" >&2
	[ -f /etc/adjtime ] || touch /etc/adjtime 
    xcat /etc/adjtime \
'0.0 0 0.0
0
UTC
'
}

#======================================
# ADG standard DevKit configuration.
#--------------------------------------
adg_DevKitConfig() {
    echo "Executing adg_DevKitConfig"
#    #######################################################
#    #            Initialize service states                #
#    #######################################################
#    chkconfig tomcat6 off
#    chkconfig apache2 off

    #######################################################
    #          Links to apache2 directories               #
    #######################################################
    if [ ! -d /srv/www/modules ]; then
        ln -s /usr/lib64/apache2 /srv/www/modules
    fi

    if [ ! -d /srv/www/logs ]; then
        ln -s /var/log/apache2/  /srv/www/logs
    fi

    ########################################################
    #                Add tools to PATH                     #
    ########################################################
    echo 'PATH=$PATH:/opt/apache-maven/bin' >> /etc/profile
}

#======================================
# ADG configuration for DevKit HOME/END keys.
#--------------------------------------
adg_DevKitHomeEndKeys() {
    #######################################################
    #            Configure Home/End Keys                  #
    #######################################################
    cp /etc/inputrc /etc/inputrc-original
    LineNo=$(grep -A 5 -n ".*Normal keypad and cursor of xterm" /etc/inputrc | grep history-search-backward | cut -d- -f1)
    LineNo="$LineNo "$(grep -A 5 -n ".*Normal keypad and cursor of xterm" /etc/inputrc | grep set-mark | cut -d- -f1)
    for ln in $LineNo; do sed -i "$ln s/.*/#&/" /etc/inputrc; done
}

adg_kernelMessage() {
    if [ -f /boot/do_purge_kernels ] ; then
       echo "*** Fixing Purge Kernels console screen message" >&2
       xsed /etc/zypp/zypp.conf '
       s/^\# multiversion = provides:multiversion/multiversion = provides:multiversion/
       s/^\# multiversion.kernels = latest,running/multiversion.kernels = latest,running/'
    fi
}

adg_KIWIMods() {
    #######################################################
    # KIWI Modifications                                  #
    #######################################################
    kiwiPath="/usr/share/kiwi/modules"

    #######################################################
    # Changes for 3 digits to 5 digits version
    FileValidator="$kiwiPath/KIWIXMLValidator.pm"
    if [ -f $FileValidator ]; then
     echo "Modifying to fix version check for $FileValidator"
     sed -i."bak" 's/\($version !~ \/^\\d+\\.\\d+\\.\\d+$\/\)/\($version !~ \/^\\d+\\.\\d+\\.\\d+$\/\) \&\& \($version !~ \/^\\d+\\.\\d+\\.\\d+\\.\[a-zA-Z0-9_-]+\\.\[a-zA-Z0-9_-]+$\/\)/g'  $FileValidator
    fi

    FilePreference="$kiwiPath/KIWIXMLPreferenceData.pm"
    if [ -f $FilePreference ]; then
     echo "Modifying to fix version check for $FilePreference"
     sed -i."bak" 's/\( $ver !~ \/^\\d+?\\.\\d+?\\.\\d+?$\/smx \)/\( $ver !~ \/^\\d+?\\.\\d+?\\.\\d+?$\/smx \) \&\& \( $ver !~ \/^\\d+?\\.\\d+?\\.\\d+?\\.\[a-zA-Z0-9_-]+?\\.\[a-zA-Z0-9_-]+?$\/smx \)/g' $FilePreference
    fi

    FileArchiveBuilder="$kiwiPath/KIWITarArchiveBuilder.pm"
    if [ -f $FileArchiveBuilder ]; then
     echo "Modifying to fix version check for $FileArchiveBuilder"
     xsed $FileArchiveBuilder 's/--exclude=image/--exclude=\.\/image/'
    fi

    #######################################################
    # Modify zypper cache behaviour
    sed -i "s|my @cache = (\"/var/cache/kiwi\");|my \$packageCache = (dirname \$root) . \"/packages\";\n\tmy @cache = (\"/var/cache/kiwi\", \$packageCache);|" $kiwiPath/KIWIRoot.pm

    linenum=`grep -n "# KIWI Modules" $kiwiPath/KIWIRoot.pm|cut -d ":" -f1`
    let newnum=$linenum-1
    sed -i "$newnum i use File::Basename;" $kiwiPath/KIWIRoot.pm

    linenum=`grep -n "Store zypper command parameters" $kiwiPath/KIWIManagerZypper.pm|cut -d ":" -f1`
    let newnum=$linenum+2
    sed -i "$newnum i my \$packageCache = (dirname \$root) . \"/packages\";" $kiwiPath/KIWIManagerZypper.pm
    sed -i "s|'--pkg-cache-dir /var/cache/kiwi/packages'|\"--pkg-cache-dir \$packageCache\"|g" $kiwiPath/KIWIManagerZypper.pm
    sed -i "s|my @zopts = ();|my @zopts = (\"--keep-packages\");|" $kiwiPath/KIWIManagerZypper.pm
    sed -i -e '/keep packages on remote repos/,+6d' $kiwiPath/KIWIManagerZypper.pm
}

#======================================
# ADG set motd.
#--------------------------------------
adg_motd() {
    echo "Executing adg_motd"
    echo "Welcome to" $(cat /etc/ImageVersion) > /etc/motd
}

#======================================
# HACK - ovftool install
#--------------------------------------
adg_ovftoolInstall() {
    rpm -Uvh http://pld-imgapprd01.isus.emc.com:8081/artifactory/adg-icc/x86_64/vmware-ovftool/3.5.0-1274719/VMware-ovftool-3.5.0-1274719.x86_64.rpm
}

#======================================
# ADG standard zypper repos (currently SLES 11.3 plus some common ones)
#--------------------------------------
adg_zypperRepos() {
    #######################################################
    #            Configure Zypper Repos                   #
    #######################################################
    zypper addrepo http://lglob090.lss.emc.com/SLES12/SDK/ sles12_sdk
    zypper addrepo http://lglob090.lss.emc.com/SLES12/SLE/ sles12
    zypper addrepo http://asdrepo.isus.emc.com:8081/artifactory/adg-catalog/common/generic/linux icc
    zypper addrepo http://asdrepo.isus.emc.com:8081/artifactory/adg-catalog/sles/12 sles12_icc
    zypper addrepo http://asdrepo.isus.emc.com:8081/artifactory/adg-catalog/common/sles/12 sles12_common
}

#======================================
# "fix" functions
# NOTE:
# All "fix" functions below are expected to have idempotent behavior
# so that the script can be executed multiple times safely.
#--------------------------------------

fix_adg_security() {
    echo "*** Fixing Running the ADG harden scripts" >&2
    # Create the list of scripts that will be executed:
    [ -f /opt/ADG/hardening/utils/vipr-scripts.list ] || touch /opt/ADG/hardening/utils/vipr-scripts.list
    xcat /opt/ADG/hardening/utils/vipr-scripts.list '# List the name of scripts to be executed below
CVE_2004_1653.sh - Remote SSH server may permit anonymous port bouncing
GEN000290_1.sh - The system must not have unnecessary accounts: Remove "games" account
GEN000290_2.sh - The system must not have unnecessary accounts: Remove "news" account
GEN000290_3.sh - The system must not have unnecessary accounts: Remove "gopher" account
GEN000290_4.sh - The system must not have unnecessary accounts: Remove "ftp" account
GEN000480.sh - The delay between login prompts following a failed login attempt must be at least 4 seconds.
GEN000590.sh - The system must use a FIPS 140-2 approved cryptographic hashing algorithm for generating account password hashes.
GEN001480.sh - All user home directories must have mode 0750 or less permissive 
GEN001480.sh - Will ensure that all users home directories have permissions of 0750
GEN001560.sh - Ensures all files within interactive home directories have mode 0750 or less permissive
GEN001780.sh - Appends mesg n to /etc/profile so messaging will not be used to create a DoS attack
GEN002040.sh - There must be no .rhosts, .shosts, hosts.equiv, or shosts.equiv files on the system - hosts.equiv
GEN002060.sh - All .rhosts, .shosts, .netrc, or hosts.equiv files must be accessible by only root or the owner - hosts.equiv
GEN002440.sh - The owner, group-owner, mode, ACL and location of files with the 'sgid' bit set must be documented.
GEN002560.sh - The system and user default umask must be 077
GEN002690.sh - Sets the audit log file owner to root if it is not set to root, bin, sys or system
GEN002700.sh - System audit logs must have mode 0640 or less permissive.
GEN002717.sh - System audit tool executables must have mode 0750 or less permissive -  /sbin/auditctl
GEN002719.sh - The audit system must alert the SA in the event of an audit processing failure -  /etc/audit/auditd.conf disk_full_action
GEN002720.sh - The audit system must be configured to audit failed attempts to access files and programs -
GEN002720_2.sh - To audit failed attempts to access files and programs -  -S open -F exit=-EPERM  and  -S creat -F exit=-EACCES
GEN002720_3.sh - To audit failed attempts to access files and programs -  -S openat -F exit=-EPERM  and  -S creat -F exit=-EACCES
GEN002720_4.sh - To audit failed attempts to access files and programs -  -S truncate -F exit=-EPERM  and  -S creat -F exit=-EACCES
GEN002720_5.sh - To audit failed attempts to access files and programs -  -S ftruncate -F exit=-EPERM  and  -S creat -F exit=-EACCES
GEN002740.sh - Sets the unlink command to be audited
GEN002740.sh - The audit system must be configured to audit files and programs deleled by the user -  unlink
GEN002740_2.sh - Sets the rmdir command to be audited
GEN002740_2.sh - The audit system must be configured to audit files and programs deleted by the user -  rmdir
GEN002750.sh - The audit system must be configured to audit account creation -  useradd  and  groupadd
GEN002751.sh - The audit system must be configured to audit account modification -  usermod  and  groupmod
GEN002753.sh - The audit system must be configured to audit account termination -  userdel  and  groupdel
GEN002760.sh - The audit system must be configured to audit all administrative, privileged, and security actions -  /etc/audit/auditd.conf
GEN002760_10.sh - The audit system must be configured to audit all administrative, privileged, and security actions -  sched_setscheduler
GEN002760_2.sh - The audit system must be configured to audit all administrative, privileged, and security actions -  /etc/audit/audit.rules
GEN002760_3.sh - The audit system must be configured to audit all administrative, privileged, and security actions -  adjtimex
GEN002760_4.sh - The audit system must be configured to audit all administrative, privileged, and security actions -  settimeofday
GEN002760_6.sh - The audit system must be configured to audit all administrative, privileged, and security actions -  clock_settime
GEN002760_7.sh - The audit system must be configured to audit all administrative, privileged, and security actions -  sethostname
GEN002760_8.sh - The audit system must be configured to audit all administrative, privileged, and security actions -  setdomainname
GEN002760_9.sh - The audit system must be configured to audit all administrative, privileged, and security actions -  sched_setparam
GEN002800.sh - The audit system must be configured to audit login, logout, and session initiation -  faillog
GEN002820.sh - The audit system must be configured to audit all discretionary access control permission modifications -  chmod
GEN002820_10.sh - The audit system must be configured to audit all discretionary access control permission modifications -  fsetxattr
GEN002820_11.sh - The audit system must be configured to audit all discretionary access control permission modifications -  removexattr
GEN002820_12.sh - The audit system must be configured to audit all discretionary access control permission modifications -  lremovexattr
GEN002820_13.sh - The audit system must be configured to audit all discretionary access control permission modifications -  fremovexattr
GEN002820_2.sh - The audit system must be configured to audit all discretionary access control permission modifications -  fchmod
GEN002820_3.sh - The audit system must be configured to audit all discretionary access control permission modifications -  fchmodat
GEN002820_4.sh - The audit system must be configured to audit all discretionary access control permission modifications -  chown
GEN002820_5.sh - The audit system must be configured to audit all discretionary access control permission modifications -  fchown
GEN002820_6.sh - The audit system must be configured to audit all discretionary access control permission modifications -  fchownat
GEN002820_7.sh - The audit system must be configured to audit all discretionary access control permission modifications -  lchown
GEN002820_8.sh - The audit system must be configured to audit all discretionary access control permission modifications -  setxattr
GEN002820_9.sh - The audit system must be configured to audit all discretionary access control permission modifications -  lsetxattr
GEN002825.sh - The audit system must be configured to audit the loading and unloading of dynamic kernel modules -  init_module
GEN002825_2.sh - The audit system must be configured to audit the loading and unloading of dynamic kernel modules -  delete_module
GEN002825_3.sh - The audit system must be configured to audit the loading and unloading of dynamic kernel modules -  insmod
GEN002825_4.sh - The audit system must be configured to audit the loading and unloading of dynamic kernel modules -  modprobe
GEN002825_5.sh - The audit system must be configured to audit the loading and unloading of dynamic kernel modules -  rmmod
GEN003060.sh - System accounts must not be listed in cron.allow or must be included in cron.deny, if cron.allow does not exist
GEN003080.sh - Sets crontab file permssions to be no more permissive than 700
GEN003252.sh - The at.deny file must have mode 0600 or less permissive.
GEN003460.sh - The at.allow file must be owned by root, bin, or sys.
GEN003581.sh - Network interfaces must not be configured to allow user control.
GEN003600.sh - The system must not forward IPv4 source_routed packets.
GEN003601.sh - TCP backlog queue sizes must be set appropriately.
GEN003602.sh - The system must not process Internet Control Message Protocol (ICMP) timestamp requests.
GEN003607.sh - The system must not accept source_routed IPv4 packets.
GEN003608.sh - Proxy ARP must not be enabled on the system
GEN003609.sh - The system must ignore IPv4 Internet Control Message Protocol (ICMP) redirect messages.
GEN003610.sh - The system must not send IPv4 Internet Control Message Protocol (ICMP) redirects.
GEN003611.sh - The system must log martian packets.
GEN003612.sh - The system must be configured to use TCP syncookies when experiencing a TCP SYN flood.
GEN003740.sh - The inetd.conf and xinetd.conf files must have mode 0440 or less permissive.
GEN004000.sh - The traceroute file must have mode 0700 or less permissive.
GEN005400.sh - /etc/syslog.conf Assessibility - The /etc/syslog.conf is not owned by root or is more permissive than 640.
GEN005505.sh - The SSH daemon must be configured to only use FIPS 140-2 approved ciphers.
GEN005507.sh - The SSH daemon must be configured to only use Message Authentication Codes (MACs) employing FIPS 140-2 approved cryptographic hash algorithms
GEN005510.sh - The SSH client must be configured to only use FIPS 140-2 approved ciphers.
GEN005512.sh - The SSH client must be configured to only use Message Authentication Codes (MACs) employing FIPS 140-2 approved cryptographic hash algorithms
GEN005521.sh - The SSH daemon must restrict login ability to specific users and/or groups
GEN007020.sh - The Stream Control Transmission Protocol (SCTP) must be disabled unless required.
GEN007080.sh - The Datagram Congestion Control Protocol (DCCP) must be disabled unless required
GEN007260.sh - The AppleTalk protocol must be disabled or not installed.
GEN007480.sh - The Reliable Datagram Sockets (RDS) protocol must be disabled or not installed unless required
GEN007540.sh - The Transparent Inter-Process Communication (TIPC) protocol must be disabled or uninstalled
GEN007660.sh - The Bluetooth protocol handler must be disabled or not installed
GEN007860.sh - The system must ignore IPv6 ICMP redirect messages
GEN007950.sh - The system must not respond to ICMPv6 echo requests sent to a broadcast address
GEN008480.sh - Ensures that USB Mass Storage is disabled
GEN008500.sh - Ensures that IEEE 1394 (Firewire) is disabled
LNX000580.sh - This script disallows system shutdown by disabling the ctrl-alt-delete keys in the /etc/inittab file.
LNX00440.sh - The /etc/security/access.conf file must have mode 0640 or less permissive.
LNX00520.sh - The /etc/sysctl.conf file must have mode 0600 or less permissive.
LNX00660.sh - The /etc/securetty file must have mode 0640 or less permissive.
'
    chmod 640 /opt/ADG/hardening/utils/vipr-scripts.list

    # Run the ADG harden script now
    perl /opt/ADG/hardening/utils/harden.pl -f /opt/ADG/hardening/utils/vipr-scripts.list

    # Call the harden scripts directly with input parameters below:
    # GEN006620 - The system access control program must be configured to grant or deny system access to specific hosts
    /bin/bash /opt/ADG/hardening/scripts/GEN006620.sh -v 'sshd: ALL'
    # GEN000460 - Lock user account after 3 failed logins, reset after 10 minutes
    /bin/bash /opt/ADG/hardening/scripts/GEN000460.sh -v 600

    # Clean up extra files left behind in removal of the "news" account
    rm -r /etc/news
    rm -r /var/lib/news

    # Clean up home directory from emcupdate user
    rm -r /home/emcupdate
}

fix_alias() {
    [ -f /etc/bash.bashrc.local ] || touch /etc/bash.bashrc.local
    xcat  "/etc/bash.bashrc.local" \
	"
alias -- +='pushd .'
alias -- -='popd'
alias ..='cd ..'
alias ...='cd ../..'
alias beep='echo -en \"\\007\"'
alias cd..='cd ..'
alias dir='ls -l'
alias l='ls -alF'
alias la='ls -la'
alias ll='ls -l'
alias ls='ls $LS_OPTIONS'
alias ls-l='ls -l'
alias md='mkdir -p'
alias o='less'
alias rd='rmdir'
alias rehash='hash -r'
alias unmount='echo \"Error: Try the command: umount\" 1>&2; false'
alias you='if test \"\$EUID\" = 0 ; then /sbin/yast2 online_update ; else su - -c \"/sbin/yast2 online_update\" ; fi'
"
}

fix_apache() {
    [ -f /etc/apache2/conf.d/site.conf ] || touch /etc/apache2/conf.d/site.conf
    xcat /etc/apache2/conf.d/site.conf \
'# /workspace
<Directory /workspace>
    AllowOverride FileInfo AuthConfig Limit
    Options MultiViews Indexes SymLinksIfOwnerMatch IncludesNoExec
    <Limit GET POST OPTIONS>
        Order allow,deny
        Allow from all
    </Limit>
    <LimitExcept GET POST OPTIONS>
        Order deny,allow
        Deny from all
    </LimitExcept>
</Directory>

Alias /workspace/ "/workspace/"
'

    # Move apache to port 8081.
    if [ -f /etc/apache2/listen.conf ] ; then
        xsed /etc/apache2/listen.conf 's/Listen 80$/Listen 8081/'
    fi
}

fix_atop() {
       xsed /etc/atop/atop.daily '
    /^INTERVAL=600/aif [ ! -d $LOGPATH ]; then\n   mkdir -p $LOGPATH\n   chmod 755 $LOGPATH \nfi'
}

fix_audit() {
    echo "*** Fixing audit"
    if [ -f /etc/sysconfig/auditd ] ; then
         echo "*** Setting AUDITD_DISABLE_CONTEXTS to no" >&2
         xsed /etc/sysconfig/auditd 's/^AUDITD_DISABLE_CONTEXTS=.*/AUDITD_DISABLE_CONTEXTS="no"/'
    fi
	
    if [ -f /etc/audit/auditd.conf ] ; then
         echo "*** Modifying auditd.conf" >&2
         xsed /etc/audit/auditd.conf '
         s/^max_log_file = .*//
         s/^max_log_file_action = .*/max_log_file_action = IGNORE/'
    fi

   if [ -f /etc/audit/audit.rules ] ; then
         echo "*** Removing duplicate in audit.rules" >&2
         perl -ne 'print unless $duplicate{$_}++' /etc/audit/audit.rules > temp.rules
         mv temp.rules /etc/audit/audit.rules
         chmod 640 /etc/audit/audit.rules
    fi
}

fix_autofs() {
    xsed /etc/auto.master '
s/+auto.master/\/net	-hosts/'
}

fix_bootfs() {
    echo "*** Fixing /.volumes/bootfs/etc" >&2
    mkdir -p  /.volumes/bootfs/etc
    chmod 755 /.volumes
    chmod 755 /.volumes/bootfs
    chmod 755 /.volumes/bootfs/etc
}

# Create a read-only copy of the original JRE cacerts keytore, and create a PEM format of it for openssl
#
fix_cacerts() {
    local keytool=/etc/alternatives/jre_oracle/bin/keytool
    local cacerts=/etc/alternatives/jre_oracle/lib/security/cacerts

    if [ ! -f "${cacerts}.orig" ] ; then
         echo "*** Fixing ${cacerts}.orig" >&2
         cp -a "${cacerts}" "${cacerts}.orig"
    fi
    if [ ! -f "${cacerts}.pem"  ] ; then
         echo "*** Fixing ${cacerts}.pem"  >&2
         for f in $(${keytool} -list -keystore "${cacerts}.orig" -storepass changeit | sed -n 's/,.*//p') ; do
              ${keytool} -exportcert -rfc -alias "${f}" -keystore "${cacerts}.orig" -storepass changeit
         done >"${cacerts}.pem"
    fi
}

fix_connectemc() {
    for d in archive failed history logs output poll queue recycle tmp ; do
        local lnk="/opt/connectemc/${d}"
        local dir="/data/connectemc/${d}"
        [ -L "${lnk}" -a "$(readlink ${lnk})" = "${dir}" ] || {
             rm -rf "${lnk}" && ln -s "${dir}" "${lnk}"
        }
    done
    chown -Rh 444:444 /opt/connectemc
}

fix_connectemc_data() {
    for d in archive failed history logs output poll queue recycle tmp ; do
        local dir="/data/connectemc/${d}"
        mkdir -p -m 0755 "${dir}"
    done
    chown -Rh 444:444 /data/connectemc
}

fix_data() {
    echo "*** Fixing /data" >&2
    mkdir -p                  /data
    chown storageos:storageos /data
    chmod 755                 /data
}

fix_devkit_firewall() {
    if [ -f /etc/init.d/SuSEfirewall2_setup ] ; then
        xsed /etc/init.d/SuSEfirewall2_setup 's/\# Should-Start:   $ALL/\# Should-Start:   $null/'
    fi
}

# fix root umask for devkit
#  Required negate global umask settings in dev environment
fix_devkit_root_umask() {
    if [ -f /root/.bashrc ] ; then
        if grep umask /root/.bashrc ; then
            xsed /root/.bashrc 's/umask .*/umask 022/'
        else
            xcat /root/.bashrc 'umask 022'
        fi
    else
        touch /root/.bashrc
        xcat /root/.bashrc 'umask 022'
    fi
}

# Fix /etc/sysconfig/network/ifcfg-eth0
#
fix_ifcfg() {
    [ -f /etc/sysconfig/network/ifcfg-eth0 ] || touch /etc/sysconfig/network/ifcfg-eth0
    xcat /etc/sysconfig/network/ifcfg-eth0 \
'DEVICE=eth0
BOOTPROTO=static
STARTMODE=auto'
}

# Fix /etc/init.d/connectemc
#
fix_initd_connectemc() {
    touch /etc/sysconfig/connectemc
    xcat  /etc/sysconfig/connectemc '#
# /etc/sysconfig/connectemc
#
CONNECTEMC_USER=444
CONNECTEMC_GROUP=444
'

    xcat  /etc/init.d/connectemc '#!/bin/sh

### BEGIN INIT INFO
# Provides:                     connectemc
# Required-Start:               $network
# Should-Start:
# Should-Stop:
# Required-Stop:
# Default-Start:                3 5
# Default-Stop:                 0 1 2 6
# Short-Description:            ConnectEMC
# Description:                  Start the ConnectEMC daemon
### END INIT INFO

# Setup environment
test -s /etc/rc.status && . /etc/rc.status && rc_reset

# Load custom settings, also set $HOME if needed
test -s /etc/sysconfig/connectemc && . /etc/sysconfig/connectemc

# Service variables
CONNECTEMC_USER=${CONNECTEMC_USER:-root}
CONNECTEMC_GROUP=${CONNECTEMC_GROUP:-root}

# Paths
CONNECTEMC_PROGRAM=/opt/connectemc/connectemc
CONNECTEMC_PIDFILE=/var/run/connectemc.pid
CONNECTEMC_LOGFILE=/var/log/connectemc.log

_startproc() {
    startproc -c / -l "${CONNECTEMC_LOGFILE}" -p "${CONNECTEMC_PIDFILE}" -u ${CONNECTEMC_USER} -g ${CONNECTEMC_GROUP} "${CONNECTEMC_PROGRAM}" && \
         pgrep -f "${CONNECTEMC_PROGRAM}" >"${CONNECTEMC_PIDFILE}"
}

_killproc() {
    killproc "${CONNECTEMC_PROGRAM}" && \
        rm -f "${CONNECTEMC_PIDFILE}"
}

_checkproc() {
    checkproc -p "${CONNECTEMC_PIDFILE}" "${CONNECTEMC_PROGRAM}"
    local status=$?
    case ${status} in
        0) echo "ConnectEMC service is running (pid=$(<${CONNECTEMC_PIDFILE}))";;
        *) echo "ConnectEMC service is not running";;
    esac
    return ${status}
}


# Actions
#
case "$1" in
    start)
        echo -n "Starting ConnectEMC service"
        _startproc
        rc_status -v
        ;;
    stop)
        echo -n "Stopping ConnectEMC service"
        _killproc
        rc_status -v
        ;;
    restart)
        $0 stop
        $0 start
        ;;
    status)
        _checkproc
        rc_status
        ;;
    *)
        echo $"Usage: $0 {start|stop|restart|status}"
        exit 1
esac
rc_exit
'
}

# Fix /etc/init.d/network
#
fix_initd_network() {
    xsed /etc/init.d/network '
/Required-Start:\|Required-Stop:/ s/\sdbus\(\s\|$\)//'
}

# Fix the list of kernel modules in initrd
# - add PV modules
# - some modules seem still unnecessary but I don't have time to test it now
#
fix_kernel_modules() {
    xsed /etc/sysconfig/kernel '
s/^INITRD_MODULES=.*/INITRD_MODULES="vmxnet3 vmw_pvscsi mptspi ata_generic processor jbd ext3"/'
    mkinitrd
}

fix_kiwi_storage_network() {
    echo "*** Fixing kiwi_storage_network"
    if [ -f /.volumes/bootfs/etc/ovf-env.properties ] ; then
         echo "*** Removing /.volumes/bootfs/etc/ovf-env.propertes" >&2
         rm -rf /.volumes/bootfs/etc/ovf-env.properties
    fi
}

# Fix /etc/sysconfig/network/config on both base and devkit
#
fix_network_config() {
    rm -f /etc/resolv.conf /etc/resolv.conf.netconfig
    xsed /etc/sysconfig/network/config '
s/^NETCONFIG_MODULES_ORDER=.*/NETCONFIG_MODULES_ORDER="dns-resolver dns-bind ntp-runtime"/
s/^NETCONFIG_DNS_STATIC_SEARCHLIST=.*/NETCONFIG_DNS_STATIC_SEARCHLIST=""/
s/^NETCONFIG_DNS_STATIC_SERVERS=.*/NETCONFIG_DNS_STATIC_SERVERS=""/'
}

# Fix /etc/sysconfig/network/config on base (in addition to common)
#
fix_base_network_config() {
    xsed /etc/sysconfig/network/config '
s/^CHECK_DUPLICATE_IP=.*/CHECK_DUPLICATE_IP="no"/'
}

fix_permission() {
    if [ -d /var/run/PolicyKit ] ; then
         echo "*** Setting /var/run/PolicyKit to 0750" >&2
         chmod 0750 /var/run/PolicyKit
    fi
}

fix_rootdir() {
    echo "*** Fixing /" >&2
    chmod 1777 /
}

fix_rsyslog() {
	rm -rf /etc/rsyslog.conf
	touch /etc/rsyslog.conf
	xcat  /etc/rsyslog.conf "
\$ModLoad imudp          # Module to support UDP remote messages inbound
\$UDPServerAddress *     # Listen to any/all inbound IP addresses (note that the * is default, specifying to make config clear)
\$UDPServerRun 514       # Listen on port 514

\$ModLoad immark.so     # provides --MARK-- message capability (every 1 hour)
\$MarkMessagePeriod     3600

\$ModLoad imuxsock.so   # provides support for local system logging (e.g. via logger command)
                       # reduce dupplicate log messages (last message repeated n times)
\$RepeatedMsgReduction on

\$ModLoad imklog.so     # kernel logging (may be also provided by /sbin/klogd),
                       # see also http://www.rsyslog.com/doc-imklog.html.
\$klogConsoleLogLevel 1 # set log level 1 (same as in /etc/sysconfig/syslog).

# Define template for vipr logs (messages, auth): 
\$template vipr_log_format,\"%TIMESTAMP:1:4:date-mysql%-%TIMESTAMP:5:6:date-mysql%-%TIMESTAMP:7:8:date-mysql% %TIMESTAMP:8:15:% [%syslogfacility-text%] %syslogseverity-text% %syslogtag%%msg:::sp-if-no-1st-sp%%msg:::drop-last-lf%\n\"

# Define template for vipr alert logs (systemevents):
\$template vipr_alerts_log_format,\"%TIMESTAMP:1:4:date-mysql%-%TIMESTAMP:5:6:date-mysql%-%TIMESTAMP:7:8:date-mysql% %TIMESTAMP:8:15:% [%syslogfacility-text%] %syslogseverity-text% ALERTS: %syslogtag%%msg:::sp-if-no-1st-sp%%msg:::drop-last-lf%\n\"

# Use traditional log format by default. 
\$ActionFileDefaultTemplate RSYSLOG_TraditionalFileFormat

# Include config generated by /etc/init.d/syslog script
# using the SYSLOGD_ADDITIONAL_SOCKET* variables in the
# /etc/sysconfig/syslog file.
\$IncludeConfig /var/run/rsyslog/additional-log-sockets.conf

# Include config files, that the admin provided? :
\$IncludeConfig /etc/rsyslog.d/*.conf

# print most important on tty10 and on the xconsole pipe
if ( \\
        /* kernel up to warning except of firewall  */ \\
        (\$syslogfacility-text == 'kern')      and      \\
        (\$syslogseverity <= 4 /* warning */ ) and not  \\
        (\$msg contains 'IN=' and \$msg contains 'OUT=') \\
    ) or ( \\
        /* up to errors except of facility authpriv */ \\
        (\$syslogseverity <= 3 /* errors  */ ) and not  \\
        (\$syslogfacility-text == 'authpriv')           \\
    ) \\
then  /dev/tty10
& |/dev/xconsole

# Emergency messages to everyone logged on (wall)
*.emerg  :omusrmsg:*

# Added for \"local7\" logging (All warn and more urgent go to file)
local7.warn /var/log/messages;vipr_log_format

# cron messages to dedicated cron log (STIG GEN003160)
\$FileCreateMode 0600
cron.* -/var/log/cron

# firewall messages into separate file and stop their further processing
if             (\$syslogfacility-text == 'kern') and \\
                (\$msg contains 'IN=' and \$msg contains 'OUT=') \\
then      -/var/log/firewall
& ~

# acpid messages into separate file and stop their further processing
if             (\$programname == 'acpid' or \$syslogtag == '[acpid]:') and \\
                (\$syslogseverity <= 5 /* notice */) \\
then      -/var/log/acpid
& ~

# NetworkManager into separate file and stop their further processing
if      (\$programname == 'NetworkManager') or \\
                (\$programname startswith 'nm-') \\
then      -/var/log/NetworkManager
& ~

# email-messages (log and stop further processing)
mail.*  -/var/log/mail
& ~

# news-messages (log and stop further processing)
news.* ~

# Warnings in one file (except iptables logged above)
*.=warning;*.=err  -/var/log/warn
*.crit              /var/log/warn

# All messages except iptables and the facilities news and mail logged above
\$FileOwner root
\$FileGroup root
\$FileCreateMode 0640
*.*;mail.none;news.none   -/var/log/messages;vipr_log_format
\$FileOwner root
\$FileGroup root
\$FileCreateMode 0644

# Some foreign boot scripts require local7
local0,local1.*  -/var/log/localmessages
local2,local3.*  -/var/log/localmessages
local4,local5.*  -/var/log/localmessages
local6,local7.*  -/var/log/localmessages

# Log file for Bourne node AUTH facility - all messages from auth facility
\$FileOwner root
\$FileGroup storageos
\$FileCreateMode 0640
auth.* /var/log/auth;vipr_log_format

#Alerts use local7 - will capture warn or more severe
local7.warn /var/log/systemevents;vipr_alerts_log_format

# webstorage; anything that contains the string \"[ws_native]\" written to the /opt/storageos/logs/blobsvc_native.log, then messages are discarded
\$FileOwner root
\$FileGroup root
\$FileCreateMode 0644
:msg, contains, \"[ws_native]\" /opt/storageos/logs/blobsvc_native.log
& ~

# storageserver destination: Anything that contains \"storageserver\" is logged and then discarded
\$FileOwner storageos
\$FileGroup storageos
\$FileCreateMode 0664
:msg, contains, \"storageserver\" /var/log/storageserver.log
& ~

## END of file
"
}

# Fix SSH host keys and authorized_keys2
#
fix_ssh() {
    mkdir -p -m 700 /root/.ssh
    [ -f /root/.ssh/id_dsa           ] || touch /root/.ssh/id_dsa
    chmod 700 /root/.ssh/id_dsa
    xcat /root/.ssh/id_dsa '-----BEGIN DSA PRIVATE KEY-----
MIIBuwIBAAKBgQDGtcUIk22WPi536lRqv7lou7t/NrSkvjnj7CjqSs1utQmoiwg8
2trPWTO4CRKz8SyOIXObLdWNN2J53bStch5TJ/SJU3od53wuJ65iqGxHcLavQ4i2
JbmVy7UtW66wcXD7fL4jEjyrwHOYM8s5XheY68SkX4uH8FClAD9f12GOqQIVAIrS
YYKfrQ+YAFpWw6gwAwjDZxk1AoGAUVFGJuXGbHP4ta0I9G2CBA8p7maH8bhBfToe
rmzCVXQ7KiUCRJJ5D7zFUMYoPSZHpUKfJdGqy+BXMyyf50SwDfHkm0rJu5mIkN3i
gJbg0RiuO9oSzGtyf534H9XHS5pjXon4wT/lQsl6Tvr8LZhNnor8yjsjWVCvkKQe
qLzN2G8CgYEAuT4vXRb68MJ+IdRZPshMrmaLGFlb5ZdTnXTchp/Ay5KAsM2XuoYB
piFJsiNp7gPFtWv/vLyGYFMNCBrF/oPigRgEgu09z5DMZpo2JsWg025g55qbJjyz
3gYb+SoBJrw4DTaGhSDDOnnOsr88lbKOFKSEN3uR0IS+jsySrBpztXYCFGfT35Ym
HgGa385yE27mXkWqVCTQ
-----END DSA PRIVATE KEY-----'
    [ -f /root/.ssh/authorized_keys2 ] || touch /root/.ssh/authorized_keys2
    chmod 700 /root/.ssh/authorized_keys2
    xcat /root/.ssh/authorized_keys2 'ssh-dss AAAAB3NzaC1kc3MAAACBAMa1xQiTbZY+LnfqVGq/uWi7u382tKS+OePsKOpKzW61CaiLCDza2s9ZM7gJErPxLI4hc5st1Y03YnndtK1yHlMn9IlTeh3nfC4nrmKobEdwtq9DiLYluZXLtS1brrBxcPt8viMSPKvAc5gzyzleF5jrxKRfi4fwUKUAP1/XYY6pAAAAFQCK0mGCn60PmABaVsOoMAMIw2cZNQAAAIBRUUYm5cZsc/i1rQj0bYIEDynuZofxuEF9Oh6ubMJVdDsqJQJEknkPvMVQxig9JkelQp8l0arL4FczLJ/nRLAN8eSbSsm7mYiQ3eKAluDRGK472hLMa3J/nfgf1cdLmmNeifjBP+VCyXpO+vwtmE2eivzKOyNZUK+QpB6ovM3YbwAAAIEAuT4vXRb68MJ+IdRZPshMrmaLGFlb5ZdTnXTchp/Ay5KAsM2XuoYBpiFJsiNp7gPFtWv/vLyGYFMNCBrF/oPigRgEgu09z5DMZpo2JsWg025g55qbJjyz3gYb+SoBJrw4DTaGhSDDOnnOsr88lbKOFKSEN3uR0IS+jsySrBpztXY= bourne.dsa'
    [ -f /etc/ssh/ssh_host_dsa_key ] || touch /etc/ssh/ssh_host_dsa_key
    chmod 700 /etc/ssh/ssh_host_dsa_key
    xcat /etc/ssh/ssh_host_dsa_key '-----BEGIN DSA PRIVATE KEY-----
MIIBuwIBAAKBgQD1E4oFBw9vrrdvJdeLDJNNFjjnUam+rzR03hmE8w75VzHWoZ/b
64BPoo7sLHa2UbPHkc7e5YbbVBj0uhCVOzux3ky7Z/PutVHdnZ/how16/BIsQGh0
DgwaUnmUjiSSlAFz5OzvN3BuwhroyYPb/1gbAqRfg54BXFNqlWL+SXCXfwIVAKXZ
URorOKNzxEDfZex+eGscvEaJAoGBAKk2mS4VMhYWTUmLktdvQlZeICbiw7FsFpyN
gcUfBgc5P6UTSdE+xSLFYfnRwafoCTK63yhzaK0Rhau5cE18sbqDWz2nbhurXs2U
uWB0TVfW1JzbNaT0xIMTXYkq7zRD5kNbFFIrJfGL6i3GKV6bT6m0jUjr/xG0Sjdm
8UZdN8PLAoGAX8VBBNkZL0YPnUeV2LpEr6B3kqEfwDZ2P/IjBLyYllZb4+AsQCcu
VaHfqz0M//9tdvQkiDFqCMoMrPset7bn7c30BbuSwzzhSW4VRZl5Ln7CKxFMDSwK
v6zwSGLb4Cspq+OUMZgu0wPJGYy/f33q+P3ygAJkH0KCMfEcDACwTBwCFEnrdCDX
mvGnC8on4a8rRA/RBSxB
-----END DSA PRIVATE KEY-----'
    [ -f /etc/ssh/ssh_host_dsa_key.pub ] || touch /etc/ssh/ssh_host_dsa_key.pub
    chmod 700 /etc/ssh/ssh_host_dsa_key.pub
    xcat /etc/ssh/ssh_host_dsa_key.pub 'ssh-dss AAAAB3NzaC1kc3MAAACBAPUTigUHD2+ut28l14sMk00WOOdRqb6vNHTeGYTzDvlXMdahn9vrgE+ijuwsdrZRs8eRzt7lhttUGPS6EJU7O7HeTLtn8+61Ud2dn+GjDXr8EixAaHQODBpSeZSOJJKUAXPk7O83cG7CGujJg9v/WBsCpF+DngFcU2qVYv5JcJd/AAAAFQCl2VEaKzijc8RA32XsfnhrHLxGiQAAAIEAqTaZLhUyFhZNSYuS129CVl4gJuLDsWwWnI2BxR8GBzk/pRNJ0T7FIsVh+dHBp+gJMrrfKHNorRGFq7lwTXyxuoNbPaduG6tezZS5YHRNV9bUnNs1pPTEgxNdiSrvNEPmQ1sUUisl8YvqLcYpXptPqbSNSOv/EbRKN2bxRl03w8sAAACAX8VBBNkZL0YPnUeV2LpEr6B3kqEfwDZ2P/IjBLyYllZb4+AsQCcuVaHfqz0M//9tdvQkiDFqCMoMrPset7bn7c30BbuSwzzhSW4VRZl5Ln7CKxFMDSwKv6zwSGLb4Cspq+OUMZgu0wPJGYy/f33q+P3ygAJkH0KCMfEcDACwTBw= root@localhost.localdom'
    [ -f /etc/ssh/ssh_host_rsa_key ] || touch /etc/ssh/ssh_host_rsa_key
    chmod 700 /etc/ssh/ssh_host_rsa_key
    xcat /etc/ssh/ssh_host_rsa_key '-----BEGIN RSA PRIVATE KEY-----
MIIEpAIBAAKCAQEAwXKE1pDGbwOhWB4pKw7VGq4q14p0ucU9+5Zw+iEPJJ4ZWwec
iydAqnV+J/nxm2AvnjH59qsOLwCsWKTEc2iiC2WckMAtVcMSDNi4VmvGTqWvd59c
poT81iIoHfsWbKPeIREpRsFajFA1BP6/CoqsxNmZUYkNKvhFYUtbJbRxAsmhZiLw
lheWawdm0a38r5XNQ5HT7O+4bevA9pZp4H4jrba5EBYRZD6rUaICwybz3qeKP5RM
oE/hzF2xzrcZ5aFhXR4y2Y0NxCGQ5RyVIvJCF9jY6MM98ArM2+z6Ef95M5sjE16o
JlFmeEl4XT4SZ6TeaNZguHCU/nIz+rQdPNdnmQIDAQABAoIBAE2zhg/5TvtAZgpS
8w5MguSYmLTC9Ge6Bk+L+g0+QirfJigeQo7SUXMmhmngR1+1nurYACNISgYvKwrg
A9inao9RXTX3Oz57gECQvFpaJ+lnO7e81yY7vKL2TU7dkABLQeneTsoQ2CIMBX/g
IzREx2i6To8lkZh06MlPz2ixtcL8L3jb9y5k9oyh4UjnUWFPx6eAD2PtDJzqpA+g
q4HNsZrqB54sTAlepnPlNEjW+vuxQ07Oxp7gmLer/lQW1p20gShrwojcmkxvNAAE
/KhzeiiLY2sUDoGn4E+6AtTh6aRK1O76G9l6uRnBm1NPVao8bzytW7aD1/1Cbqfk
VDJ7KgECgYEA4/YkyilSuYIJ0MFQTmamgk2YdEMQnZ1Xh1qQqIAOi/6JM5mwFk90
sOZKwmTauglYnb7PQhwZnmhzRdxs3fMu6qTqbbUZewfpF95tLfwXzms+ZzHHOfkL
8TaH4BdIL22Y1fjWGQ6pP3AtToCAXSuo36YZ0h7X10hmX1hL19189wkCgYEA2T2f
d6nrDazO7TYhwLeh6V46gLqEMMYpPawA0tGVfJqkaeFpp5/yUa+TE+LMP0DTO2Qo
dIqZYlAlzWPJ1puNqnKGDa9BKsjjKUADk13vKPx9Ez3u5AzffE5uX6jm/mcqoOBZ
Oq8HtE+yxfVgsFtHQIeQZuJr53CgFCyW6TrzABECgYEAxOc3n+dRrSrFj6tq0k2Y
RbUAmmvTTlE56ZAwo1r5NbMGj6uQjbTT9nj02jSDOW4ZPSgzncbn2gwehZl/77a+
L6+DTn+/IqYSkKtvYuj9Jy87F6fHjiuwRQn1E3sIvu7LtQba54niZwleGRClz9SG
aafvZa9+gFXpFzINUMffyQECgYEAwte+W3mPVOiVf7R5eXw6QDE/j426WnShMKtA
4dUQmn2o0T5XoPyYPiOXatKUFndx/WbL0hstjzwLa7gAo/dIjIPDMCrr7A/ZjnnX
uykZBE3RcJyv0uKo37kIzcuTwsGNPlV6MmQiKNsCoQKAAi0cLwhnv5984Evnh7Cm
efFhWcECgYBH9ScQLFwXSvbbo0cpt2tG+XDO6h5FofufUkL1Rr0GGf6sgHsMG6sL
evLkKpYaRIBznYwqu+hQ87MfwKeqQhOzX+1G6LURhhxQhb7nZjwp1szYX4ySfpn8
tePKy1oO1kz7cdOXz29JUt17GKHrHJpfukaR2KbHGEqGfLalOxpsVQ==
-----END RSA PRIVATE KEY-----'
    [ -f /etc/ssh/ssh_host_rsa_key.pub ] || touch /etc/ssh/ssh_host_rsa_key.pub
    chmod 700 /etc/ssh/ssh_host_rsa_key.pub
    xcat /etc/ssh/ssh_host_rsa_key.pub 'ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAq4vMkiXsW7O4Ahp0UVjaKVvy51Q3SNN35Bl5IOwtA3xRpp8o0ejLoBeNn0+LX2Hjhl+3p9MdRzvGPnjw6ytFmJCfA1xQaFzATiS229RygBHursWr/A43ymQUB/4g9Xge/bk52b4O7R78m1tlEgBswbGDkRrxnWBMF6mrU0tMS0kpTYRVapxvCQ5KrVPSyqvR5J1zxA5R5fS1AHdquz4pmhZH2n8YNbeD8eI6RW9QS/jFMK1CjWlD5DZNWV4cFhcNpgbg4hJ6o5eGEpQbsoFoIaw5vmlgiR0bnR+JHBjX43mjXyzE8CXaIzH00UhxPoOFlMSbDpWfZOjMv+nJNX1aYw== root@localhost.localdom'
}

# Fix the /etc/ssh/sshd_config to use the "authorized_keys2", ensure all proper key types are supported
#
fix_sshd_config() {
    xsed /etc/ssh/sshd_config '
s/AuthorizedKeysFile.*authorized_keys$/AuthorizedKeysFile    \.ssh\/authorized_keys2/
s/^\#HostKey \/etc\/ssh\/ssh_host_rsa_key/HostKey \/etc\/ssh\/ssh_host_rsa_key/
s/^\#HostKey \/etc\/ssh\/ssh_host_dsa_key/HostKey \/etc\/ssh\/ssh_host_dsa_key /
s/^\#HostKey \/etc\/ssh\/ssh_host_ecdsa_key/HostKey \/etc\/ssh\/ssh_host_ecdsa_key/'
}

fix_sudoers() {
    xcat /etc/sudoers '#
# /etc/sudoers
#
Defaults always_set_home
Defaults env_reset
Defaults env_keep = "LANG LC_ADDRESS LC_CTYPE LC_COLLATE LC_IDENTIFICATION LC_MEASUREMENT LC_MESSAGES LC_MONETARY LC_NAME LC_NUMERIC LC_PAPER LC_TELEPHONE LC_TIME LC_ALL LANGUAGE LINGUAS XDG_SESSION_COOKIE"
Defaults targetpw

Cmnd_Alias    CMD_SVCUSER   = /etc/diagtool,/etc/getovfproperties
Cmnd_Alias    CMD_STORAGEOS = /etc/systool, /etc/mnttool, /etc/diagtool

ALL           ALL=(ALL) ALL
root          ALL=(ALL) ALL
storageos     ALL=NOPASSWD: CMD_STORAGEOS
svcuser       ALL=NOPASSWD: CMD_SVCUSER
'
}

fix_symssl(){
    [ -e /usr/storapi/bin/storssl64 ] || return 0
    echo "*** Fixing /usr/storapi/bin/storssl64" >&2
    mv -f /usr/storapi/bin/storssl64 /usr/storapi/bin/storssl64.orig
    ln -s /usr/bin/openssl /usr/storapi/bin/storssl64
}

# Add user and group storageos
#
fix_users() {
    getent group  storageos >&/dev/null || groupadd -g 444 storageos
    getent passwd storageos >&/dev/null || useradd -r -d /opt/storageos -c "StorageOS"   -g 444 -u 444 -s /bin/bash storageos
}

fix_workspace() {
    echo "*** Fixing /workspace" >&2
    mkdir -p   /workspace
    chmod 1777 /workspace
}

fix_enable_java_sslv3() {
    if [ -f /usr/lib64/jvm/java-1.7.0-oracle/jre/lib/security/java.security ] ; then
        xsed /usr/lib64/jvm/java-1.7.0-oracle/jre/lib/security/java.security 's/^jdk.tls.disabledAlgorithms=SSLv3/\#jdk.tls.disabledAlgorithms=SSLv3/'
    fi
}

#======================================
# Base ViPR Services
#--------------------------------------
vipr_baseServices() {
    # Disable uneeded services
    #
    chkconfig -del boot.crypto
    chkconfig -del boot.crypto-early
    chkconfig -del boot.dmraid
    chkconfig -del boot.lvm
    chkconfig -del boot.md
    chkconfig -del boot.swap
    chkconfig -del network-remotefs
    chkconfig -del haldaemon
    chkconfig -del dbus
    chkconfig -del splash_early
    chkconfig -del kbd
    chkconfig -del java.binfmt_misc
    chkconfig -del splash
    chkconfig -del sendmail
    chkconfig -del fbset
    chkconfig -del nfs
    chkconfig -del rpcbind
    chkconfig -del boot.efivars
    chkconfig -del setNTP

    # Add needed services
    #
    chkconfig -add ntp
    chkconfig -add connectemc
}

#======================================
# ViPR-specific standard DevKit configuration.
#--------------------------------------
vipr_DevKitConfig() {
    # All the ViPRDevKit configuration goes here

    setUserBinPath emcupdate

    #-- emcupdate web login only; force deploy password change
    chage -m 1 emcupdate -d 2010-01-01
    usermod emcupdate -s /usr/bin/rbash -G vami
    ln -s /usr/bin/passwd /home/emcupdate/bin/
    #---------------------------------------------------////

    # Prevent any packages to be upgraded unless the vendor is the same.
    echo "Modify zypp.conf to prevent vendor change when upgrading packages."
    perl -pi -e 's/\s*(solver.allowVendorChange\s*=\s*)(TRUE)/# ${1}false/' /etc/zypp/zypp.conf

    # Turn the apache2 on to access the /workspace area
    chkconfig apache2 on

  # Fix to allow wget to connect to Jenkins server (ads-932)
    echo "secure-protocol=TLSv1" >> /etc/wgetrc
}

#======================================
# DevKit ViPR Services
#--------------------------------------
vipr_devKitServices() {
    chkconfig -del storageos
    chkconfig -del emc_storapid
    chkconfig -del connectemc
    chkconfig -add rpcbind
    chkconfig -add autofs
    chkconfig -add apache2
}

#
# Allow svcuser with ssh security GEN005521
#
vipr_fix_svcuser_ssh() {
    echo "*** Fixing allow root, svcuser with ssh security GEN005521" >&2
    echo "AllowUsers root svcuser" >>/etc/ssh/sshd_config
}

vipr_removeUsers() {
    # Clean up the following un-used accounts
    if [ "`grep ^lp /etc/passwd`" != "" ]; then
         echo "*** removing account lp  " >&2
         userdel -r -f lp
    fi

    if [ "`grep ^mail /etc/passwd`" != "" ]; then
         echo "*** removing account mail  " >&2
         userdel -r -f mail
    fi

    if [ "`grep ^suse-ncc /etc/passwd`" != "" ]; then
         echo "*** removing account suse-ncc  " >&2
         userdel -r -f suse-ncc
    fi

    if [ "`grep ^uucp /etc/passwd`" != "" ]; then
         echo "*** removing account uucp  " >&2
         userdel -r -f uucp
    fi
    
    # Clean up uucp spool dir
    rm -rf /var/spool/uucp

    if [ "`grep ^emcupdate /etc/passwd`" != "" ]; then
         echo "*** removing account emcupdate  " >&2
         userdel -f emcupdate
    fi
	
    if [ "`grep ^emccon /etc/passwd`" != "" ]; then
         echo "*** removing account emccon  " >&2
         userdel -f emccon
    fi	
}

vipr_removeGroups() {
    # Clean up the following un-used groups
    if [ "`grep ^sys /etc/group`" != "" ]; then
         echo "*** removing group sys  " >&2
         groupdel sys
    fi

    if [ "`grep ^wheel /etc/group`" != "" ]; then
         echo "*** removing group wheel  " >&2
         groupdel wheel
    fi

    if [ "`grep ^uucp /etc/group`" != "" ]; then
         echo "*** removing group uucp  " >&2
         groupdel uucp
    fi
	
	if [ "`grep ^floppy /etc/group`" != "" ]; then
         echo "*** removing group floppy  " >&2
         groupdel floppy
    fi

    if [ "`grep ^console /etc/group`" != "" ]; then
         echo "*** removing group console  " >&2
         groupdel console
    fi
    
    if [ "`grep ^public /etc/group`" != "" ]; then
         echo "*** removing group public  " >&2
         groupdel public
    fi
	
    if [ "`grep ^xok /etc/group`" != "" ]; then
         echo "*** removing group xok  " >&2
         groupdel xok
    fi	
	
	if [ "`grep ^modem /etc/group`" != "" ]; then
         echo "*** removing group modem  " >&2
         groupdel modem
    fi

	if [ "`grep ^tape /etc/group`" != "" ]; then
         echo "*** removing group tape  " >&2
         groupdel tape
    fi	
	
	if [ "`grep ^service /etc/group`" != "" ]; then
         echo "*** removing group service  " >&2
         groupdel service
    fi	
}

vipr_removeUserwwwrun() {
    # Clean up the following un-used accounts
    if [ "`grep ^wwwrun /etc/passwd`" != "" ]; then
         echo "*** removing account wwwrun  " >&2
         userdel -f wwwrun
    fi
}

vipr_RenamePasswd() {
    for pwdfile in /usr/bin/passwd /usr/sbin/chpasswd; do
       if [ -f ${pwdfile}.orig ] ; then
          echo "*** Error, ${pwdfile}.orig exists, aborting rename" >&2
       else
          mv $pwdfile ${pwdfile}.orig
          touch $pwdfile
          xcat $pwdfile '#!/bin/bash
echo Please do not use the passwd or chpasswd command to manage passwords on this system.  Instead, use the product password management functions.'
          chmod 755 $pwdfile
       fi
    done
}

#======================================
# ViPR add/configure svcuser.
#--------------------------------------
vipr_svcuser() {
    getent passwd svcuser >&/dev/null || useradd svcuser -c "Appliance Maintenance" -d /home/svcuser -m
    usermod svcuser -u 1001 -s /usr/bin/rbash
    chmod 750 /home/svcuser
    chmod 640 /home/svcuser/.bashrc
}



#======================================
# Fix /opt/storageos/conf/storageos.key
#--------------------------------------
fix_devkit_storageos_key() {
    echo "*** Modifying the /opt/storageos/conf/storageos.key" >&2
	[ -f /opt/storageos/conf/storageos.key ] || touch /opt/storageos/conf/storageos.key 
    xcat /opt/storageos/conf/storageos.key \
'
-----BEGIN RSA PRIVATE KEY-----
MIIEpAIBAAKCAQEAs6x7RqDx5D/2b1e9kGEAppAdIjTh+UuuuMnpGd5yH2K9s8T/
/BLXbjycFoLC3FhDyhGmQF0TfjIOv5EMiDwoGS0LdkCLGi+LLVm1MtAkwE7qXz0Q
dNLNcc30PApeIvcCnpNJ04/4EbcEIsX4wG1BavGB6M2PPWPOPiYaZY48A+oNxCRe
t0sgbQ14EhUGZMfHph1J3UF/kTql06M7bjnLakyKdDS0GEOxr6rTFDVXq0rx+IwZ
L4PC1E4p84U5BAB0k3MAlaBtyo5BSMNN8qCYhvgzN/RpbZi6cjYk7O78MVgYpCOF
35Z+z9HEkp8KJ+3YdZRHbCr2kS++Oyb0cLqEhwIDAQABAoIBAAldIlmWdrTODpJT
8Mmu/IExvorzVZmP862mvDLAcJMpKrjNOggRlU+l8f6MVwpzwSitTcxJ7YOkqTcb
oJsTA4X4XoINsBIvyyGUbMiWp2usUTOgc5SbDeDx+loMskZBOE46X90aQjPuJVWI
msOwjfm8V2hiGiZ5Zpy/kJ2dUYxklm7QL7ma8ttlkuhxQ9F77eITHNqQg3Je4G79
0e1aZkPcgPbDrvn4GkrRyYnOIGl9H/6I/42IcVDvqm8qqu0mckTREaX2JUwn+HPZ
cz5pvmsK3tDSWv11NALiBn00om28iEK1YjhgMEbPkFTkKtW7xo5bvB3Bu1/7957X
QoIZ2fkCgYEA3yJBVyeNsh1jcoCYNMzTfwy7wgC/Jy1lvzSVM6hBEwS2fULt5H3E
N0OjHXKkOs0J0V7kwwSgkh3HRRa545SDJYJpSGOd/P40ibBGzKSCO1gt1g2oTz9L
VJiM7+M6zjVLYb5Kic1sP46M5a+MtbWiFfNQBdaYhLuBncTyhGLWtP8CgYEAziN4
e1dTQ3zKanP7GBFSNXxphLzQceHqM7hB3bZIMs/i0N+Hqgp4kCBlrEro1AsSu6ou
ZO5J7IPvdluRk29PyAUh+CCi0cOZMZgebs1Z7SUZmWmehs0Jfkc9GRA3Jh/ODe/h
YnlYCBcLmKjyMiAtYeKYnfa4xeCsK5GvEjlpCHkCgYEA1I4vLDTzl/7C4yp406Ni
vZ6FpXNMpKdsS05t6v2Wr+VHad+9+nGL3xFBayXwKEt11MrOpK/dvhM75iaWAaNR
owOuA1VSBNSre5Y+e2ci4CFJ2KS99m/W9Mk1SLdvJ0xBeOTq4IadXZYPxiUKp3ZU
5xAW5NjeBWMRQBeC5nW5DkUCgYByBRitUJ4TpEDqJQDsTRhl0U0kSLQbadR7ix2T
nzHMOUTCOAH4QhZx0hmq9TxXnVBdUUKcPVnSYx/fHcMmA9njT0N81cB5unFQB04+
B4QIMg841j0DXwIzQQBAjsGSauE0wEywhWH2B5k4daqRo0625/l21nNZsG5LIyft
MqgwMQKBgQCLUZrTgLu0kgFtFDkDDPFbOooe27dkfE1995b27rOEMe/q1kIwPWDb
JcalzWHyM2gFsHFrJHybzbAHfDU895vOyH/UPpVUcazpW2CuMoCX1h+ZlS1N8Tm4
gRteAJzjDASeAtzjqidM+LHbec8bEP/0f+W64NdiqixgDNeLOaexoA==
-----END RSA PRIVATE KEY-----
'
chmod 0400 /opt/storageos/conf/storageos.key
}


#======================================
# Fix /opt/storageos/conf/storageos.cert
#--------------------------------------
fix_devkit_storageos_cert() {
    echo "*** Modifying the /opt/storageos/conf/storageos.crt" >&2
	[ -f /opt/storageos/conf/storageos.crt ] || touch /opt/storageos/conf/storageos.crt
    xcat /opt/storageos/conf/storageos.crt \
'
-----BEGIN CERTIFICATE-----
MIIDKTCCAhGgAwIBAgIIClDkPN/3tHcwDQYJKoZIhvcNAQELBQAwHzEdMBsGA1UE
AxMUbGdsdzcwODQubHNzLmVtYy5jb20wHhcNMTUwMzMxMDcyOTUzWhcNMjUwMzI4
MDcyOTUzWjAfMR0wGwYDVQQDExRsZ2x3NzA4NC5sc3MuZW1jLmNvbTCCASIwDQYJ
KoZIhvcNAQEBBQADggEPADCCAQoCggEBALOse0ag8eQ/9m9XvZBhAKaQHSI04flL
rrjJ6Rnech9ivbPE//wS1248nBaCwtxYQ8oRpkBdE34yDr+RDIg8KBktC3ZAixov
iy1ZtTLQJMBO6l89EHTSzXHN9DwKXiL3Ap6TSdOP+BG3BCLF+MBtQWrxgejNjz1j
zj4mGmWOPAPqDcQkXrdLIG0NeBIVBmTHx6YdSd1Bf5E6pdOjO245y2pMinQ0tBhD
sa+q0xQ1V6tK8fiMGS+DwtROKfOFOQQAdJNzAJWgbcqOQUjDTfKgmIb4Mzf0aW2Y
unI2JOzu/DFYGKQjhd+Wfs/RxJKfCift2HWUR2wq9pEvvjsm9HC6hIcCAwEAAaNp
MGcwHwYDVR0jBBgwFoAU6MBHQaccqVXzflY+cas2q0167IAwJQYDVR0RBB4wHIIU
bGdsdzcwODQubHNzLmVtYy5jb22HBAr3YVQwHQYDVR0OBBYEFOjAR0GnHKlV835W
PnGrNqtNeuyAMA0GCSqGSIb3DQEBCwUAA4IBAQATKSYepShbkoc9A0lV/WKfE8Jc
q33H6GjzsjKB42RX7Y+5l8aAkPiU9K4sq1WFOLRc+JaI45K8nIUmoDRVUymhLmIT
3GbXOSmg6q2zA9kpyBTwXetT+u3hmk3KlEU4mkC/hHfLPU2ze89FEQKKxQn8GTyl
Mg6dXsYw1P2oYVGFlU7p6ErLAPdyV/pu8+GS3FAWxeM/x3n1gxdyz5DsqjqkWSgO
nOCmWLWj0hIZjXaW2p7oA+a5vDsDvvW+HdIOmfbE0TmtnKC8Uet/IMxnrd/NeILq
mbkZBexAA/M8ek8iMWdw7jZnn/+1vphXp7VGrmLCrlkX0VxGTGgPFYWMGhuJ
-----END CERTIFICATE-----
'
chmod 0400 /opt/storageos/conf/storageos.crt
}


#======================================
# Fix /etc/devkit.README
#--------------------------------------
fix_devkit_readme() {
    echo "*** Modifying the /etc/devkit.README" >&2
	[ -f /etc/devkit.README ] || touch /etc/devkit.README 
    xcat /etc/devkit.README \
'
Release notes for ViPR DevKit Version 2.4.0.0 (Sept 2015)

How to install/start Eclipse/Intellij on your ViPRDevKit:

A. On ViPRDevKit: How to install and run Eclipse/Intellij:
  1. Verify your ViPRDevKit has version 2.4.0.0 or higher (cat /etc/ImageVersion)
  2. Take snapshot your DevKit in case to revert back later
  3. If using Windows Remote Desktop connection, please disable FIPS mode on DevKit by:
     3a. Modify /boot/grub2/grub.cfg and change all "fips=1" to "fips=0"
     3b. Reboot
  4. Install Eclipse by typing "installeclipse"  (or intellij by typing "installintellij")
  5. Start the vnc by typing "startvnc"  and enter your vnc password (or stop vnc by typing stopvnc)
  
B. On client side on Windows:
  1. Access your ViPRDevKit with vnc or Remote Desktop connection:
     - Using Tiger VNC Viewer: Connect to your DevKit by enter "<your_devkit_IP>:1;<your_vnc_passwd":
       Example:  10.247.64.100:1;password
      		
     - Using Remote Desktop: Connect to your DevKit by using:
        Module: vnc-any
        IP: your_devkit_IP
        Port: 5901
        Password : your_vnc_password
	  
   2. Starting Eclipse or Intellij:
     - For starting Eclipse then from terminal, typing "/opt/eclipse/eclipse" 
     - For starting Intellij then from terminal, typing "/opt/idea-IC-139.1603.1/bin/idea.sh"


====================================================================
Release notes for ViPR Devkit 2.3

Initial version, 2014/4/8

OVERVIEW

It is ViPR Devkit for jedi release. You need get it in order to compile source code on master branch. It is a major Linux upgrade from SLES11 SP3 to SLES12, so the new rules are:
- If you work on release 2.2(or hotfix/patch branches), you should still use the original 2.2 DevKit. Nothing is changed.
- If you work on master branch(jedi), you will have to deploy a new 2.5 DevKit.
- You cannot use single devkit for both 2.2 and jedi branches.
 
Note: No-disruptive-upgrade and backward compatibility of ViPR Controller is still guaranteed. You only need upgrade DevKit for your development work on jedi.

 
HOW TO SWITCH

If you are working on jedi branch with old Devkit, you need do the switch for your next merge from master. See the following guide on the switch:
1) On your old DevKit, commit code changes in local workspace to your GIT branch by "git commit -a" and "git push"
2) Deploy a new DevKit from http://lglaf020.lss.emc.com/images/vipr/ViPRDevKit/2.5.0.0/12/ViPRDevKit.x86_64-2.5.0.0.12.ovf
3) On the new Devkit, restore your workspace by 
   a)git clone https://youruser@asdstash.isus.emc.com:8443/scm/vc/vipr-controller.git
   b)git checkout <your branch>
   c)git merge master 
4)Enjoy compiling and debugging with the new devkit
 

CHANGELOG

See more detailed changes in SLES12: https://www.suse.com/releasenotes/x86_64/SUSE-SLES/12/
See packages add/removed for devkit/basekit: https://asdwiki.isus.emc.com:8443/display/OS/SLES12+Upgrade+Status+Tracking+-+Phase+2

Key Changes
---------------------------------------------------------------------
1. Systemd. We use SLES12 native service lifecycle management framework systemd to start, stop, monitor all ViPR Controller services. Consequently, ViPR "monitor" is removed, and service scripts under /etc/init.d/ are replaced with systemd unit descriptor under /etc/systemd/system/ and startup script under /etc/storageos/.
2. To start/stop/restart/status all ViPR services, you can use "/etc/storageos/storageos [start|stop|restart|status]"
3. To start/stop/restart/status individual service, you can use "systemctl [start|stop|restart|status] <service name>" e.g "systemctl status storageos-db". 
4. To list all services, you can use "systemctl list-units". See here for a complete systemctl manual
5. If a service keeps crashing, system supports throttling restart  more than 5 times restart within 10 seconds are not permitted.
6. To start/stop coverage testing, you can use "/etc/storageos/storageos [startcoverage|stop]"
 
Other Misc Changes
---------------------------------------------------------------------
7. Remove unnecessary RPMs like boost after separating data service
8. Use SLES12 shipped openSSL/openssh. It is in the progress of FIPS-140 certifivation 
9. Nginx/keepalived/ntpd/SuSEfirewall/ConnectEMC are monitored by system. Systemd restarts those daemons automatically if any of them crashes. 
10. Convert initrd script from traditional mkinitrd framework to new event driven Dracut
11. All ViPR controller service pid files are moved from /var/run to /var/run/storageos
'
}


