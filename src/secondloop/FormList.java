package secondloop;

import java.util.ArrayList;
import java.util.Collection;

/** A list such as (+ 1 2). When evaluated, it is a function call or a special form. */
public final class FormList extends ArrayList<Object> {
    public FormList() {
    }

    public FormList(Collection<?> items) {
        super(items);
    }
}
