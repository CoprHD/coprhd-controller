import argparse
parser = argparse.ArgumentParser()
mandatory_args = parser.add_argument_group('mandatory arguments')

mandatory_args.add_argument('-user',
                    metavar='<user>',
                    dest='user',
                    help='user to be encrypted',
                    required=True)

mandatory_args.add_argument('-password',
                    metavar='<password>',
                    dest='password',
                    help='password to be encrypted',
                    required=True)

mandatory_args.add_argument('-securityfile',
                    metavar='<securityfile>',
                    dest='securityfile',
                    help='securityfile',
                    required=True)

mandatory_args.add_argument('-cinderuser',
                    metavar='<cinderuser>',
                    dest='cinderuser',
                    help='user account used by cinder service',
                    required=True)

args = parser.parse_args()


from Crypto.Cipher import ARC4
#obj1 = ARC4.new(getpass.getuser())
obj1 = ARC4.new(args.cinderuser)

cipher_text = obj1.encrypt(args.user)
security_file = open(args.securityfile, 'w+')
security_file.write(cipher_text)
security_file.write("\n")
cipher_text = obj1.encrypt(args.password)
security_file.write(cipher_text)
security_file.write("\n")
security_file.close()
