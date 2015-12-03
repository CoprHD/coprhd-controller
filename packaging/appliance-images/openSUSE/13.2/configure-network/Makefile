#-%emc-cr-s-shell-v2%-
#
# Copyright (c) 2012-2015, EMC Corporation. All Rights Reserved.
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
# configure-network/Makefile
#

##################################################
#  Setup
##################################################
WORKSPACE := $(shell pwd)/../configure-network
PRODUCT_VERSION := 3
PRODUCT_RELEASE := 4
RPM_REVISION := 3

RPMBUILD_SOURCEDIR := $(WORKSPACE)
RPMBUILD_BUILDROOT := $(WORKSPACE)/BUILDROOT
RPMBUILD_BUILDDIR := $(WORKSPACE)/BUILDDIR
RPMBUILD_TMP_DIR := $(WORKSPACE)/TMP
RPMBUILD_RPMS_DIR := $(WORKSPACE)/RPMS

.DEFAULT: all
.PHONY: build
all: build

RPMBUILD_FLAGS  = -bb \
	--buildroot "$(RPMBUILD_BUILDROOT)" \
	--define '__spec_build_pre /bin/true' \
	--define "_topdir $(RPMBUILD_BUILDDIR)" \
	--define "_builddir $(RPMBUILD_BUILDDIR)" \
	--define "_tmppath $(RPMBUILD_TMP_DIR)" \
	--define "_sourcedir $(RPMBUILD_SOURCEDIR)" \
	--define "_rpmdir $(RPMBUILD_RPMS_DIR)" \
	--define "product_version $(PRODUCT_VERSION).$(PRODUCT_RELEASE)" \
	--define "product_release $(RPM_REVISION)"

##################################################
#  Targets
##################################################
build:
	rpmbuild $(RPMBUILD_FLAGS) $(WORKSPACE)/configure-network.spec
