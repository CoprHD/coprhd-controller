from suites.vipr import ViprTestCase
import time
import os
import subprocess

class VolumeLifecycleTest(ViprTestCase):
# 
#     def test1Create(self):
#         context = os.environ['TEST_CONTEXT']
#         print "Running %s volume CREATE test..." % context
#         time.sleep(1)
#  
#     def test2Read(self):
#         context = os.environ['TEST_CONTEXT']
#         print "Running %s volume READ test..." % context
#         time.sleep(1)
#  
#     def test3Update(self):
#         context = os.environ['TEST_CONTEXT']
#         print "Running %s volume UPDATE test..." % context
#         time.sleep(1)
#  
#     def test4Delete(self):
#         context = os.environ['TEST_CONTEXT']
#         print "Running %s volume DELETE test..." % context
#         time.sleep(1)
#    
  
   
    def test5Fail(self):
        time.sleep(1)
        raise Exception("Failing a test on purpose!")
    
    def test6ViprApi(self):
        print("Logging in to ViPR")
        subprocess.check_call("python $VIPR_API_DIR/security login root ChangeMe1!", shell=True)
        subprocess.check_call("python $VIPR_API_DIR/project list", shell=True)
        subprocess.check_call("python $VIPR_API_DIR/volume list project", shell=True)
         