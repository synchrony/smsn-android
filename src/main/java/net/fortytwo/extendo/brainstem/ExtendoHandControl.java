package net.fortytwo.extendo.brainstem;

import com.illposed.osc.OSCMessage;
import net.fortytwo.extendo.p2p.ExtendoAgent;
import net.fortytwo.extendo.p2p.SideEffects;
import net.fortytwo.extendo.p2p.osc.OscControl;
import net.fortytwo.extendo.p2p.osc.OscMessageHandler;
import net.fortytwo.extendo.p2p.osc.OscReceiver;
import net.fortytwo.extendo.rdf.Gesture;
import net.fortytwo.rdfagents.model.Dataset;

import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A controller for the Extend-o-Hand gestural glove
 *
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class ExtendoHandControl extends OscControl {

    private static final Logger logger = Logger.getLogger(ExtendoHandControl.class.getName());

    public ExtendoHandControl(final OscReceiver oscDispatcher,
                              final ExtendoAgent agent,
                              final SideEffects sideEffects) {
        super(oscDispatcher);

        oscDispatcher.register("/exo/hand/ping-reply", new OscMessageHandler() {
            public void handle(final OSCMessage message) {
                long delay = System.currentTimeMillis() - timeOfLastEvent;
                sideEffects.setStatus("bluetooth delay: " + delay + "ms");
            }
        });

        oscDispatcher.register("/exo/hand/raw", new OscMessageHandler() {
            public void handle(final OSCMessage message) {
                timeOfLastEvent = System.currentTimeMillis();

                // TODO: the recognition instant should be inferred from the timestamp supplied by the device
                Date recognizedAt = new Date();

                if (sideEffects.verbose()) {
                    List<Object> args = message.getArguments();
                    if (5 == args.size()) {
                        sideEffects.setStatus("Extend-o-Hand raw gesture: "
                                + args.get(0) + " " + args.get(1) + " " + args.get(2)
                                + " " + args.get(3) + " " + args.get(4));
                    } else {
                        sideEffects.setStatus("Extend-o-Hand error (wrong # of args)");
                    }
                }

                // TODO: restore the event stack proxy, but using SLIP+OSC rather than HTTP
                /*
                if (null != proxy) {
                    proxy.push(
                        brain.getEventStack().createGestureEvent(agent.getAgentUri().stringValue(), recognizedAt));
                }*/

                Dataset d = Gesture.datasetForGenericBatonGesture(recognizedAt.getTime(), agent.getAgentUri());
                try {
                    agent.getQueryEngine().addStatements(d.getStatements());
                    //agent.broadcastDataset(d);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "failed to broadcast RDF dataset", e);
                }
            }
        });
    }
}
