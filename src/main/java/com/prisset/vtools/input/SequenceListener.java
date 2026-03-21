package com.prisset.vtools.input;

import org.lwjgl.glfw.GLFW;

public class SequenceListener {

    private static final int[] TARGET = {
        GLFW.GLFW_KEY_P,
        GLFW.GLFW_KEY_R,
        GLFW.GLFW_KEY_I
    };

    private static final int TIMEOUT_TICKS = 60; // 3 seconds at 20 TPS

    private int position;
    private long lastInputTick;
    private boolean completed;

    public SequenceListener() {
        reset();
    }

    /**
     * Feed a key press event. Returns true if the full sequence was just completed.
     */
    public boolean onKey(int keyCode, long currentTick) {
        if (completed) return false;

        if (keyCode == TARGET[position]) {
            position++;
            lastInputTick = currentTick;

            if (position >= TARGET.length) {
                completed = true;
                return true;
            }
        } else {
            // wrong key — restart matching from scratch
            position = 0;
            // but check if this key matches the FIRST element (overlap)
            if (keyCode == TARGET[0]) {
                position = 1;
                lastInputTick = currentTick;
            }
        }
        return false;
    }

    /**
     * Called every tick to handle timeout.
     */
    public void tick(long currentTick) {
        if (position > 0 && !completed) {
            if (currentTick - lastInputTick > TIMEOUT_TICKS) {
                position = 0;
            }
        }
    }

    public boolean isCompleted() {
        return completed;
    }

    public void reset() {
        position = 0;
        lastInputTick = 0;
        completed = false;
    }
}
