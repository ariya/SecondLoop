#!/usr/bin/env bash
# Builds build/SecondLoop.java, runs the tests, and checks that the
# single file compiles (against stand-ins for the FTC SDK classes).
set -euo pipefail
cd "$(dirname "$0")"

if command -v javac >/dev/null 2>&1; then
    JAVAC=(javac)
else
    JAVAC=(java -m jdk.compiler/com.sun.tools.javac.Main)
fi
# Java 8 language level, the most OnBot Java can be assumed to accept.
JAVAC+=(-source 8 -target 8 -Xlint:-options -encoding UTF-8)

python3 tools/amalgamate.py build/SecondLoop.java

rm -rf build/classes build/check
# The FTC stand-ins let src/ftc modules compile and be tested off the robot.
STUBS=$(find test/ftc-stubs -name '*.java')
"${JAVAC[@]}" -d build/classes $STUBS src/secondloop/*.java src/ftc/*.java test/secondloop/*.java
java -cp build/classes secondloop.ReplTest
java -cp build/classes secondloop.GamepadTest
java -cp build/classes secondloop.TelemetryTest
java -cp build/classes secondloop.MotorTest
java -cp build/classes secondloop.ServoTest
java -cp build/classes secondloop.BatteryTest
java -cp build/classes secondloop.ImuTest
java -cp build/classes secondloop.PinpointTest
java -cp build/classes secondloop.PublishTest

"${JAVAC[@]}" -d build/check $STUBS build/SecondLoop.java
echo "build/SecondLoop.java compiles."
