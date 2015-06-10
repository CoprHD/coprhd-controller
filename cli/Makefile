#
# Copyright (c) 2012 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#
# etc/Makefile
#
 

include ../Makefile.common

PRODUCT_NAME     := storageos-cli
PRODUCT_RELEASE  = $(GIT_REVISION)
PRODUCT          = $(PRODUCT_NAME)-$(PRODUCT_VERSION).$(PRODUCT_RELEASE)

MKDIR		?= /bin/mkdir -p
RM		?= /bin/rm
PYTHON		?= /usr/bin/python
PRINTF		= /usr/bin/printf
CHMOD		?= /bin/chmod
CHOWN		?= /bin/chown
LS		?= /bin/ls
HARD_LN		?= /bin/ln
CP		?= /bin/cp
TAR		?= /bin/tar
ATSIGN		?= @
OWNER		?= root
GROUP		?= root
DIRPERM		 = 0755

CLI_MAIN_MODULE = viprcli.py
CLI_MAIN	= viprcli

CLI_PROFILE	= viprcli.profile
CLI_INSTALLER	= package
LINUX		= Linux
CLI_BIN		= bin
CLI_CONFIG	= config
CLI_DOCS	= docs

VIPRCLI_DIR     = viprcli
VIPRINST_DIR     = install
CLI_SETUP_FILE  = setup.py
CLI_CNF_FILE    = setup.cfg
CLI_EZSETUP_FILE = ez_setup.py
CLI_PATH_FILE   = viprcli.pth


CLIPACKAGECONTENTS=					\
		$(CLI_CONFIG)				\
		$(CLI_DOCS)				\
		$(CLI_INSTALLER)			\
		$(CLI_PROFILE)

CLIINSTCONTENTS=					\
		$(CLI_SETUP_FILE)				\
		$(CLI_CNF_FILE)				\
		$(CLI_EZSETUP_FILE)			\
		$(CLI_PATH_FILE)
		
MAKE_DIR	= 					\
		  if [ ! -d $(1) ]; then		\
		     $(MKDIR)  $(1) > /dev/null;	\
		  fi;					\
                  cd $(1) > /dev/null;			\
                  if [ $$? -eq 0 ]; then        	\
                       pwd;                     	\
                  fi;

BUILD_BASE_CLI	?= $(shell				\
		     cd $(CWD); cd ../;			\
		     $(call MAKE_DIR, "$(BUILD_BASE)/cli")	\
		   )

BUILDDIR	= $(BUILD_BASE_CLI)

BINDIR		= $(shell				\
		    cd $(CLIBUILDDIR);			\
		    $(call MAKE_DIR, "bin")		\
		   )

VIPRDIR		= $(shell				\
		    cd $(CLIBUILDDIR);			\
		    $(call MAKE_DIR, "viprcli")		\
		   )	

VIPRINSTDIR	= $(shell				\
		    cd $(CLIBUILDDIR);			\
		    $(call MAKE_DIR, "install")		\
		   )

THIRDPARTY_CLI_PACKAGE = $(shell				\
			   cd $(CWD); cd ..;  			\
			   pwd					\
			 )

BUILD_MODULES	=							\
	cd $(CLIBINDIR) >/dev/null;					\
	if [[ $$? -ne 0 ]];						\
	then								\
	   $(PRINTF) "%-40s : %s\n" "$(CLIBINDIR)" "[Fail]";		\
	   retval=1;							\
	else								\
	   $(PRINTF) "=====>$(CLIBINDIR)\n";				\
	   $(PRINTF) "%-40s : %s\n" "Thirdparty dependency package check" "[Start]";\
	   skipflag=1;							\
	   for i in "argparse" "requests";				\
	   do								\
	      $(PYTHON) -c "import $$i; print $$i" >/dev/null 2>&1;	\
	      if [[ $$? -ne 0 ]];					\
	      then							\
		 $(PRINTF) "%-40s : %s\n" "$$i" "[Not Found]";		\
		 skipflag=1;						\
	      else							\
		 $(PRINTF) "%-40s : %s\n" "$$i" "[Found]";		\
	      fi;							\
	   done;							\
	   $(PRINTF) "%-40s : %s\n" "Thirdparty dependency package check" "[Done]";\
	   echo $(PRODUCT) >  $(CLIBINDIR)/ver.txt;			\
	   $(CHMOD) $(DIRPERM) $(CLIBINDIR) > /dev/null 2>&1;		\
	   $(CHOWN) $(OWNER):$(GROUP) $(CLIBINDIR) > /dev/null 2>&1;	\
	   module_files=`$(LS) *.py`;					\
	   for i in $$module_files;					\
	   do								\
	      $(CHMOD) 0444 $$i >/dev/null 2>&1;			\
	      $(CHOWN) $(OWNER):$(GROUP) $$i >/dev/null 2>&1;		\
	      if [[ $$skipflag -eq 1 ]];				\
	      then							\
		 $(PRINTF) "%-40s : %s\n" "$(PYTHON) $$i" 		\
					"[Syntax check skipped:Pass]";	\
	         continue;						\
	      fi;							\
	      pythonOut=`$(PYTHON) $$i --help 2>&1`; 			\
	      if [[ $$? -ne 0 ]];					\
	      then							\
	         $(PRINTF) "%-40s : %s\n" "$(PYTHON) $$i" "[Fail]";	\
		 $(PRINTF) "   $$pythonOut\n\n";			\
		 retval=1;						\
	      else							\
	         $(PRINTF) "%-40s : %s\n" "$(PYTHON) $$i" "[Pass]";	\
	      fi;							\
	   done;							\
           $(RM) -rf *.pyc lib64 ./$(CLI_MAIN) > /dev/null 2>&1;	\
	   $(CHMOD) 0755 ./$(CLI_MAIN_MODULE) >/dev/null;		\
	   lnOut=`$(HARD_LN) -s ./$(CLI_MAIN_MODULE) ./$(CLI_MAIN) 2>&1`;    \
	   if [[ $$? -ne 0 ]];						\
	   then								\
	      $(PRINTF) "%-40s : %s\n"					\
			"$(HARD_LN) -s ./$(CLI_MAIN_MODULE) ./$(CLI_MAIN)"	\
			"[Fail]";					\
	      $(PRINTF) "     $(CLIBINDIR): $$lnOut\n\n";		\
	      retval=1;							\
	   else								\
	      $(CHMOD) 0755 ./$(CLI_MAIN) > /dev/null 2>&1;		\
	      $(CHOWN) $(OWNER):$(GROUP) ./$(CLI_MAIN) >/dev/null 2>&1;	\
	      $(PRINTF) "%-40s : %s\n" "./$(CLI_MAIN)" "[Pass]";	\
	   fi;								\
	fi
	
BUILD_PACKAGE	=							\
	cd $(CLIBASEDIR) > /dev/null;					\
	$(PRINTF) "=====>$(CLIBUILDDIR)\n";				\
	module_files=`echo $(CLIPACKAGECONTENTS)`;			\
	for i in $$module_files;					\
	do								\
	   $(RM) -rf $(CLIBUILDDIR)/$$i > /dev/null 2>&1;		\
	   cpOutput=`$(CP) -r $$i $(CLIBUILDDIR)/. 2>&1`;		\
	   if [[ $$? -eq 0 ]]; then					\
	      $(PRINTF) "%-40s : %s\n" "$$i" "[Pass]";			\
	      $(RM) -rf $(CLIBUILDDIR)/$$i/.svn > /dev/null 2>&1;	\
	   else								\
	      $(PRINTF) "%-40s : %s\n" "$$i" "[Fail]";			\
	      $(PRINTF) "     $(CP) -r $(CLIBASEDIR)/$$i . Failed"	\
	      $(PRINTF) " :$$cpOutput\n";				\
	      retval=1;							\
	   fi;								\
	done;								\
	cpOutput=`$(CP) *.*  $(CLIBUILDDIR)/. 2>&1`;         \
        cpOutput=`$(CP)  ./src/*.* $(CLIBUILDDIR)/$(VIPRCLI_DIR)/. 2>&1`;            \
        if [[ $$retval -eq 0 ]]; then					\
	   cd $(CLIBUILDDIR) > /dev/null;				\
	   $(CHMOD) -R $(DIRPERM) $(CLI_INSTALLER) >/dev/null 2>&1;	\
	   $(CHMOD) 0755 $(CLI_INSTALLER)/* >/dev/null 2>&1;		\
	   $(CHOWN) -R $(ROOT):$(GROUP) $(CLI_INSTALLER) >/dev/null 2>&1;\
	   $(CHMOD) $(DIRPERM) $(CLI_CONFIG) >/dev/null 2>&1;		\
	   $(CHMOD) 0644 $(CLI_CONFIG)/* >/dev/null 2>&1;		\
	   $(CHOWN) -R $(ROOT):$(GROUP) $(CLI_CONFIG) >/dev/null 2>&1;	\
	   $(CHMOD) $(DIRPERM) $(CLI_DOCS) >/dev/null 2>&1;		\
	   $(CHMOD) 0444 $(CLI_DOCS)/* >/dev/null 2>&1;			\
	   $(CHOWN) -R $(ROOT):$(GROUP) $(CLI_DOCS) >/dev/null 2>&1;	\
	   $(CHMOD) 0755 $(CLI_PROFILE) >/dev/null 2>&1;		\
	   $(CHOWN) -R $(ROOT):$(GROUP)	$(CLI_PROFILE) >/dev/null 2>&1;	\
									\
	   if [[ $$retval -eq 0 ]]; then				\
              cd $(VIPRCLI_DIR) > /dev/null;                           \
              $(CHMOD) 0755 ./$(CLI_MAIN_MODULE) >/dev/null;            \
	      lnOut=`$(HARD_LN) -s $(CLI_MAIN_MODULE) $(CLI_MAIN) 2>&1`; \
              if [[ $$? -ne 0 ]];                                       \
              then                                                      \
                  $(PRINTF) "%-40s : %s\n"                               \
                  "$(HARD_LN) -s ./$(CLI_MAIN_MODULE) ./$(CLI_MAIN)"     \
                  "[Fail]";                                              \
                  $(PRINTF) "     $(CLIBINDIR): $$lnOut\n\n";            \
                  retval=1;                                              \
              else                                                            \
                 $(CHMOD) 0755 $(CLI_MAIN) > /dev/null 2>&1;           \
                 $(CHOWN) $(OWNER):$(GROUP) $(CLI_MAIN) >/dev/null 2>&1;       \
                 $(PRINTF) "%-40s : %s\n" "./$(CLI_MAIN)" "[Pass]";  \
              fi;                                                       \
              echo $(PRODUCT) >  ver.txt;			\
              cd $(CLIBUILDDIR)/$(CLI_INSTALLER) > /dev/null;            \
              inst_files=`echo $(LS) ../$(VIPRINST_DIR)/*.*`;             \
              tarOutput=`$(TAR) -cvzf $(PRODUCT).tar.gz			\
                        ../$(VIPRCLI_DIR)  ../$(CLI_SETUP_FILE)  ../$(CLI_PROFILE)  \
                        ../$(CLI_CNF_FILE)  ../$(CLI_EZSETUP_FILE)  ../$(CLI_PATH_FILE)   \
                        ../$(CLI_CONFIG) ../$(CLI_DOCS) 2>&1` &&	\
	      lnOut=`$(HARD_LN) -s ./$(PRODUCT).tar.gz ./$(PRODUCT_NAME).tar.gz  2>&1`;\
	      if [[ $$? -ne 0 ]]; then					\
	         $(PRINTF) "%-40s : %s\n"				\
		    "$(CLIBUILDDIR)/$(CLI_INSTALLER)/$(PRODUCT).tar.gz" \
		    "[Failed]";						\
		 $(PRINTF) " :$$tarOutput\n";				\
		 retval=1;						\
	      else							\
		 $(CHMOD) 0660 ./$(PRODUCT).tar.gz>/dev/null 2>&1;	\
		 $(CHOWN) $(ROOT):$(GROUP)				\
		   ./$(PRODUCT).tar.gz >/dev/null 2>&1;			\
		 $(PRINTF) "%-40s : %s\n"				\
		 "$(CLIBUILDDIR)/$(CLI_INSTALLER)/$(PRODUCT).tar.gz"	\
		 "[Created]";						\
		 cd $(CLIBUILDDIR) > /dev/null;            \
		 $(RM) -rf ./$(VIPRCLI_DIR)			\
			 ./$(VIPRINST_DIR) $(CLI_INSTALLER_LINUX) >/dev/null 2>&1;			\
	      fi;							\
	   fi;								\
	fi;

all:: install_cli

install_cli:
	$(eval CWD 		:= $(shell pwd))
	$(eval CLIBASEDIR 	:= $(CWD))
	$(eval CLISRCDIR 	:= $(CWD)/src)
	$(eval CLIPACKAGEDIR 	:= $(CWD)/package)
	$(eval CLIBUILDDIR 	:= $(BUILDDIR))	
	$(eval CLIBINDIR 	:= $(BINDIR))
	$(eval VIPRCLIDIR       := $(VIPRDIR))
	$(eval INST_DIR       := $(VIPRINSTDIR))
	$(eval CLITHIRDPARTYLINUX	:= $(THIRDPARTY_CLI_PACKAGE)/thirdparty/python/linux)

	@$(PRINTF) "%-40s : %s\n" "Make & Install of Sos Cli"		\
		"[In Progress..]";                             
	@cd $(CLISRCDIR) > /dev/null;					\
	retval=0;							\
	$(PYTHON) setup.py install --install-data=$(CLIBINDIR)		\
		--prefix=$(CLIBINDIR) > /dev/null; 			\
	if [[ $$? -eq 0 ]];						\
	then								\
	    $(BUILD_MODULES)						\
	else								\
	    retval=1;							\
	    $(PRINTF) "%-40s : %s\n" "Make & Install of Sos Cli"	\
			"[Failed]";					\
	fi;								\
	if [[ $$retval -eq 0 ]];					\
	then								\
	    $(BUILD_PACKAGE)						\
	    $(PRINTF) "%-40s : %s\n" "Make & Install of Sos Cli"	\
			"[Done]";					\
	else								\
	    $(PRINTF) "%-40s : %s\n" "Make & Install of Sos Cli"	\
			"[Failed]";					\
	fi;

clean clobber::
	$(eval CWD 		:= $(shell pwd))
	$(eval CLIBASEDIR 	:= $(CWD))
	$(eval CLIBUILDDIR 	:= $(BUILDDIR))
	@if [[ -n $(CLIBUILDDIR) ]]; then				\
	   $(RM) -rf $(CLIBUILDDIR);					\
	   $(PRINTF) "%-40s : %s\n" "$(CLIBUILDDIR)" "[Deleted]";      \
	fi;

# END $Rev: XXX $

