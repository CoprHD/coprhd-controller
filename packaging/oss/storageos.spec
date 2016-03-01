Name: %{product_name}
Version: %{product_version}
Release: %{product_release}
Vendor: EMC Corp.
Summary: %{product_name}-%{product_version}.%{product_release}
License: FIXME
Group: System
Requires: java-1.8.0, telnet, openssh
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
/bin/echo "exit 0" > /etc/sysconfig/SuSEfirewall2
/bin/echo "exit 0" > /etc/sysconfig/SuSEfirewall2-template

/usr/bin/systemctl enable keepalived
/usr/bin/systemctl enable boot-ovfenv
/usr/bin/systemctl enable nginx
/usr/bin/systemctl enable storageos-installer
/etc/storageos/storageos enable
    
if [ -z "${DO_NOT_START}" ] ; then
    /usr/bin/systemctl stop SuSEfirewall2_init
    /usr/bin/systemctl stop SuSEfirewall2
    /etc/storageos/boot-ovfenv start
    /usr/bin/systemctl start keepalived
    /usr/bin/systemctl start nginx 
    /etc/storageos/storageos start
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
/usr/bin/systemctl stop syncntp
/usr/bin/systemctl disable syncntp
/usr/bin/systemctl stop keepalived
/usr/bin/systemctl disable keepalived
/usr/bin/systemctl stop SuSEfirewall2
/usr/bin/systemctl stop SuSEfirewall2_init

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
/etc/genconfig.d/controller
/etc/genconfig.d/coordinator
/etc/genconfig.d/db 
/etc/genconfig.d/dbclient
/etc/genconfig.d/geo
/etc/genconfig.d/geodb
/etc/genconfig.d/nginx
/etc/genconfig.d/password
/etc/genconfig.d/portal
/etc/genconfig.d/security
/etc/genconfig.d/ssh
/etc/genconfig.d/ssh_auth_key
/etc/genconfig.d/ssl
/etc/genconfig.d/syssvc
/etc/genconfig.d/ipsec
/etc/genconfig.d/test
/etc/genconfig.d/boot.manifest
/etc/gentmpl
/etc/systool
/etc/datatool
/etc/diagtool
/etc/powerofftool
/etc/ipsectool
/etc/gatherheapdumps
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
%config %attr(-,storageos,storageos) /data/db
%config %attr(-,storageos,storageos) /data/geodb
%config %attr(-,storageos,storageos) /data/zk
%config %attr(-,storageos,storageos) /object/rmt
/etc/systemd/system/*
%attr(-,storageos,storageos) /var/run/storageos
%config /etc/sudoers.d/storageos

