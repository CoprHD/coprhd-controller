#
# Copyright 2015 EMC Corporation
# All Rights Reserved
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
