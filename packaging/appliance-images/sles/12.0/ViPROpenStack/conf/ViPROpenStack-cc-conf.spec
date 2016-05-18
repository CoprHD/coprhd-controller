#-%emc-cr-s-shell-v2%-
#
# Copyright (c) 2016, EMC Corporation. All Rights Reserved.
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.
# This software is protected, without limitation, by copyright law and
# international treaties.
# Use of this software and the intellectual property contained therein
# is expressly limited to the terms and conditions of the License
# Agreement under which it is provided by or on behalf of EMC.
#
#-%emc-cr-e-shell-v2%-
#

################################################################################
# Section define - Common
%define sourceDir /usr/src/packages/SOURCES
%define baseDir /opt
%define destDir %{baseDir}/ADG
%define confDir %{destDir}/conf
%define rabbitmqLicDir %{confDir}/rabbitmq
# End Section define - Common
################################################################################

################################################################################
# Section define - Config
%define ViPROpenStackConfig ViPROpenStackConfig.sh
%define cinderConf cinder.conf
%define rabbitmqLICENSE LICENSE
%define rabbitmqLICENSEMPL LICENSE-MPL-RabbitMQ

################################################################################

Name:       ViPROpenStack-cc-conf
Version:    2
Release:    1
Summary:    Default configuration for ViPROpenStack appliances
Vendor:     EMC
Group:      emc/asd/adg
License:    EMC
URL:        http://www.emc.com/
BuildArch:  noarch
BuildRoot:  %{_builddir}/%{name}
Requires:   adg-firstboot
Requires:	rabbitmq-server
AutoReq:    0
AutoProv:   0

%description
This package is used for default configurations for ViPROpenStack appliance built
using KIWI.

%undefine __check_files

%prep
[ -d ${RPM_BUILD_ROOT} ] && rm -rf ${RPM_BUILD_ROOT}
mkdir -p ${RPM_BUILD_ROOT}%{destDir}/firstboot/scripts
mkdir -p ${RPM_BUILD_ROOT}%{rabbitmqLicDir}


################################################################################
# Section prep - Config
cp %{sourceDir}/ViPROpenStackConfig.sh ${RPM_BUILD_ROOT}%{destDir}/firstboot/scripts/%{ViPROpenStackConfig}
cp %{sourceDir}/cinder.conf ${RPM_BUILD_ROOT}%{confDir}/%{cinderConf}
cp %{sourceDir}/rabbitmq/%{rabbitmqLICENSE} ${RPM_BUILD_ROOT}%{rabbitmqLicDir}
cp %{sourceDir}/rabbitmq/%{rabbitmqLICENSEMPL} ${RPM_BUILD_ROOT}%{rabbitmqLicDir}

# End Section prep - Config
################################################################################

%post
################################################################################
# Copying the MPL-2.0 license files to rabbitmq installation folders

for rabbitmqDir in $(ls -d /usr/lib64/rabbitmq/lib/rabbitmq_server*/)
do
	cp -f %{rabbitmqLicDir}/* ${rabbitmqDir}
done

cp -f %{rabbitmqLicDir}/* /usr/share/doc/packages/rabbitmq-server
################################################################################

%files
%defattr (-,root,root,-)
################################################################################
# Section files - Firstboot

%attr (700,root,root) %{destDir}/firstboot/scripts/%{ViPROpenStackConfig}
%attr (600,root,root) %{confDir}/%{cinderConf}

# End Section files - Firstboot
################################################################################

################################################################################
# Section files - RabbitMQ MPL-2.0 license files

%attr (644,root,root) %{rabbitmqLicDir}/%{rabbitmqLICENSE}
%attr (644,root,root) %{rabbitmqLicDir}/%{rabbitmqLICENSEMPL}

# End files - RabbitMQ MPL-2.0 license files
################################################################################

%changelog
* Fri Mar 11 2016 Padmakumar G Pillai <ApplianceDevelopmentGroup@emc.com> 0:2-1
- Added RabbitMQ MPL-2.0 license files

* Tue Feb 09 2016 Amulya Lokesha <ApplianceDevelopmentGroup@emc.com> 0:1-1
- Initial release
