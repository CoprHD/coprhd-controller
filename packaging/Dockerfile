#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

FROM opensuse:13.2
 
#
# Install prerequisite software
#
# The following packages are required to RUN CoprHD and are mandatory
RUN zypper --non-interactive install keepalived wget openssh-fips telnet aaa_base arping2 python python-base mozilla-nss sudo ipcalc java-1_7_0-openjdk
# The following packages are required to build nginx and may be removed after nginx gets installed
RUN zypper --non-interactive install --no-recommends patch gcc-c++ pcre-devel libopenssl-devel tar make

#
# Need to get sipcalc - using "unstable" one from opensuse
#
ADD http://download.opensuse.org/repositories/home:/seife:/testing/openSUSE_13.2/x86_64/sipcalc-1.1.6-5.1.x86_64.rpm /
RUN rpm -Uvh --nodeps sipcalc-1.1.6-5.1.x86_64.rpm && \
 	rm -f sipcalc-1.1.6-5.1.x86_64.rpm
#
# Create users/groups
#
RUN groupadd storageos && useradd -d /opt/storageos -g storageos storageos
RUN groupadd svcuser && useradd -g svcuser svcuser

#
# Download, patch, compile, and install nginx, clean up the source files at the end
# All the commands are squeezed into a single RUN command in order to save some space within an image layer
#
RUN wget http://nginx.org/download/nginx-1.6.2.tar.gz && \
    wget --no-check-certificate https://github.com/yaoweibin/nginx_upstream_check_module/archive/v0.3.0.tar.gz && \
    wget --no-check-certificate https://github.com/openresty/headers-more-nginx-module/archive/v0.25.tar.gz && \
    tar xvzf nginx-1.6.2.tar.gz && tar xvzf v0.3.0.tar.gz && tar xvzf v0.25.tar.gz && \
    cd nginx-1.6.2 && patch -p1 < ../nginx_upstream_check_module-0.3.0/check_1.5.12+.patch && ./configure --add-module=../nginx_upstream_check_module-0.3.0 --add-module=../headers-more-nginx-module-0.25 --with-http_ssl_module --prefix=/usr --conf-path=/etc/nginx/nginx.conf && make && make install && cd .. && \
    rm -f nginx-1.6.2.tar.gz v0.3.0.tar.gz v0.25.tar.gz && \
    rm -rf nginx-1.6.2 nginx_upstream_check_module-0.3.0 headers-more-nginx-module-0.25

#
# Copy the storageos rpm into the container and install it without starting any service (since systemd is not yet available)
#
ADD storageos-*.x86_64.rpm /
RUN DO_NOT_START="yes" rpm -iv storageos-*.x86_64.rpm && \
    rm -f /storageos-*.x86_64.rpm

#
# Prepare a hook for the ovfenv.properties file
# An actual file needs to be provided when the container starts
#
RUN ln -s /coprhd/ovfenv.properties /etc

#
# Start /sbin/init in the background to enable systemd
#  
CMD ["/sbin/init"]

