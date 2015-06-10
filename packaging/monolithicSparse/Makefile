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
# packaging/monolithicSparse/Makefile
#

SRC    := monolithicSparse.c
SOFILE  = $(OBJDIR)/$(SRC:.c=.so)
LIBS   := -ldl

include ../../Makefile.common

all:: $(SOFILE)

$(OBJDIR)/%.so: %.c
	@$(MKDIR) $(@D)
	$(ATECHO) $(CC) -o $(@F)
	$(ATSIGN)$(CC) $(CFLAGS) -shared -Wl,-soname,$(@F) -o $(@) $(<) $(LIBS) $(LDFLAGS)

clean::
	$(ATSIGN)$(RM) $(SOFILE)

test:
	$(ATSIGN)./mtest.sh

clean-test:
	$(ATSIGN)$(RM) m.so mtest input output

# END $Rev: XXX $

