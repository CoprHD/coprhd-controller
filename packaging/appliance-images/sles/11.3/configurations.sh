#!/bin/bash

#======================================
# Load KIWI functions.
#--------------------------------------
test -f /.kconfig && . /.kconfig
test -f /.profile && . /.profile

#======================================
# Greeting.
#--------------------------------------
echo "Configure image: [$kiwi_iname]..."

#======================================
# The type of configuration, understood by KIWI.
# This value is written by the Makefile when this
# file becomes either config.sh or images.sh
#--------------------------------------
TYPE=""

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
# "fix" functions
# NOTE:
# All "fix" functions below are expected to have idempotent behavior
# so that the script can be executed multiple times safely.
#--------------------------------------

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

# Fix the /etc/ssh/sshd_config to use the "authorized_keys2"
#
fix_sshd_config() {
    xsed /etc/ssh/sshd_config '
s/AuthorizedKeysFile.*authorized_keys$/AuthorizedKeysFile    \.ssh\/authorized_keys2/'
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

# Fix /etc/sysconfig/network/config
#
fix_network_config() {
    rm -f /etc/resolv.conf /etc/resolv.conf.netconfig
    xsed /etc/sysconfig/network/config '
s/^NETCONFIG_MODULES_ORDER=.*/NETCONFIG_MODULES_ORDER="dns-resolver dns-bind ntp-runtime"/
s/^NETCONFIG_DNS_STATIC_SEARCHLIST=.*/NETCONFIG_DNS_STATIC_SEARCHLIST=""/
s/^NETCONFIG_DNS_STATIC_SERVERS=.*/NETCONFIG_DNS_STATIC_SERVERS=""/'
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

# Fix /etc/init.d/network
#
fix_initd_network() {
    xsed /etc/init.d/network '
/Required-Start:\|Required-Stop:/ s/\sdbus\(\s\|$\)//'
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

# Fix broken /etc/init.d/emc_storapid startup script
#
fix_initd_storapid() {
    [ -f /etc/init.d/emc_storapid ] || return 0
    xsed /etc/init.d/emc_storapid '
    /^#\s*Provides:/ {
      n
      s/#\s*Default-Start:.*/# Required-Start: $network $local_fs\n# Required-Stop: $network $local_fs\n\0/
    }
    s/#\s*Default-Start:.*/# Default-Start: 3 5/
    s/#\s*Default-Stop:.*/# Default-Stop: 0 1 2 6/'
}

# Add user and group storageos
#
fix_users() {
    getent group  storageos >&/dev/null || groupadd -g 444 storageos
    getent passwd storageos >&/dev/null || useradd -r -d /opt/storageos -c "StorageOS"   -g 444 -u 444 -s /bin/bash storageos
}

# Fix Grub's menu.lst
#
fix_grub() {
     xsed /boot/grub/menu.lst '
s/^timeout\s.*/timeout 3/
s/\sdns=[0-9\.][0-9.]*//
s/\squiet$//
s/\(kernel\s.*\)/\1 quiet/'
}

fix_nfs() {
    xsed /etc/sysconfig/nfs '
s/^NFS4_SUPPORT=.*/NFS4_SUPPORT="no"/
'
}

fix_autofs() {
    xsed /etc/auto.master '
s/+auto.master/\/net	-hosts/'
}


# Parse the comman line args
#
usage() {
    echo "Usage: $0 [-devkit]" >&2
    exit 2
}

fix_workspace() {
    echo "*** Fixing /workspace" >&2
    mkdir -p   /workspace
    chmod 1777 /workspace
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

fix_symssl(){
    [ -e /usr/storapi/bin/storssl64 ] || return 0
    echo "*** Fixing /usr/storapi/bin/storssl64" >&2
    mv -f /usr/storapi/bin/storssl64 /usr/storapi/bin/storssl64.orig
    ln -s /usr/bin/openssl /usr/storapi/bin/storssl64
}

fix_rsyslog() {
# Removed as this is being overwritten by storageos.rpm
#    echo "# rsyslog parameter used by syslog startup" >> /etc/sysconfig/syslog
#    echo "# added by ADG to enable rsyslog to start in correct mode" >> /etc/sysconfig/syslog
#    echo "RSYSLOGD_PARAMS=\"-c5\"" >> /etc/sysconfig/syslog

# Insert the vipr_log_format template into the file
# '$template vipr_log_format,"%TIMESTAMP:1:4:date-mysql%-%TIMESTAMP:5:6:date-mysql%-%TIMESTAMP:7:8:date-mysql% %TIMESTAMP:8:15:% [%syslogfacility-text%] %syslogseverity-text% %syslogtag%%msg:::sp-if-no-1st-sp%%msg:::drop-last-lf%\n"'
  xsed /etc/rsyslog.early.conf 's@\(# the rest in one file\)@\1\n$template vipr_log_format,"%TIMESTAMP:1:4:date-mysql%-%TIMESTAMP:5:6:date-mysql%-%TIMESTAMP:7:8:date-mysql% %TIMESTAMP:8:15:% [%syslogfacility-text%] %syslogseverity-text% %syslogtag%%msg:::sp-if-no-1st-sp%%msg:::drop-last-lf%\\n"@g'
# Modify the initial boot rsyslog to use the Vipr Format:
  xsed /etc/rsyslog.early.conf 's@\(/var/log/messages;RSYSLOG_TraditionalFileFormat\)@/var/log/messages;vipr_log_format@g'
  
  

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

fix_rootdir() {
    echo "*** Fixing /" >&2
    chmod 1777 /
}

fix_bootfs() {
    echo "*** Fixing /.volumes/bootfs/etc" >&2
    mkdir -p  /.volumes/bootfs/etc
    chmod 755 /.volumes
    chmod 755 /.volumes/bootfs
    chmod 755 /.volumes/bootfs/etc
}

fix_data() {
    echo "*** Fixing /data" >&2
    mkdir -p                  /data
    chown storageos:storageos /data
    chmod 755                 /data
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

#
# Allow svcuser with ssh security GEN005521
#
vipr_fix_svcuser_ssh() {
    echo "*** Fixing allow root, svcuser with ssh security GEN005521" >&2
    echo "AllowUsers root svcuser" >>/etc/ssh/sshd_config
}

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

vipr_removeUserwwwrun() {
    # Clean up the following un-used accounts
    if [ "`grep ^wwwrun /etc/passwd`" != "" ]; then
         echo "*** removing account wwwrun  " >&2
         userdel -f wwwrun
    fi
}

fix_patches() {
    echo "*** Applying the Python patch 5111" >&2
	touch /tmp/python_patch5111
	xcat /tmp/python_patch5111 '
Index: /usr/lib64/python2.6/httplib.py
===================================================================
--- /usr/lib64/python2.6/httplib.py
+++ /usr/lib64/python2.6/httplib.py
@@ -865,6 +865,10 @@
                         host_enc = self.host.encode("ascii")
                     except UnicodeEncodeError:
                         host_enc = self.host.encode("idna")
+                    # As per RFC 2732, IPv6 address should be wrapped
+                    # with [] to use as Host header
+                    if host_enc.find('"'"':'"'"') >= 0:
+                        host_enc = "[" + host_enc + "]"
                     if self.port == self.default_port:
                         self.putheader('"'"'Host'"'"', host_enc)
                     else:

	'

    chmod 755 /tmp/python_patch5111
    patch -p0 < /tmp/python_patch5111
	rm -rf /tmp/python_patch5111

	echo "*** Applying the Patch for python-requests-2.3.0" >&2
	touch /tmp/python-requests-patch
	xcat /tmp/python-requests-patch '
--- /usr/lib64/python2.6/site-packages/requests/packages/urllib3/util/ssl_.py
+++ /usr/lib64/python2.6/site-packages/requests/packages/urllib3/util/ssl_.py
***************
*** 1,5 ****
  from binascii import hexlify, unhexlify
- from hashlib import md5, sha1

  from ..exceptions import SSLError

--- 1,4 ----
***************
*** 28,33 ****
--- 27,35 ----

      # Maps the length of a digest to a possible hash function producing
      # this digest.
+
+     from hashlib import md5, sha1
+
      hashfunc_map = {
          16: md5,
          20: sha1
	'

    chmod 755 /tmp/python-requests-patch
    patch -p0 < /tmp/python-requests-patch
	rm -rf /tmp/python-requests-patch
}

fix_atop() {
       xsed /etc/atop/atop.daily '
    /^INTERVAL=600/aif [ ! -d $LOGPATH ]; then\n   mkdir -p $LOGPATH\n   chmod 755 $LOGPATH \nfi'
}

fix_devkit_firewall() {
    if [ -f /etc/init.d/SuSEfirewall2_setup ] ; then
        xsed /etc/init.d/SuSEfirewall2_setup 's/\# Should-Start:   $ALL/\# Should-Start:   $null/'
    fi
}

# This function creates links to the current location of the libcrypto and libssl
# required after updating to libopenssl1-devel-1.0.1g-0.16.1 on devkit
fix_openssl_links() {
    ln -s /usr/lib64/libssl.so.1.0.0 /lib64/libssl.so.1.0.0
    ln -s /usr/lib64/libcrypto.so.1.0.0 /lib64/libcrypto.so.1.0.0
}

fix_kiwi_storage_network() {
    echo "*** Fixing kiwi_storage_network"
    if [ -f /.volumes/bootfs/etc/ovf-env.properties ] ; then
         echo "*** Removing /.volumes/bootfs/etc/ovf-env.propertes" >&2
         rm -rf /.volumes/bootfs/etc/ovf-env.properties
    fi
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

fix_permission() {
    if [ -d /var/run/PolicyKit ] ; then
         echo "*** Setting /var/run/PolicyKit to 0750" >&2
         chmod 0750 /var/run/PolicyKit
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

#======================================
# Nile MKNOD.
#--------------------------------------
nile_mknod() {
    echo "Executing nile_mknod"
    major=8
    minor=0
    for dev in a b c d e f g h i j k l m n o p
    do
        mknod /dev/sd$dev b $major $minor
        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=65
    for dev in q r s t u v w x y z aa ab ac ad ae af
    do
        mknod /dev/sd$dev b $major $minor
        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=66
    for dev in ag ah ai aj ak al am an ao ap aq ar as at au av
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=67
    for dev in aw ax ay az ba bb bc bd be bf bg bh bi bj bk bl
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=68
    for dev in bm bn bo bp bq br bs bt bu bv bw bx by bz ca cb
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=69
    for dev in cc cd ce cf cg ch ci cj ck cl cm cn co cp cq cr
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=70
    for dev in cs ct cu cv cw cx cy cz da db dc dd de df dg dh
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=71
    for dev in di dj dk dl dm dn do dp dq dr ds dt du dv dw dx
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=128
    for dev in dy dz ea eb ec ed ee ef eg eh ei ej ek el em en
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=129
    for dev in eo ep eq er es et eu ev ew ex ey ez fa fb fc fd
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=130
    for dev in fe ff fg fh fi fj fk fl fm fn fo fp fq fr fs ft
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=131
    for dev in fu fv fw fx fy fz ga gb gc gd ge gf gg gh gi gj
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=132
    for dev in gk gl gm gn go gp gq gr gs gt gu gv gw gx gy gz
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=133
    for dev in ha hb hc hd he hf hg hh hi hj hk hl hm hn ho hp
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=134
    for dev in hq hr hs ht hu hv hw hx hy hz ia ib ic id ie if
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done

    minor=0
    major=135
    for dev in ig ih ii ij ik il im in io ip iq ir is it iu iv
    do
        mknod /dev/sd$dev b $major $minor

        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15
        do
            minor=$(expr $minor + 1)
            mknod /dev/sd"$dev"$i b $major $minor
        done

        minor=$(expr $minor + 1)
    done
}


#======================================
# Nile MKSGNOD.
#--------------------------------------
nile_mksgnod() {
    echo "Executing nile_mksgnod"
    i=0
    while [ $i -lt 256 ]
    do
        mknod /dev/sg$i c 21 $i
        i=$(expr $i + 1)
    done

    i=0
    for dev in a b c d e f g h i j k l m n o p q r s t u v w x y z
    do
        ln -s /dev/sg$i /dev/sg$dev
        i=$(expr $i + 1)
    done

    for prefix in a b c d e f g h
    do
        for dev in a b c d e f g h i j k l m n o p q r s t u v w x y z
        do
        ln -s /dev/sg$i /dev/sg"$prefix"$dev
        i=$(expr $i + 1)
        done
    done

    prefix=i
    for dev in a b c d e f g h i j k l m n o p q r s t u v
    do
        ln -s /dev/sg$i /dev/sg"$prefix"$dev
        i=$(expr $i + 1)
    done
}


#======================================
# Nile add/configure svcuser.
#--------------------------------------
nile_svcuser() {
    echo "Executing nile_svcuser"
    useradd -md /home/svcuser -c "Appliance Maintenance" -s /bin/bash svcuser
}


#======================================
# ViPR add/configure svcuser.
#--------------------------------------
vipr_svcuser() {
    getent passwd svcuser >&/dev/null || useradd svcuser -c "Appliance Maintenance" -d /home/svcuser -m
    usermod svcuser -u 1001 -s /usr/bin/rbash
    chmod 750 /home/svcuser
}

#======================================
# Nile modifications/additions to KIWI default nodes.
# This is just to modify the defaults of KIWI to match
# the defaults desired by Nile.  The defaults desired
# by Nile are:
#       mkdir -p /dev
#       mkdir -m 755 -p /dev/pts
#       mknod -m 666 /dev/null c 1 3
#       mknod -m 666 /dev/zero c 1 5
#       mknod -m 622 /dev/full c 1 7
#       mknod -m 666 /dev/random c 1 8
#       mknod -m 666 /dev/urandom c 1 9
#       mknod -m 666 /dev/tty c 5 0
#       mknod -m 666 /dev/ptmx c 5 2
#       ln -s /proc/self/fd /dev/fd
#       ln -s fd/2 /dev/stderr
#       ln -s fd/0 /dev/stdin
#       ln -s fd/1 /dev/stdout
#       mknod -m 660 /dev/loop0 b 7 0
#       mknod -m 660 /dev/loop1 b 7 1
#       mknod -m 660 /dev/loop2 b 7 2
#       mknod -m 660 /dev/loop3 b 7 3
#       mknod -m 660 dev/loop4 b 7 4
#       mknod -m 660 dev/loop5 b 7 5
#       mknod -m 660 dev/loop6 b 7 6
#       mknod -m 660 dev/loop7 b 7 7
#       mknod -m 660 dev/loop8 b 7 8
#       mknod -m 660 dev/loop9 b 7 9
#       mknod -m 640 dev/mem c 1 1
#--------------------------------------
nile_modDefaultNode() {
    echo "Executing nile_modDefaultNode"
    pushd /
    chmod 666 dev/urandom
    chmod 660 dev/loop0
    chmod 660 dev/loop1
    chmod 660 dev/loop2
    chmod 660 dev/loop3
    mknod -m 660 dev/loop4 b 7 4
    mknod -m 660 dev/loop5 b 7 5
    mknod -m 660 dev/loop6 b 7 6
    mknod -m 660 dev/loop7 b 7 7
    mknod -m 660 dev/loop8 b 7 8
    mknod -m 660 dev/loop9 b 7 9
    mknod -m 640 dev/mem c 1 1
    popd
}


#======================================
# ADG container-specific configurations.
#--------------------------------------
adg_modContainer() {
    echo "Executing adg_modContainer"
    if [ "$kiwi_type" == "tbz" ]; then
        echo "Disabling root fsck ..."
        cat <<EOF >> /etc/sysconfig/boot
ROOTFS_FSCK="0"
ROOTFS_BLKDEV="/dev/null"
EOF

        echo "Removing pointless services ..."
        /sbin/insserv -r -f -p /etc/init.d boot.udev boot.udev_retry boot.md boot.lvm boot.loadmodules boot.device-mapper boot.clock boot.swap haldaemon boot.klog
        if [ $? -ne 0 ]; then
            echo "ERROR: insserv error removing pointless services during container configuration"
        fi
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
# ADG standard DevKit configuration.
#--------------------------------------
adg_DevKitConfig() {
    echo "Executing adg_DevKitConfig"
    #######################################################
    #            Initialize service states                #
    #######################################################
    chkconfig tomcat6 off
    chkconfig apache2 off

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

#======================================
# ADG standard zypper repos (currently SLES 11.3 plus some common ones)
#--------------------------------------
adg_zypperRepos() {
    #######################################################
    #            Configure Zypper Repos                   #
    #######################################################
    zypper addrepo http://lglob090.lss.emc.com/SLES11SP3/SDK/ sles11.3_sdk
    zypper addrepo http://lglob090.lss.emc.com/SLES11SP3/SLE/ sles11.3
    zypper addrepo http://pld-imgapprd01.isus.emc.com:8081/artifactory/adg-icc icc
    zypper ar http://lglob090.lss.emc.com/DevKit_repo DevKit_repo
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
# HACK - ovftool install
#--------------------------------------
adg_ovftoolInstall() {
    rpm -Uvh http://pld-imgapprd01.isus.emc.com:8081/artifactory/adg-icc/x86_64/vmware-ovftool/3.5.0-1274719/VMware-ovftool-3.5.0-1274719.x86_64.rpm
}

#======================================
# Fix the boot.clock start up
#--------------------------------------
adg_bootClock() {
    echo "*** Modifying /etc/sysconfig/clock" >&2
    xsed /etc/sysconfig/clock 's/^HWCLOCK=.*/HWCLOCK="-u"/'
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
	
	########################################################
	# Grub2 modification 
    cp -R /usr/share/kiwi/image/vmxboot/suse-SLES11/ /usr/share/kiwi/image/vmxboot/suse-SLES11-grub2
    cp -R /usr/share/kiwi/image/oemboot/suse-SLES11/ /usr/share/kiwi/image/oemboot/suse-SLES11-grub2
    cp /usr/share/kiwi/image/vmxboot/suse-linuxrc /usr/share/kiwi/image/vmxboot/suse-SLES11-grub2/root/linuxrc
    sed -i /if\ searchVolumeGroup\;\ then/a"\ kiwi_lvmgroup=\"\${kiwi_lvmgroup}N\"" /usr/share/kiwi/image/vmxboot/suse-SLES11-grub2/root/linuxrc
    sed -i /if\ searchVolumeGroup\;\ then/a"\ vgrename \$kiwi_lvmgroup \"\${kiwi_lvmgroup}N\"" /usr/share/kiwi/image/vmxboot/suse-SLES11-grub2/root/linuxrc
    cp /usr/share/kiwi/image/oemboot/suse-linuxrc /usr/share/kiwi/image/oemboot/suse-SLES11-grub2/root/linuxrc
    sed -i /if\ searchVolumeGroup\;\ then/a"\ kiwi_lvmgroup=\"\${kiwi_lvmgroup}N\"" /usr/share/kiwi/image/oemboot/suse-SLES11-grub2/root/linuxrc
    sed -i /if\ searchVolumeGroup\;\ then/a"\ vgrename \$kiwi_lvmgroup \"\${kiwi_lvmgroup}N\"" /usr/share/kiwi/image/oemboot/suse-SLES11-grub2/root/linuxrc
    sed -i /package\ name=\"grub\"/a"\ <package name=\"grub2-i386-pc\"/>" /usr/share/kiwi/image/vmxboot/suse-SLES11-grub2/config.xml
    sed -i /package\ name=\"grub\"/a"\ <package name=\"grub2-x86_64-efi\" arch=\"x86_64\"/>" /usr/share/kiwi/image/vmxboot/suse-SLES11-grub2/config.xml
    sed -i /package\ name=\"grub\"/a"\ <package name=\"grub2\"/>" /usr/share/kiwi/image/vmxboot/suse-SLES11-grub2/config.xml
    sed -i /package\ name=\"grub\"/a"\ <package name=\"grub2-i386-pc\"/>" /usr/share/kiwi/image/oemboot/suse-SLES11-grub2/config.xml
    sed -i /package\ name=\"grub\"/a"\ <package name=\"grub2-x86_64-efi\" arch=\"x86_64\"/>" /usr/share/kiwi/image/oemboot/suse-SLES11-grub2/config.xml
    sed -i /package\ name=\"grub\"/a"\ <package name=\"grub2\"/>" /usr/share/kiwi/image/oemboot/suse-SLES11-grub2/config.xml
	
	
    ########################################################
    # The following patch is needed in order to complete a build when running a container
    module="/usr/share/kiwi/modules/KIWIBoot.pm"
    cp ${module} ${module}.bak
    grep -q "#GRUB2 INSTALLATION ADG PATCH" $module
    if [ $? -ne 0 ]; then
       sed -i '5605d;' $module
       line=$( grep -nr "Installing grub2:" $module | cut -d: -f1 )
       sed -i "$((${line}+2))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \#GRUB2 INSTALLATION ADG PATCH" $module
       sed -i "$((${line}+3))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \$status = KIWIQX::qxx (\"pgrep init 2>&1\");" $module
       sed -i "$((${line}+4))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \$result = \$? >> 8;" $module
       sed -i "$((${line}+5))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ if (\$result != 0) {" $module
       sed -i "$((${line}+6))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \$loaderTarget = \$this->{loop};" $module
       sed -i "$((${line}+7))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ my \$grubtool = \$locator -> getExecPath ('grub2-install');" $module
       sed -i "$((${line}+8))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ my \$grubtoolopts = \"--grub-mkdevicemap=\$dmfile \";" $module
       sed -i "$((${line}+9))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \$grubtoolopts.= \"-d \$stages \";" $module
       sed -i "$((${line}+10))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \$grubtoolopts.= \"--root-directory=/mnt --force --no-nvram \";" $module
       sed -i "$((${line}+11))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \$status = KIWIQX::qxx (" $module
       sed -i "$((${line}+12))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \"\$grubtool \$grubtoolopts \$loaderTarget 2>&1\"" $module
       sed -i "$((${line}+13))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ );" $module
       sed -i "$((${line}+14))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \$result = \$? >> 8;" $module
       sed -i "$((${line}+15))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ }" $module
       sed -i "$((${line}+16))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ else {" $module
       sed -i "$((${line}+28))i \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ \ }" $module
    fi
}

adg_defaultLoopDevices() {
    ########################################################
    #   Create default loop devices for containers         #
    ########################################################
    [ -e /dev/loop4 ] || mknod -m640 /dev/loop4 b 7 4
    [ -e /dev/loop5 ] || mknod -m640 /dev/loop5 b 7 5
    [ -e /dev/loop6 ] || mknod -m640 /dev/loop6 b 7 6
    [ -e /dev/loop7 ] || mknod -m640 /dev/loop7 b 7 7
    [ -e /dev/loop8 ] || mknod -m640 /dev/loop8 b 7 8
}

adg_kernelMessage() {
    if [ -f /boot/do_purge_kernels ] ; then
       echo "*** Fixing Purge Kernels console screen message" >&2
       xsed /etc/zypp/zypp.conf '
       s/^\# multiversion = provides:multiversion/multiversion = provides:multiversion/
       s/^\# multiversion.kernels = latest,running/multiversion.kernels = latest,running/'
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
# Main.
#
# This covers EIGHT scenarios:
# 1. config.sh for ViPRBase Container
# 2. config.sh for ViPRBase OVF
# 3. config.sh for ViPRDevKit Container
# 4. config.sh for ViPRDevKit OVF
# 5. images.sh for ViPRBase Container
# 6. images.sh for ViPRBase OVF
# 7. images.sh for ViPRDevKit Container
# 8. images.sh for ViPRDevKit OVF
#--------------------------------------

echo "Configuration is for $TYPE"
# config.sh
if [ "$TYPE" = "config.sh" ]; then
    echo "Running common configurations"
    adg_motd
    fix_initd_network
    fix_initd_connectemc
    fix_users
    fix_symssl
    fix_rsyslog
    fix_connectemc
    fix_sudoers
    fix_bootfs
    fix_rootdir
    fix_cacerts
    vipr_fix_svcuser_ssh
    fix_adg_security
    fix_sshd_config
    fix_patches
    fix_atop
    vipr_baseServices
    vipr_removeUsers
    fix_audit
    fix_permission
    adg_bootClock
    adg_kernelMessage

    # Do specific things based on product being built
    if [ "$kiwi_iname" = "ViPRBase" ]; then
        vipr_removeUserwwwrun
    elif [ "$kiwi_iname" = "ViPRDevKit" ]; then
        echo "Running additional common configurations for ViPRDevKit"
        fix_ssh
        fix_workspace
        fix_apache
        fix_autofs
        fix_initd_storapid
        fix_connectemc_data
        fix_data
        fix_devkit_firewall
        fix_openssl_links
        vipr_devKitServices
        adg_ovftoolInstall
        adg_KIWIMods
        adg_zypperRepos
        adg_DevKitHomeEndKeys
        adg_DevKitConfig
    fi

    # Do specific things based on image type (vmx = appliance; tbz = container)
    if [ "$kiwi_type" = "vmx" ]; then
        echo "Running configurations for OVF image"
        fix_kernel_modules
        fix_network_config
        fix_ifcfg
        fix_kiwi_storage_network
        vipr_svcuser
        if [ "$kiwi_iname" = "ViPRDevKit" ]; then
            echo "Running additional OVF configurations for ViPRDevKit"
            vipr_DevKitConfig
        fi
        if [ "$kiwi_iname" = "ViPRBase" ]; then
            echo "Running additional OVF configurations for ViPRBase"
            vipr_RenamePasswd
        fi
    elif [ "$kiwi_type" = "tbz" ]; then
        echo "Running configurations for container image"
        adg_modContainer
        nile_svcuser
    fi
fi

# images.sh
if [ "$TYPE" = "images.sh" ]; then
    if [ "$kiwi_type" = "vmx" ]; then
        echo "No configurations for OVF image"
    elif [ "$kiwi_type" = "tbz" ]; then
        if [ "$kiwi_iname" = "ViPRBase" ]; then
            echo "Running configurations for ViPRBase container image"
            nile_modDefaultNode
            nile_mknod
            nile_mksgnod
        fi
        if [ "$kiwi_iname" = "ViPRDevKit" ]; then
            echo "Running configurations for ViPRDevKit container image"
            adg_defaultLoopDevices
        fi
    fi
fi


