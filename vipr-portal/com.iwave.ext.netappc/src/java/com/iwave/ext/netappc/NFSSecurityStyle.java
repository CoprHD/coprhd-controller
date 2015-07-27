/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc;

public enum NFSSecurityStyle {
	sys("System"), krb5("Kerberos 5");

	private String label;

	NFSSecurityStyle(String label) {
		this.label = label;
	}

	public static NFSSecurityStyle valueOfLabel(String label) {
		if(label != null) {
			for (NFSSecurityStyle t : values()) {
				if (label.equals(t.label))
					return t;
			}
		}
		throw new IllegalArgumentException(label + " is not a valid label for NFSSecurityStyle");
	}

	@Override
	public String toString() {
		return label;
	}

	public static NFSSecurityStyle valueOfName(String label) {
		if(label != null) {
			for (NFSSecurityStyle t : values()) {
				if (label.equals(t.name()))
					return t;
			}
		}
		throw new IllegalArgumentException(label + " is not a valid label for NFSSecurityStyle");
	}
}
