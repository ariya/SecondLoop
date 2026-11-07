package secondloop;

import java.util.LinkedHashMap;
import java.util.Map;

/** A map such as {:a 1}. Keeps insertion order. */
public final class FormMap extends LinkedHashMap<Object, Object> {
    public FormMap() {
    }

    public FormMap(Map<?, ?> entries) {
        super(entries);
    }
}
