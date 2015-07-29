/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.isilon.restapi;

import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Class for IsilonStats keys and values
 */
public class IsilonStats {
    public enum AggregationType {
        custom,
        avg,
        last,
        max
    }

    public enum Scope {
        cluster,
        node
    }

    public enum Type {
        type_int32,
        type_int64,
        type_double,
        type_blob,
        type_string,
        type_list    // proto_opstat_list
    }

    public enum StatProtocolType {
        external,
        internal
    }

    public static class Protocol {
        protected String name;
        protected StatProtocolType type;
    }

    public static class StatKey {
        private AggregationType aggregation; // aggregation-type
        private String base_name; // base-name
        private String default_cache_time; // default-cache-time
        private String desc;
        private String real_name; // real-name
        private Scope scope;
        private Type type;
        private String units;
    }

    public static class StatValueCurrent<T> {
        protected ArrayList<String> error;
        protected long time;
        protected T value; // of type StatKey.type

        public T getValue() {
            return value;
        }

        public long getTime() {
            return time;
        }
    }

    public static class StatValueHistory<T> {
        protected ArrayList<String> error;
        protected HashMap<Long, T> values;

        public StatValueHistory() {
            error = new ArrayList<String>();
            values = new HashMap<Long, T>();
        }

        public HashMap<Long, T> getValues() {
            return values;
        }
    }

    public static class Policy {
        @JsonProperty("interval")
        protected int interval;
        @JsonProperty("peristent")
        protected boolean peristent;
        @JsonProperty("retention")
        protected long retention;
    }

    public static class Policies {
        @SerializedName("cache-time")
        @JsonProperty("cache-time")
        protected int cache_time;
        @JsonProperty("policies")
        protected ArrayList<Policy> policies;
    }

    private static final String OP_CLASS_WRITE = "write";
    private static final String OP_CLASS_READ = "read";

    public static class StatsClientProto {
        protected String client_id;
        protected String local_addr;
        protected String remote_addr;

        public static class OpClassValue {
            protected String class_name;
            protected long in_max;
            protected long in_min;
            protected float in_rate;
            protected long op_count;
            protected float op_rate;
            protected long out_max;
            protected long out_min;
            protected float out_rate;
            protected float time_avg;
            protected long time_max;
            protected long time_min;
        }

        protected ArrayList<OpClassValue> op_class_values;

        /**
         * get out rate for reads, if exists, else, return 0
         * 
         * @return
         */
        public float getOutBW() {
            float val = 0;
            for (OpClassValue value : op_class_values) {
                val += value.out_rate;
            }
            return val;
        }

        /**
         * get in rate, if exists, else, return 0
         * 
         * @return
         */
        public float getInBW() {
            float val = 0;
            for (OpClassValue value : op_class_values) {
                val += value.in_rate;
            }
            return val;
        }

        /**
         * get op counter for reads
         * 
         * @return
         */
        public long getReadOps() {
            for (OpClassValue value : op_class_values) {
                if (value.class_name.equals(OP_CLASS_READ)) {
                    return value.op_count;
                }
            }
            return 0;
        }

        /**
         * get op counter for writes
         * 
         * @return
         */
        public long getWriteOps() {
            for (OpClassValue value : op_class_values) {
                if (value.class_name.equals(OP_CLASS_WRITE)) {
                    return value.op_count;
                }
            }
            return 0;
        }

        /**
         * get client address for this record
         * 
         * @return
         */
        public String getClientAddr() {
            return remote_addr;
        }
    }
}
