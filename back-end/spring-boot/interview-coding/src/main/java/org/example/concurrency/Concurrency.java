package org.example.concurrency;

public class Concurrency {
    // ---- BROKEN VERSION (no volatile) ----
    private boolean flagUnsafe = false;
    private int valueUnsafe = 0;

    public void writerUnsafe() {
        valueUnsafe = 42;
        flagUnsafe = true;
    }

    public boolean isFlagUnsafe() {
        return flagUnsafe;
    }

    public int getValueUnsafe() {
        return valueUnsafe;
    }

    // ---- FIXED VERSION (volatile) ----
    private volatile boolean flagSafe = false;
    private int valueSafe = 0; // doesn't need volatile itself, flag write covers it

    public void writerSafe() {
        valueSafe = 42;
        flagSafe = true; // volatile write -> happens-before edge
    }

    public boolean isFlagSafe() {
        return flagSafe;
    }

    public int getValueSafe() {
        return valueSafe;
    }
}
