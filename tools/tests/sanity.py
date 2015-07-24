#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

import unittest
import os
import commands

class ProvisioningSanityTest (unittest.TestCase):
    def setUp (self):
        print "setup function."
        self.path = "logs"
	#if not os.path.exist(self.path):
        #    os.mkdir(self.path)

    # Test cases start
    def testIsilon (self):
        cmd = "./sanity lglw8173.lss.emc.com isilon"
        print "Launch isilon test: %s" % cmd 
	ret = self.runCommand(cmd)
        self.assertEqual (ret, 0)

    def testVnxblock (self):
        cmd = "./sanity lglw8173.lss.emc.com vnxblock"
        print "Launch vnxblock test: %s" % cmd
	ret = self.runCommand(cmd)
        self.assertEqual (ret, 0)

#    def testVnxfile (self):
#        cmd = "./sanity lglw8173.lss.emc.com vnxfile"
#        print "Launch vnx file test: %s" % cmd
#	ret = self.runCommand(cmd)
#        self.assertEqual (ret, 0)

    def testVmaxblock (self):
        cmd = "./sanity lglw8173.lss.emc.com vmaxblock"
        print "Launch vmaxblock test: %s" % cmd
	ret = self.runCommand(cmd)
        self.assertEqual (ret, 0)

    def testBlockSnapshot (self):
        cmd = "./sanity lglw8173.lss.emc.com blocksnapshot"
        print "Launch blocksnapshot test: %s" % cmd
        ret = self.runCommand(cmd)
        self.assertEqual (ret, 0)

    def testBlockMirror (self):
        cmd = "./sanity lglw8173.lss.emc.com blockmirror"
        print "Launch blockmirror test: %s" % cmd
        ret = self.runCommand(cmd)
        self.assertEqual (ret, 0)
    
    def testConsistencyGroup (self):
        cmd = "./sanity lglw8173.lss.emc.com blockconsistencygroup"
        print "Launch blockconsistencygroup test: %s" % cmd
        ret = self.runCommand(cmd)
        self.assertEqual (ret, 0)    

    #def testCombined_block (self):
    #    cmd = "./sanity lglw8173.lss.emc.com combined_block"
    #    print "Launch combined test: %s" % cmd
    #ret = self.runCommand(cmd)
    #    self.assertEqual (ret, 0)

    # Test cases finish
    def tearDown (self):
        print "tearDown function" 


    def runCommand (self, cmd):
        r = commands.getstatusoutput(cmd)
        print r[1]
        return r[0]
