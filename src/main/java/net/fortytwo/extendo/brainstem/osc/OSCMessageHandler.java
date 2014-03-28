package net.fortytwo.extendo.brainstem.osc;

import com.illposed.osc.OSCMessage;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public interface OSCMessageHandler {
    void handle(OSCMessage message);
}
