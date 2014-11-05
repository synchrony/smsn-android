package net.fortytwo.extendo.brainstem.devices;

import android.util.Log;
import com.illposed.osc.OSCMessage;
import net.fortytwo.extendo.brain.ExtendoBrain;
import net.fortytwo.extendo.brainstem.Brainstem;
import net.fortytwo.extendo.brainstem.EventStackProxy;
import net.fortytwo.extendo.p2p.ExtendoAgent;
import net.fortytwo.extendo.p2p.osc.OSCDispatcher;
import net.fortytwo.extendo.p2p.osc.OSCMessageHandler;
import net.fortytwo.extendo.p2p.osc.SlipOscControl;
import net.fortytwo.extendo.rdf.Gesture;
import net.fortytwo.rdfagents.model.Dataset;

import java.util.Date;
import java.util.List;

/**
 * A controller for the Extend-o-Hand gestural glove
 *
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class ExtendoHandControl extends SlipOscControl {

    public ExtendoHandControl(final OSCDispatcher oscDispatcher,
                              final ExtendoBrain brain,
                              final EventStackProxy proxy,
                              final ExtendoAgent agent,
                              final Brainstem brainstem) {
        super(oscDispatcher);

        oscDispatcher.register("/exo/hand/ping-reply", new OSCMessageHandler() {
            public void handle(final OSCMessage message) {
                long delay = System.currentTimeMillis() - timeOfLastEvent;
                brainstem.getToaster().makeText("bluetooth delay: " + delay + "ms");
            }
        });

        oscDispatcher.register("/exo/hand/raw", new OSCMessageHandler() {
            public void handle(final OSCMessage message) {
                timeOfLastEvent = System.currentTimeMillis();

                // TODO: the recognition instant should be inferred from the timestamp supplied by the device
                Date recognizedAt = new Date();

                if (Brainstem.VERBOSE) {
                    List<Object> args = message.getArguments();
                    if (5 == args.size()) {
                        brainstem.getTexter().setText("Extend-o-Hand raw gesture: "
                                + args.get(0) + " " + args.get(1) + " " + args.get(2)
                                + " " + args.get(3) + " " + args.get(4));
                    } else {
                        brainstem.getTexter().setText("Extend-o-Hand error (wrong # of args)");
                    }
                }

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
                    Log.e(Brainstem.TAG, "failed to broadcast RDF dataset: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        });
    }
}
