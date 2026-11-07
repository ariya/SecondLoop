package secondloop;

/** An error carrying a map of data, made by (ex-info "message" {:key value}). */
public final class ExInfo extends ScriptError {
    public final Object data;

    public ExInfo(String message, Object data) {
        super(message);
        this.data = data;
    }
}
