package com.emc.storageos.hp3par.command;

public class Space {
	private Long reservedMiB;
	private Long rawReservedMiB;
	private Long usedMiB;
	private Long freeMiB;

	public Long getReservedMiB() {
        return reservedMiB;
    }
    public void setReservedMiB(Long reservedMiB) {
        this.reservedMiB = reservedMiB;
    }
	public Long getRawReservedMiB() {
        return rawReservedMiB;
    }
    public void setRawReservedMiB(Long rawReservedMiB) {
        this.rawReservedMiB = rawReservedMiB;
    }
	public Long getUsedMiB() {
        return usedMiB;
    }
    public void setUsedMiB(Long usedMiB) {
        this.usedMiB = usedMiB;
    }
	public Long getFreeMiB() {
        return freeMiB;
    }
    public void setFreeMiB(Long freeMiB) {
        this.freeMiB = freeMiB;
    }
}
