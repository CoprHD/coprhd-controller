#-%emc-cr-s-shell-v2%-
#
# Copyright (c) 2012-2014, EMC Corporation. All Rights Reserved.
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
# Makefile
#

SUBDIRS := packaging etc syssvc/src cli

include Makefile.common
include Makefile.subdir

# Call ant to build/clean java
#
all clean::
	$(ATSIGN)$(GRADLE) $(BUILD_TYPE_PROPERTY) $(@)

# Local installation and building RPM, OVF and ISO packages
#
.PHONY: _install rpm ovf ova vsphere hyperv iso docker svt controller devkit
_install rpm ovf ova vsphere hyperv iso docker svt controller devkit: all
	$(ATSIGN)$(MAKE) -C packaging $(@)

# Top level clobber: delete everything in the build directory
#
clobber::
	$(ATSIGN)$(RM) -r $(BUILD_BASE)

# END
