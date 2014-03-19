package com.bautabits.bautabit.dto;

import java.util.ArrayList;
import java.util.List;

public class PinConfiguration {
    public static enum Mode {
        in,
        out
    }
    public static interface Command {
        public String getName();
    }
    public static class BlinkCommand implements Command {

        Float interval;
        Float intervalOff;

        public String getName() { return "blink"; }

        public Float getInterval() {
            return interval;
        }

        public void setInterval(Float interval) {
            this.interval = interval;
        }

        public Float getIntervalOff() {
            return intervalOff;
        }

        public void setIntervalOff(Float intervalOff) {
            this.intervalOff = intervalOff;
        }
    }

    Mode mode;
    List<Command> commands;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public List<Command> getCommands() {
        return commands;
    }

    public void setCommands(List<Command> commands) {
        this.commands = commands;
    }

    public static PinConfiguration out() {
        PinConfiguration config = new PinConfiguration();
        config.mode = Mode.out;
        return config;
    }

    public static PinConfiguration blink(final Float onInterval, final Float offInterval) {
        PinConfiguration config = new PinConfiguration();
        config.mode = Mode.out;
        config.commands = new ArrayList<Command>();
        config.commands.add(new BlinkCommand() {{
            interval = onInterval;
            intervalOff = offInterval;
        }});
        return config;
    }
}
