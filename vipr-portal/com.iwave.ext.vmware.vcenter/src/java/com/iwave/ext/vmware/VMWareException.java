/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.vmware;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;

import com.vmware.vim25.KeyAnyValue;
import com.vmware.vim25.LocalizableMessage;
import com.vmware.vim25.LocalizedMethodFault;
import com.vmware.vim25.MethodFault;
import com.vmware.vim25.PlatformConfigFault;

public class VMWareException extends RuntimeException {
    private static final long serialVersionUID = 4559548891858414498L;

    public VMWareException(String message, Throwable t) {
        super(message, t);
    }

    public VMWareException(String message) {
        super(message);
    }

    public VMWareException(Throwable t) {
        this(getDetailMessage(t), t);
    }

    /**
     * Gets a detail message from a method fault, only if the argument is an instance of MethodFault.
     * 
     * @param t
     *            the throwable.
     * @return the detail message, or null if the argument is not a MethodFault.
     */
    public static String getDetailMessage(Throwable t) {
        if (t instanceof MethodFault) {
            return getDetailMessage((MethodFault) t);
        }
        return null;
    }

    /**
     * Gets a detail message from a method fault. This will attempt to return as much information as possible to
     * describe the reason for the method fault.
     * 
     * @param fault
     *            the method fault.
     * @return the detail message.
     */
    public static String getDetailMessage(MethodFault fault) {
        LocalizedMethodFault cause = fault.getFaultCause();
        if ((cause != null) && StringUtils.isNotBlank(cause.getLocalizedMessage())) {
            return cause.getLocalizedMessage();
        }
        StrBuilder sb = new StrBuilder();
        if (fault instanceof PlatformConfigFault) {
            String text = ((PlatformConfigFault) fault).getText();
            if (StringUtils.isNotBlank(text)) {
                sb.append(text);
            }
        }
        LocalizableMessage[] messages = fault.getFaultMessage();
        if (messages != null && messages.length > 0) {
            String message = getAsText(messages);
            if (StringUtils.isNotBlank(message)) {
                int index = sb.isEmpty() ? 0 : 1;
                sb.appendSeparator(" [", index);
                sb.append(message);
                sb.appendSeparator("]", index);
            }
        }

        return StringUtils.trimToNull(sb.toString());
    }

    /**
     * Gets the list of messages as text.
     * 
     * @param messages
     *            the messages.
     * @return the text value of the messages.
     */
    public static String getAsText(LocalizableMessage[] messages) {
        StrBuilder sb = new StrBuilder();
        if (messages != null && messages.length > 0) {
            for (LocalizableMessage message : messages) {
                sb.appendSeparator(", ");
                sb.append(getAsText(message));
            }
        }
        return StringUtils.trimToEmpty(sb.toString());
    }

    /**
     * Gets the message text from a localizable message.
     * 
     * @param message
     *            the message.
     * @return the message text.
     */
    public static String getAsText(LocalizableMessage message) {
        if (StringUtils.isNotBlank(message.getMessage())) {
            return message.getMessage();
        }
        else {
            // No message, provide the message key and arguments
            StrBuilder sb = new StrBuilder();
            sb.append(message.getKey());

            KeyAnyValue[] args = message.getArg();
            if ((args != null) && (args.length > 0)) {
                sb.append("[");
                for (int i = 0; i < args.length; i++) {
                    sb.appendSeparator(",", i);
                    sb.append(args[i].key).append("=").append(args[i].value);
                }
                sb.append("]");
            }
            return sb.toString();
        }
    }
}
