/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
#ifndef _UTILS_HXX
#define _UTILS_HXX

#include <vector>

#define DATETIME_STAT_FORMAT 1
#define DATETIME_FILE_FORMAT 2

template<class T>
std::ostream& outputCollection(std::ostream& os,const T& C) {
    if (C.empty()) {
        return os << "()";
    } else {
        typename T::const_iterator it;
        for (it = C.begin(); it != C.end(); ++it) {
            os << (it == C.begin() ? "( " : ", ") << *it;
        }
            return os << ')';
    }
}


// Specialized for std::vector
template<class T>
std::ostream& operator<<(std::ostream& os, const std::vector<T>& v)
{
    return outputCollection(os, v);
}


class Utils
{
    public:
        // Split
        // - split a string into fields using separators
        // - consecutive separators are treated as multiple empty fields
        //
        // Arguments:
        //   - s         : input string
        //   - separator : separator character used for parsing
        //   - tokens    : resulting vector of tokens
        //   - maxTokens : maximum number of tokens (default: 0 - no limit)
        // Return value:
        //   The number of elements in the tokens vector
        //
        static size_t Split(const std::string& s, const std::string& separators, std::vector<std::string>& fields, size_t maxFields = 0)
        {
            fields.clear();
            for (size_t end, begin = 0; begin < s.size(); begin = end + 1)
            {
                end = s.find_first_of(separators, begin);
                //std::cout << "***: s=" << s << " begin=" << begin << " end=" << end <<  " size=" << s.size() << std::endl;
                //std::cout << "fields: " << fields << std::endl;
                if (end != std::string::npos and (maxFields == 0 or fields.size() < maxFields - 1))
                {
                    fields.push_back(s.substr(begin, end - begin));
                }
                else
                {
                    fields.push_back(s.substr(begin));
                    break;
                }
            }
            return fields.size();
        }

        // Tokenize
        // - split a string into tokens
        // - like split but multiple consequitive separators are treated as one
        // - in other words, there will be no empty tokens
        //
        static size_t Tokenize(const std::string& s, const std::string& separators, std::vector<std::string>& tokens)
        {
            std::vector<std::string> fields;
            Split(s, separators, fields);

            tokens.clear();
            for (size_t i = 0; i < fields.size(); i++)
            {
                if (fields[i].size() > 0)
                {
                    tokens.push_back(fields[i]);
                }
            }
            return tokens.size();
        }

        // GetCurrentTimeString
        // Return buffer containing formatted date and time string.
        static void getCurrentTimeString(char* tBuf, int tBufSize, int format)
        {
            time_t rawtime;
            struct tm *timeinfo;
            time(&rawtime);
            timeinfo = localtime(&rawtime);
            if (format == DATETIME_FILE_FORMAT)
            {
                strftime(tBuf,tBufSize,"%Y%m%d%H%M%S",timeinfo);
            }
            else
            {
                strftime(tBuf,tBufSize,"%Y%m%d,%X,",timeinfo);
            }
        }
};
#endif
