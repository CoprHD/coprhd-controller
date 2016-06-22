package exceptions;

/**
 * Created by gang on 6/22/16.
 */
public class Vmaxv3RestCallException extends RuntimeException {

    public Vmaxv3RestCallException() {
        super();
    }

    public Vmaxv3RestCallException(String message) {
        super(message);
    }

    public Vmaxv3RestCallException(String message, Throwable cause) {
        super(message, cause);
    }

    public Vmaxv3RestCallException(Throwable cause) {
        super(cause);
    }
}
