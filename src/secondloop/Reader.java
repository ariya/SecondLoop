package secondloop;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Turns source text into forms: numbers, strings, symbols, keywords, lists, vectors, maps and sets. */
public final class Reader {
    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    private final String src;
    private int pos;

    private Reader(String src) {
        this.src = src;
    }

    /** Reads every form in src. Throws Incomplete if the last form is not finished yet. */
    public static List<Object> readAll(String src) {
        Reader reader = new Reader(src);
        List<Object> forms = new ArrayList<Object>();
        while (true) {
            reader.skipWhitespace();
            if (reader.atEnd()) {
                return forms;
            }
            forms.add(reader.read());
        }
    }

    private boolean atEnd() {
        return pos >= src.length();
    }

    private void skipWhitespace() {
        while (!atEnd()) {
            char c = src.charAt(pos);
            if (c == ';') {
                while (!atEnd() && src.charAt(pos) != '\n') {
                    pos++;
                }
            } else if (Character.isWhitespace(c) || c == ',') {
                pos++;
            } else {
                return;
            }
        }
    }

    private Object read() {
        skipWhitespace();
        if (atEnd()) {
            throw new Incomplete();
        }
        char c = src.charAt(pos);
        switch (c) {
            case '(':
                pos++;
                return readSequence(')', new FormList());
            case '[':
                pos++;
                return readSequence(']', new FormVector());
            case '{':
                pos++;
                return readMap();
            case '#':
                pos++;
                if (atEnd()) {
                    throw new Incomplete();
                }
                if (src.charAt(pos) == '{') {
                    pos++;
                    return readSet();
                }
                throw new SyntaxError("Unsupported syntax: #" + src.charAt(pos));
            case ')':
            case ']':
            case '}':
                pos++;
                throw new SyntaxError("Unmatched delimiter: " + c);
            case '"':
                pos++;
                return readString();
            case '\'': {
                pos++;
                FormList quoted = new FormList();
                quoted.add(Symbol.of("quote"));
                quoted.add(read());
                return quoted;
            }
            case '@': {
                pos++;
                FormList deref = new FormList();
                deref.add(Symbol.of("deref"));
                deref.add(read());
                return deref;
            }
            case '`':
            case '~':
            case '^':
            case '\\':
                pos++;
                throw new SyntaxError("Unsupported syntax: " + c);
            default:
                return readAtom();
        }
    }

    private <T extends List<Object>> T readSequence(char close, T items) {
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                throw new Incomplete();
            }
            if (src.charAt(pos) == close) {
                pos++;
                return items;
            }
            items.add(read());
        }
    }

    private FormMap readMap() {
        FormVector forms = readSequence('}', new FormVector());
        if (forms.size() % 2 != 0) {
            throw new SyntaxError("Map literal must contain an even number of forms");
        }
        FormMap map = new FormMap();
        for (int i = 0; i < forms.size(); i += 2) {
            if (map.containsKey(forms.get(i))) {
                throw new SyntaxError("Duplicate key: " + Printer.print(forms.get(i)));
            }
            map.put(forms.get(i), forms.get(i + 1));
        }
        return map;
    }

    private FormSet readSet() {
        FormSet set = new FormSet();
        for (Object item : readSequence('}', new FormVector())) {
            if (!set.add(item)) {
                throw new SyntaxError("Duplicate key: " + Printer.print(item));
            }
        }
        return set;
    }

    private String readString() {
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (atEnd()) {
                throw new Incomplete();
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (atEnd()) {
                throw new Incomplete();
            }
            char escaped = src.charAt(pos++);
            switch (escaped) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                default: throw new SyntaxError("Unsupported escape character: \\" + escaped);
            }
        }
    }

    private static boolean isDelimiter(char c) {
        return Character.isWhitespace(c) || "()[]{}\",;".indexOf(c) >= 0;
    }

    private Object readAtom() {
        int start = pos;
        while (!atEnd() && !isDelimiter(src.charAt(pos))) {
            pos++;
        }
        String token = src.substring(start, pos);
        if (token.equals("nil")) {
            return null;
        }
        if (token.equals("true")) {
            return Boolean.TRUE;
        }
        if (token.equals("false")) {
            return Boolean.FALSE;
        }
        if (token.startsWith(":")) {
            if (token.length() == 1) {
                throw new SyntaxError("Invalid token: :");
            }
            return Keyword.of(token.substring(1));
        }
        if (looksNumeric(token)) {
            if (!NUMBER.matcher(token).matches()) {
                throw new SyntaxError("Invalid number: " + token);
            }
            return Double.parseDouble(token);
        }
        return Symbol.of(token);
    }

    private static boolean looksNumeric(String token) {
        char first = token.charAt(0);
        if (Character.isDigit(first)) {
            return true;
        }
        return (first == '+' || first == '-' || first == '.')
                && token.length() > 1
                && (Character.isDigit(token.charAt(1)) || token.charAt(1) == '.');
    }
}
