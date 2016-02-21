/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.sample;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.AbstractExecutionService;
import com.emc.sa.engine.service.Service;

@Service("SampleTableService")
public class SampleTableService extends AbstractExecutionService {
    @Param
    protected String text;
    @Bindable(itemType = Row.class)
    protected Row[] rows;

    @Override
    public void execute() throws Exception {
        ExecutionUtils.currentContext().logInfo("Text=%s", text);
        if (rows != null) {
            for (Row row : rows) {
                ExecutionUtils.currentContext().logInfo("row=%s", row);
            }
        }
    }

    public static class Row {
        @Param
        protected String name;
        @Param
        protected String virtualArray;
        @Param
        protected String virtualPool;

        public String toString() {
            return "name=" + name + ", virtualArray=" + virtualArray + ", virtualPool=" + virtualPool;
        }
    }

	@Override
	public void preLaunch() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void postLaunch() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void postcheck() throws Exception {
		// TODO Auto-generated method stub
		
	}
}
