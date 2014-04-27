package net.fortytwo.extendo.brainstem;

import android.util.Log;
import com.illposed.osc.OSCMessage;
import edu.rpi.twc.sesamestream.QueryEngine;
import net.fortytwo.extendo.p2p.ExtendoAgent;
import net.fortytwo.extendo.rdf.vocab.ExtendoGesture;
import net.fortytwo.extendo.rdf.vocab.Timeline;
import net.fortytwo.rdfagents.model.Dataset;
import org.openrdf.model.Literal;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.XMLSchema;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class BrainstemAgent extends ExtendoAgent {

    private static final SimpleDateFormat XSD_DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    // TODO: temporary debugging/development item
    public long timeOfLastEvent;

    public static final String QUERY_FOR_ALL_GB_GESTURES =
            "PREFIX gesture: <" + ExtendoGesture.NAMESPACE + ">\n" +
                    "PREFIX tl: <" + Timeline.NAMESPACE + ">\n" +
                    "SELECT ?person ?time WHERE {\n" +
                    "?gesture a gesture:GenericBatonGesture .\n" +
                    "?gesture gesture:expressedBy ?person .\n" +
                    "?gesture gesture:recognizedAt ?instant .\n" +
                    "?instant tl:at ?time .\n" +
                    "}";

    private DatagramSocket oscSocket;
    private InetAddress oscAddress;
    private int oscPort;

    public BrainstemAgent(final String agentUri) throws QueryEngine.InvalidQueryException, IOException, QueryEngine.IncompatibleQueryException {
        super(agentUri, true);
    }

    /**
     * @param timestamp the moment at which the gesture was recognized, in milliseconds since the Unix epoch
     * @return a Dataset describing the gesture event
     */
    public Dataset datasetForGestureEvent(final long timestamp) {
        Collection<Statement> c = new LinkedList<Statement>();

        URI gesture = factory.randomURI();
        c.add(vf.createStatement(gesture, RDF.TYPE, ExtendoGesture.GenericBatonGesture));

        c.add(vf.createStatement(gesture, ExtendoGesture.expressedBy, agentUri));
        //c.add(vf.createStatement(selfUri, RDF.TYPE, FOAF.AGENT));

        URI instant = factory.randomURI();
        c.add(vf.createStatement(instant, RDF.TYPE, Timeline.Instant));
        Literal dateValue = vf.createLiteral(XSD_DATETIME_FORMAT.format(new Date(timestamp)), XMLSchema.DATETIME);
        c.add(vf.createStatement(instant, Timeline.at, dateValue));
        c.add(vf.createStatement(gesture, ExtendoGesture.recognizedAt, instant));

        return new Dataset(c);
    }

    public void sendOSCMessageToFacilitator(final OSCMessage m) {
        if (getFacilitatorConnection().isActive()) {
            try {
                if (null == oscSocket) {
                    oscPort = getFacilitatorService().description.getOscPort();
                    oscAddress = getFacilitatorService().address;

                    oscSocket = new DatagramSocket();
                }

                byte[] buffer = m.getByteArray();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, oscAddress, oscPort);
                oscSocket.send(packet);

                Log.i(Brainstem.TAG, "sent OSC datagram to " + oscAddress + ":" + oscPort);
            } catch (IOException e) {
                Log.e(Brainstem.TAG, "error in sending OSC datagram to facilitator: " + e.getMessage());
                e.printStackTrace(System.err);
            } catch (Throwable t) {
                Log.e(Brainstem.TAG, "unexpected error in sending OSC datagram to facilitator: " + t.getMessage());
                t.printStackTrace(System.err);
            }
        }
    }
}
