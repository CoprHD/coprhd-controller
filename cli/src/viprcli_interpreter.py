#!/usr/bin/python

# Copyright (c) 2012-13 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import cmd
import common
import commands
from customparser import Parser
from tokenize import generate_tokens
from cStringIO import StringIO


class ViPRInterpreter(cmd.Cmd):

    prompt = 'ViPR_CLI> '
    parser = Parser()
    cf = ""

    def do_viprcli(self, command):
        # pass
        # Command to be executed
        command = "viprcli " + command
        # Tokenize the command
        STRING = 1
        L2 = list(token[STRING] for token in
                  generate_tokens(StringIO(command).readline)
                  if token[STRING])
        # Check if this was a command other than authenticate
        if(L2[1] != "authenticate"):
            # If cf is set then use it else show a message
            if(len(self.cf) != 0):
                command = command + " -cf " + self.cf
        # run the command
        output = commands.getoutput(command)

        # Find the cf information
        if(L2[1] == "authenticate"):
            self.cf = ""
            L1 = list(token[STRING] for token in
                      generate_tokens(StringIO(output).readline)
                      if token[STRING])
            cf_length = len(L1) - 8
            for i in range(0, cf_length - 1):
                self.cf = self.cf + str(L1[5 + i])
        print output

    def complete_viprcli(self, text, line, begidx, endidx):
        before_length = len(line)
        line = line.rstrip()
        after_length = len(line)
        STRING = 1
        L = list(token[STRING] for token in
                 generate_tokens(StringIO(line).readline)
                 if token[STRING])
        count = len(L)
        completions = self.parser.get_list_of_options(line)
        if(count == 2 and (L[1] == "authenticate" or
                           L[1] == "meter" or L[1] == "monitor")):
            output = ""
            for o in self.parser.get_list_of_options(line):
                output = output + o + " "
            if(before_length == after_length):
                output = L[1] + " " + output
            return [output]
        if(count == 3):
            output = ""
            for o in self.parser.get_list_of_options(line):
                output = output + o + " "
            if(before_length == after_length
               and (L[2] in
                    self.parser.get_list_of_options(L[0] + " " + L[1]))):
                output = L[2] + " " + output
            return [output]

        return completions

    def emptyline(self):
        return

    def do_exit(self, line):
        return True

# Need to store cf information so that, after crtl+c there is no need to
# authenticate again
cf_info = ""


def main():
    global cf_info
    try:
        interpreter = ViPRInterpreter()
        if(len(cf_info) > 0):
            interpreter.cf = cf_info
        interpreter.cmdloop()
    except (KeyboardInterrupt):
        print ""
        cf_info = interpreter.cf
        main()
    except (EOFError):
        print ""

if __name__ == '__main__':
        main()

