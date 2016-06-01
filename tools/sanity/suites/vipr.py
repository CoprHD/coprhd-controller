
import time
import unittest
import os


class ViprTestSuite(unittest.TestSuite):

    def setUp(self):
        print "ViprTestSuite set up..."
        time.sleep(1)

    def tearDown(self):
        print "ViprTestSuite tear down..."
        time.sleep(1)


class ViprTestCase(unittest.TestCase):
    
    def setUp(self):
        context = os.environ["TEST_CONTEXT"]
        suite = os.environ["TEST_SUITE"]
        print("\nViprTestCase set up for " + suite +" tests against " + context)
        time.sleep(1)

    def tearDown(self):
        print "ViprTestCase tear down...\n"
        time.sleep(1)
