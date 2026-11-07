package secondloop;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Helpers written in the REPL language itself, evaluated into the global
 * environment at startup. Anything defined here can be redefined from the REPL.
 */
public final class Prelude {
    static final String SOURCE = ""
            + "(defn clamp\n"
            + "  \"Limits x to the range lo..hi, e.g. (clamp power -1 1).\"\n"
            + "  [x lo hi]\n"
            + "  (max lo (min hi x)))\n"
            + "\n"
            + "(defn deadband\n"
            + "  \"0 when x is within width of zero, e.g. to ignore joystick drift.\"\n"
            + "  [x width]\n"
            + "  (if (< (abs x) width) 0 x))\n"
            + "\n"
            + "(defn lerp\n"
            + "  \"a when t is 0, b when t is 1, and in between for 0 < t < 1.\"\n"
            + "  [a b t]\n"
            + "  (+ a (* t (- b a))))\n"
            + "\n"
            + "(defn wrap-degrees\n"
            + "  \"The same angle within -180..180 (exclusive of 180), e.g. for heading error.\"\n"
            + "  [deg]\n"
            + "  (- (mod (+ deg 180) 360) 180))\n"
            + "\n"
            + "(defn wrap-radians\n"
            + "  \"The same angle within -PI..PI (exclusive of PI).\"\n"
            + "  [rad]\n"
            + "  (- (mod (+ rad Math/PI) (* 2 Math/PI)) Math/PI))\n"
            + "\n"
            + "(defn partial\n"
            + "  \"f with its first arguments filled in, e.g. ((partial clamp 1.5) -1 1).\"\n"
            + "  [f & args]\n"
            + "  (fn [& more] (apply f (concat args more))))\n"
            + "\n"
            + "(defn comp\n"
            + "  \"Chains functions right to left: ((comp f g) x) is (f (g x)).\"\n"
            + "  [& fs]\n"
            + "  (if (empty? fs)\n"
            + "    (fn [x] x)\n"
            + "    (reduce (fn [f g] (fn [& args] (f (apply g args)))) fs)))\n"
            + "\n"
            + ";; The control loop running in the background, shared by every connection.\n"
            + "(def control/current (atom nil))\n"
            + "\n"
            + "(defn control/stop\n"
            + "  \"Stops the loop started by control/start. True if one was running.\"\n"
            + "  []\n"
            + "  (let [f @control/current]\n"
            + "    (if f (future-cancel f) false)))\n"
            + "\n"
            + "(defn control/start\n"
            + "  \"Runs (f) in the background, so the REPL stays free. Any loop started\n"
            + "  earlier is stopped first, so only one loop drives the robot at a time.\"\n"
            + "  [f]\n"
            + "  (control/stop)\n"
            + "  (reset! control/current (future (f))))\n"
            + "\n"
            + "(defn control/running?\n"
            + "  \"True while the loop started by control/start is still running.\"\n"
            + "  []\n"
            + "  (let [f @control/current]\n"
            + "    (if f (not (future-done? f)) false)))\n";

    private Prelude() {
    }

    public static void load(Env globals) {
        Interp interp = new Interp(globals, new PrintWriter(new StringWriter()));
        for (Object form : Reader.readAll(SOURCE)) {
            interp.evalTopLevel(form);
        }
    }
}
