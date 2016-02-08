Name: %{product_name}
Version: %{product_version}
Release: %{product_release}
Vendor: EMC Corp.
Summary: %{product_name}-%{product_version}.%{product_release}
License: FIXME
Group: System
Requires: java-1.8.0, telnet, openssh, nginx >= 1.5.0-10
# Bypass RPM automatic dependency generation to avoid it
# mess up for java libs
Autoreq: 0

%description
%{product_brand} services

%prep

%build

%install

    
%post
/sbin/ldconfig %{_prefix}/lib
# check if it is base kit
fgrep -qL "ViPRBase" "/etc/ImageVersion" 2>/dev/null && IS_BASEKIT="yes"
if [ -n "${IS_BASEKIT}" ]  ; then
    #/sbin/chkconfig SuSEfirewall2_init on
    #/sbin/chkconfig --add SuSEfirewall2_setup
    /usr/bin/systemctl enable syncntp
    /usr/bin/systemctl enable keepalived
    /usr/bin/systemctl enable SuSEfirewall2_init
    /usr/bin/systemctl enable SuSEfirewall2
else
    # Force firewall off.
    /usr/bin/systemctl stop SuSEfirewall2_init
    /usr/bin/systemctl stop SuSEfirewall2
    /bin/echo "exit 0" > /etc/sysconfig/SuSEfirewall2 
    /bin/echo "exit 0" > /etc/sysconfig/SuSEfirewall2-template

    /usr/bin/systemctl disable syncntp
    /usr/bin/systemctl disable keepalived
fi
/usr/bin/systemctl enable boot-ovfenv
/usr/bin/systemctl enable nginx
/usr/bin/systemctl enable ntpd
/usr/bin/systemctl enable connectemc
/usr/bin/systemctl enable storageos-installer
/usr/bin/systemctl enable ipchecktool
/etc/storageos/storageos enable

[ -e /usr/sbin/keepalived ] || ln -s /usr/local/keepalived/sbin/keepalived /usr/sbin/keepalived
[ -e /usr/sbin/nginx ] || ln -s /usr/local/nginx/sbin/nginx /usr/sbin/nginx
[ -e /etc/nginx/mime.types ] || ( mkdir -p /etc/nginx/ && ln -s /usr/local/nginx/conf/mime.types /etc/nginx/mime.types )

if [ -z "${DO_NOT_START}" ] ; then
    /etc/storageos/boot-ovfenv start
    /usr/bin/systemctl start ipchecktool
    /etc/storageos/storageos start
    /usr/bin/systemctl start nginx
    /usr/bin/systemctl start ntpd
    /usr/bin/systemctl start connectemc
    if [ -n "${IS_BASEKIT}" ]; then
        /usr/bin/systemctl start syncntp
        /usr/bin/systemctl start keepalived
        /usr/bin/systemctl start SuSEfirewall2_init
        /usr/bin/systemctl start SuSEfirewall2
    fi
fi

%postun
/sbin/ldconfig
rm -rf %{_prefix}/lib
rm -rf %{_prefix}/bin
rm -rf %{_prefix}/pylib

%preun
if [ -f /etc/storageos/storageos ] ; then
  /etc/storageos/storageos stop
  /etc/storageos/storageos disable
fi

/usr/bin/systemctl stop boot-ovfenv
/usr/bin/systemctl disable boot-ovfenv
/usr/bin/systemctl stop nginx
/usr/bin/systemctl disable nginx
/usr/bin/systemctl stop connectemc
/usr/bin/systemctl disable connectemc
/usr/bin/systemctl stop syncntp
/usr/bin/systemctl disable syncntp
/usr/bin/systemctl stop keepalived
/usr/bin/systemctl disable keepalived
/usr/bin/systemctl stop SuSEfirewall2
/usr/bin/systemctl stop SuSEfirewall2_init
/usr/bin/systemctl stop ipchecktool
/usr/bin/systemctl disable ipchecktool

%clean

%files
%defattr(-,root,root)
%attr(-,storageos,storageos) %{_prefix}
/etc/nginx/error
/opt/storageos/etc/product
/opt/storageos/etc/productbase
/opt/storageos/etc/gitrevision
/opt/storageos/etc/gitrepo
/opt/storageos/etc/gitbranch
/etc/getovfproperties
/etc/getplatform
/etc/genconfig
/etc/genconfig.d/genconfig-core
/etc/genconfig.d/api
/etc/genconfig.d/auth
/etc/genconfig.d/connectemc
/etc/genconfig.d/controller
/etc/genconfig.d/coordinator
/etc/genconfig.d/db 
/etc/genconfig.d/dbclient
/etc/genconfig.d/dns
/etc/genconfig.d/firewall
/etc/genconfig.d/geo
/etc/genconfig.d/geodb
/etc/genconfig.d/network
/etc/genconfig.d/nginx
/etc/genconfig.d/ntp
/etc/genconfig.d/password
/etc/genconfig.d/portal
/etc/genconfig.d/security
/etc/genconfig.d/ssh
/etc/genconfig.d/ssh_auth_key
/etc/genconfig.d/ssl
/etc/genconfig.d/system
/etc/genconfig.d/syssvc
/etc/genconfig.d/vasa
/etc/genconfig.d/ipsec
/etc/genconfig.d/test
/etc/genconfig.d/boot.manifest
/etc/gentmpl
/etc/systool
/etc/diagtool
/etc/powerofftool
/etc/ipsectool
/etc/diskresizetool
/etc/gatherheapdumps
/etc/mkdisk.sh
/etc/getnic
%config /etc/sysconfig/SuSEfirewall2-template
%config /etc/sysconfig/SuSEfirewall2.d/services/storageos
%config /etc/sysconfig/scripts/FWiptables-template
/etc/patch-props.defaults
/etc/ovf-env.README
%attr(700,root,root) /etc/cron.hourly/cleanup_logs
/etc/storageos/boot-ovfenv
/etc/storageos/storageos
/etc/storageos/syncntp
/etc/storageos/installer
/etc/storageos/ipchecktool
/etc/logrotate.d/storageserver
%config /etc/keepalived/keepalived-IPv4-template.conf
%config /etc/keepalived/keepalived-IPv6-template.conf
%config /etc/keepalived/keepalived-dual-template.conf
%config /etc/nginx/nginx-IPv4-template.conf
%config /etc/nginx/nginx-IPv6-template.conf
%config /etc/nginx/nginx-dual-template.conf
%config /etc/nginx/vasasvc-server-common.conf
%config /etc/nginx/vasasvc-server-dual-template.conf
%config /etc/nginx/vasasvc-server-IPv4-template.conf
%config /etc/nginx/vasasvc-server-IPv6-template.conf
%config /etc/nginx/storageos-vasasvc.conf
%config /etc/nginx/locations.conf
%config /etc/nginx/upstream.conf
%config /etc/nginx/upstream-template.conf
/etc/nginx/api-error.conf
/etc/nginx/portal-error.conf
%attr(100,root,root) /etc/nginx/nginx.wrapper
%config /etc/sysconfig/storageos
%config /etc/sysconfig/connectemc
%config /etc/sysconfig/ntp
%config %attr(400,storageos,storageos) /etc/config.defaults
%config %attr(400,storageos,storageos) /etc/.ovfenv.properties
%config %attr(400,storageos,storageos) /etc/.iterable.properties
%config %attr(750,storageos,storageos) /data/db
%config %attr(750,storageos,storageos) /data/geodb
%config %attr(-,storageos,storageos) /data/zk
%config %attr(-,storageos,storageos) /object/rmt
%attr(-,storageos,storageos) /opt/connectemc/*
/etc/systemd/system/*
%attr(-,storageos,storageos) /var/run/storageos
%config /etc/sudoers.d/storageos

