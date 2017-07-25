package com.emc.storageos.hp3par.command;

public class CPGSpaceCommandResult {
    private Integer rawFreeMiB;
    private Integer usableFreeMiB;
	public Integer getRawFreeMiB() {
		return rawFreeMiB;
	}
	public void setRawFreeMiB(Integer rawFreeMiB) {
		this.rawFreeMiB = rawFreeMiB;
	}
	public Integer getUsableFreeMiB() {
		return usableFreeMiB;
	}
	public void setUsableFreeMiB(Integer usableFreeMiB) {
		this.usableFreeMiB = usableFreeMiB;
	}
    
}
