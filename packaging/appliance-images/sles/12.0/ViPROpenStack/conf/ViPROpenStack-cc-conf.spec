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
# End Section define - Common
################################################################################

################################################################################
# Section define - Config
%define ViPROpenStackConfig ViPROpenStackConfig.sh
%define cinderConf cinder.conf

################################################################################

Name:       ViPROpenStack-cc-conf
Version:    1
Release:    1
Summary:    Default configuration for ViPROpenStack appliances
Vendor:     EMC
Group:      emc/asd/adg
License:    EMC
URL:        http://www.emc.com/
BuildArch:  noarch
BuildRoot:  %{_builddir}/%{name}
Requires:   adg-firstboot
AutoReq:    0
AutoProv:   0

%description
This package is used for default configurations for ViPROpenStack appliance built
using KIWI.

%undefine __check_files

%prep
[ -d ${RPM_BUILD_ROOT} ] && rm -rf ${RPM_BUILD_ROOT}
mkdir -p ${RPM_BUILD_ROOT}%{destDir}/firstboot/scripts
mkdir -p ${RPM_BUILD_ROOT}%{destDir}/conf

################################################################################
# Section prep - Config
cp %{sourceDir}/ViPROpenStackConfig.sh ${RPM_BUILD_ROOT}%{destDir}/firstboot/scripts/%{ViPROpenStackConfig}
cp %{sourceDir}/cinder.conf ${RPM_BUILD_ROOT}%{destDir}/conf/%{cinderConf}

# End Section prep - Config
################################################################################

%post

%files
%defattr (-,root,root,-)
################################################################################
# Section files - Firstboot
%attr (700,root,root) %{destDir}/firstboot/scripts/%{ViPROpenStackConfig}
%attr (600,root,root) %{destDir}/conf/%{cinderConf}

# End Section files - Firstboot
################################################################################

%changelog
* Tue Feb 09 2016 Amulya Lokesha <ApplianceDevelopmentGroup@emc.com> 0:1-1
- Initial release
