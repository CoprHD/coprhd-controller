/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util.validation;

import play.data.validation.Check;
import util.MessagesUtils;

/**
 * @author Chris Dail
 */
public abstract class LocalizedCheck extends Check {
    protected String message(String message, String... vars) {
        String localizedMessage = MessagesUtils.get(message, (Object[]) vars);
        if (this.checkWithCheck != null) {
            setMessage(localizedMessage);
        }
        return localizedMessage;
    }
}
