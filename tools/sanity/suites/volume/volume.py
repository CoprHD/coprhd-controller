
import os
from suites.vipr import ViprTestSuite
from lifecycle import VolumeLifecycleTest

context = os.environ['TEST_CONTEXT']
print "Creating volume test suite for context %s" % context

# suite = ViprTestSuite()
# suite.addTest(VolumeLifecycleTest('test1Create'));
# suite.addTest(VolumeLifecycleTest('test2Read'));
# suite.addTest(VolumeLifecycleTest('test3Update'));
# suite.addTest(VolumeLifecycleTest('test4Delete'));
# 
# suite.addTest(VolumeLifecycleTest('test5Fail'))
# suite.addTest(VolumeLifecycleTest('test6ViprApi'))
