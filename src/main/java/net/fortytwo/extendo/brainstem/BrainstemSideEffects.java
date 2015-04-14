package net.fortytwo.extendo.brainstem;

import net.fortytwo.extendo.p2p.SideEffects;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class BrainstemSideEffects implements SideEffects {
    private final Brainstem brainstem;

    public BrainstemSideEffects(final Brainstem brainstem) {
        this.brainstem = brainstem;
    }

    @Override
    public void speak(final String message) {
        brainstem.getSpeaker().speak(message);
    }

    @Override
    public void setStatus(String message) {
        brainstem.getTexter().setText(message);
    }
}
