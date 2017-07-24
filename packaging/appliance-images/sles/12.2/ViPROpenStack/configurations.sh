
#!/bin/bash
#
# Copyright (c) 2015 - 2017, EMC Corporation. All Rights Reserved.
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

adg_kernelMessage() {
    if [ -f /boot/do_purge_kernels ] ; then
       echo "*** Fixing Purge Kernels console screen message" >&2
       xsed /etc/zypp/zypp.conf '
       s/^\# multiversion = provides:multiversion/multiversion = provides:multiversion/
       s/^\# multiversion.kernels = latest,running/multiversion.kernels = latest,running/'
    fi
}

#======================================
# ADG set motd.
#--------------------------------------
adg_motd() {
    echo "Executing adg_motd"
    echo "Welcome to" $(cat /etc/ImageVersion) > /etc/motd
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

fix_rootdir() {
    echo "*** Fixing /" >&2
    chmod 1777 /
}

fix_enable_java_sslv3() {
    if [ -f /usr/lib64/jvm/java-1.7.0-oracle/jre/lib/security/java.security ] ; then
        cp -p /usr/lib64/jvm/java-1.7.0-oracle/jre/lib/security/java.security /usr/lib64/jvm/java-1.7.0-oracle/jre/lib/security/java.security.orig
        xsed /usr/lib64/jvm/java-1.7.0-oracle/jre/lib/security/java.security 's/^jdk.tls.disabledAlgorithms=SSLv3/\#jdk.tls.disabledAlgorithms=SSLv3/'
        xsed /usr/lib64/jvm/java-1.7.0-oracle/jre/lib/security/java.security 's/^jdk.certpath.disabledAlgorithms=MD2, RSA keySize < 1024/jdk.certpath.disabledAlgorithms=MD2/'
    fi
}

#--------------------------------------
vipr_fix_add_strongswan() {
    echo "*** Fixing Adding strongswan parameter" >&2
    echo "net.ipv4.xfrm4_gc_thresh=32768" >> /etc/sysctl.conf
    echo "net.ipv6.xfrm6_gc_thresh=32768" >> /etc/sysctl.conf
}

