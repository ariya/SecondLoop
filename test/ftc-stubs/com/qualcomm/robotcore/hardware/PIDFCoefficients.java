package com.qualcomm.robotcore.hardware;

/** Stand-in for the FTC SDK class; only what Second Loop uses. */
public class PIDFCoefficients {
    public double p;
    public double i;
    public double d;
    public double f;

    public PIDFCoefficients(double p, double i, double d, double f) {
        this.p = p;
        this.i = i;
        this.d = d;
        this.f = f;
    }
}
