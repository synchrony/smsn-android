package net.fortytwo.extendo.brainstem;

import net.fortytwo.extendo.typeatron.ripple.Environment;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class BrainstemEnvironment implements Environment {
    private final Brainstem brainstem;

    public BrainstemEnvironment(final Brainstem brainstem) {
        this.brainstem = brainstem;
    }

    @Override
    public void speak(final String message) {
        brainstem.getSpeaker().speak(message);
    }

    @Override
    public boolean verbose() {
        return Brainstem.RELAY_OSC;
    }
}
