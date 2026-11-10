package secondloop;

/**
 * (version) tells which Second Loop is on the robot, since the file is copied
 * by hand through OnBot Java and it is easy to lose track. COMMIT is "main" in
 * development; a release build puts the short commit hash here.
 */
public final class Version {
    public static final int MAJOR = 1;
    public static final int MINOR = 1;
    public static final int PATCH = 9;
    public static final String COMMIT = "main";

    private Version() {
    }

    public static String number() {
        return MAJOR + "." + MINOR + "." + PATCH;
    }

    /** The first line a client sees, such as "Second Loop version 1.1.9 (commit ab123)". */
    public static String greeting(String commit) {
        String line = "Second Loop version " + number();
        return "main".equals(commit) ? line : line + " (commit " + commit + ")";
    }

    public static void install(Env env) {
        Builtins.define(env, new Builtin("version") {
            @Override
            public Object invoke(Interp interp, Object[] args) {
                checkArity(args, 0, 0);
                FormMap m = new FormMap();
                m.put(Keyword.of("version"), number());
                m.put(Keyword.of("major"), (double) MAJOR);
                m.put(Keyword.of("minor"), (double) MINOR);
                m.put(Keyword.of("patch"), (double) PATCH);
                m.put(Keyword.of("commit"), COMMIT);
                return m;
            }
        });
    }
}
