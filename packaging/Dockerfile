#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

FROM opensuse:13.2

#
# Add repos for opensuse 13.2 packages
#
RUN zypper --non-interactive addrepo --name suse-13.2-oss --no-gpgcheck http://download.opensuse.org/distribution/13.2/repo/oss/suse suse-13.2-oss && \
	zypper --non-interactive addrepo --name suse-13.2-oss-update --no-gpgcheck http://download.opensuse.org/repositories/openSUSE:/13.2:/Update/standard suse-13.2-oss-update && \
	zypper --non-interactive addrepo --name suse-13.2-appliances --no-gpgcheck http://download.opensuse.org/repositories/Virtualization:/Appliances/openSUSE_13.2 suse-13.2-appliances && \
	zypper --non-interactive addrepo --name suse-13.2-containers --no-gpgcheck http://download.opensuse.org/repositories/Virtualization:/containers/openSUSE_13.2 suse-13.2-containers && \
	zypper --non-interactive addrepo --name suse-13.2-electronics --no-gpgcheck http://download.opensuse.org/repositories/electronics/openSUSE_13.2 suse-13.2-electronics && \
#	zypper --non-interactive addrepo --name suse-42.1-filesystems-ceph --no-gpgcheck http://download.opensuse.org/repositories/filesystems:/ceph/openSUSE_Leap_42.1 suse-42.1-filesystems-ceph && \
	zypper --non-interactive addrepo --name suse-13.2-seife --no-gpgcheck http://download.opensuse.org/repositories/home:/seife:/testing/openSUSE_13.2 suse-13.2-seife && \
	zypper --non-interactive addrepo --name suse-13.2-monitoring --no-gpgcheck http://download.opensuse.org/repositories/server:/monitoring/openSUSE_13.2 suse-13.2-monitoring && \
	zypper --non-interactive addrepo --name suse-13.2-network --no-gpgcheck http://download.opensuse.org/repositories/network:/utilities/openSUSE_13.2 suse-13.2-network && \
	zypper --non-interactive addrepo --name suse-13.2-python --no-gpgcheck http://download.opensuse.org/repositories/devel:/languages:/python/openSUSE_Leap_42.1 suse-13.2-python && \
	zypper --non-interactive addrepo --name suse-13.2-clib --no-gpgcheck http://download.opensuse.org/repositories/devel:/libraries:/c_c++/openSUSE_Leap_42.1 suse-13.2-clib && \
	zypper modifyrepo --priority  4 suse-13.2-oss && \
	zypper modifyrepo --priority  4 suse-13.2-oss-update && \
	zypper modifyrepo --priority  3 suse-13.2-appliances && \
	zypper modifyrepo --priority  3 suse-13.2-containers && \
	zypper modifyrepo --priority  3 suse-13.2-electronics && \
	zypper modifyrepo --priority  3 suse-13.2-seife && \
	zypper modifyrepo --priority  3 suse-13.2-monitoring && \
	zypper modifyrepo --priority  5 suse-13.2-network && \
	zypper modifyrepo --priority  5 suse-13.2-python && \
	zypper modifyrepo --priority  4 suse-13.2-clib && \
	zypper refresh

#
# Install prerequisite software
#
# The following packages are required to RUN CoprHD and are mandatory
RUN zypper --non-interactive install keepalived wget openssh-fips telnet aaa_base arping2 python python-base mozilla-nss sudo ipcalc java-1_8_0-openjdk sipcalc ansible

# The following packages are required to build nginx and may be removed after nginx gets installed
RUN zypper --non-interactive install --no-recommends patch gcc-c++ pcre-devel libopenssl-devel tar make

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
CMD ["/usr/lib/systemd/systemd"]

