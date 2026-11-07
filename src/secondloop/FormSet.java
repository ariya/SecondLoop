package secondloop;

import java.util.Collection;
import java.util.LinkedHashSet;

/** A set such as #{1 2}. Keeps insertion order. */
public final class FormSet extends LinkedHashSet<Object> {
    public FormSet() {
    }

    public FormSet(Collection<?> items) {
        super(items);
    }
}
