package secondloop;

import java.util.ArrayList;
import java.util.Collection;

/** A vector such as [1 2 3]. When evaluated, each element is evaluated. */
public final class FormVector extends ArrayList<Object> {
    public FormVector() {
    }

    public FormVector(Collection<?> items) {
        super(items);
    }
}
