/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.password;


import com.emc.storageos.db.client.model.PasswordHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.*;

public class Password {
    private static final Logger _log = LoggerFactory.getLogger(Password.class);

    private String username;
    private String password;
    private String oldPassword;
    private PasswordHistory passwordHistory;

    private final static String specialCharacters = "~!@#$%^&*()-_+=,<.>?/`;:'\"{}[] ";

    private List<CharInString> alphabetic = new ArrayList<CharInString>();
    private List<CharInString> numeric = new ArrayList<CharInString>();
    private List<CharInString> lowercase = new ArrayList<CharInString>();
    private List<CharInString> uppercase = new ArrayList<CharInString>();
    private List<CharInString> special = new ArrayList<CharInString>();
    private List<CharInString> others = new ArrayList<CharInString>();

    public Password(String password) {
        this(null, password);
    }

    public Password(String oldPassword, String password) {
        this(null, oldPassword, password);
    }

    public PasswordHistory getPasswordHistory() {
        return passwordHistory;
    }

    public void setPasswordHistory(PasswordHistory passwordHistory) {
        this.passwordHistory = passwordHistory;
    }

    public Password(String username, String oldPassword, String password) {
        this.username = username;
        this.oldPassword = oldPassword;
        this.password = password;
        analysePassword();
    }

    public String getUsername() {
        return username;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public String getPassword() {
        return password;
    }

    private void analysePassword() {
        if (password == null) {
            return;
        }

        for (int i=0; i<password.length(); i++) {
            char c = password.charAt(i);
            CharInString charInString = new CharInString(i, c);
            if (Character.isDigit(c)) {
                numeric.add(charInString);
            } else if (Character.isLetter(c)) {
                alphabetic.add(charInString);
                if (Character.isUpperCase(c)) {
                    uppercase.add(charInString);
                } else if (Character.isLowerCase(c)) {
                    lowercase.add(charInString);
                }
            } else if (specialCharacters.indexOf(c) != -1) {
                special.add(charInString);
            } else {
                others.add(charInString);
            }
        }
    }

    private class CharInString {
        private int index;
        private char character;

        CharInString(int index, char character) {
            this.index = index;
            this.character = character;
        }

        int getIndex() {
            return index;
        }

        char getCharacter() {
            return character;
        }
    }

    /**
     * Returns the number of digits in this password.
     *
     * @return  number of digits in the password
     */
    public int getNumberOfDigits()
    {
        return numeric.size();
    }

    /**
     * Returns the number of alphabetical characters in this password.
     *
     * @return  number of alphabetical characters in this password
     */
    public int getNumberOfAlphabets()
    {
        return alphabetic.size();
    }

    /**
     * Returns the number of uppercase characters in this password.
     *
     * @return  number of uppercase characters in this password
     */
    public int getNumberOfUppercase()
    {
        return uppercase.size();
    }

    /**
     * Returns the number of lowercase characters in this password.
     *
     * @return  number of lowercase characters in this password
     */
    public int getNumberOfLowercase()
    {
        return lowercase.size();
    }

    /**
     * Returns the number of speical characters in this password.
     *
     * @return  number of whitespace characters in this password
     */
    public int getNumberOfSpeicial()
    {
        return special.size();
    }


    public long getLatestChangedTime() {
        List<Map.Entry<String,Long>> l = getSortedPasswordByTime();
        if (l != null && l.size() > 0) {
            return l.get(0).getValue();
        } else {
            return 0;
        }
    }

    public List<String> getPreviousPasswords(int number) {
        List<String> passwords = new ArrayList<String>();

        List<Map.Entry<String,Long>> l = getSortedPasswordByTime();
        if (l == null ) {
            return passwords;
        }

        int length = Math.min(number, l.size());
        for (int i=0; i<length; i++) {
            passwords.add(l.get(i).getKey());
            _log.info(MessageFormat.format("password {0} modify time: {1}", i, l.get(i).getValue()));
        }
        return passwords;
    }


    private List<Map.Entry<String, Long>> getSortedPasswordByTime() {
        PasswordHistory lph = getPasswordHistory();
        if (lph == null || lph.getUserPasswordHash() == null) {
            return null;
        }

        ArrayList<Map.Entry<String,Long>> list = new ArrayList<Map.Entry<String,Long>>(lph.getUserPasswordHash().entrySet());
        Collections.sort(list,
                new Comparator<Map.Entry<String, Long>>() {
                    public int compare(Map.Entry<String, Long> o1, Map.Entry<String, Long> o2) {
                        // for fixing CTRL-10305, it should NOT use intValue() of a long, as it may get overflow.
                        if (o2.getValue() == o1.getValue()) return 0;
                        else if (o2.getValue() > o1.getValue()) return 1;
                        else return -1;
                    }
                });

        return list;
    }

}
