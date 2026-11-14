package secondloop;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * Runs the REPL on a laptop, without a robot.
 *   java -cp build/classes secondloop.LocalRepl          serve on port 9001, like the robot
 *   java -cp build/classes secondloop.LocalRepl --stdin  read from the terminal directly
 */
public final class LocalRepl {
    public static void main(String[] args) throws Exception {
        Env globals = Builtins.createGlobals();
        new PublishModule().install(globals); // (publish/setup ...) serves on port 5599, like the robot
        if (args.length > 0 && args[0].equals("--stdin")) {
            new Repl(globals, new PrintWriter(System.out)).run(
                    new BufferedReader(new InputStreamReader(System.in, "UTF-8")));
            return;
        }
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9001;
        ReplServer server = new ReplServer(globals, port);
        server.start();
        System.out.println(ReplServer.status(ReplServer.preferredAddress(ReplServer.localAddresses()), port, 0)
                + ". Try: ./secondloop.py localhost " + port);
        Thread.currentThread().join();
    }
}
