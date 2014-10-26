package net.fortytwo.extendo.brainstem;

import android.util.Log;
import com.illposed.osc.OSCMessage;
import edu.rpi.twc.sesamestream.QueryEngine;
import net.fortytwo.extendo.p2p.ExtendoAgent;
import net.fortytwo.extendo.rdf.vocab.ExtendoGesture;
import net.fortytwo.extendo.rdf.vocab.FOAF;
import net.fortytwo.extendo.rdf.vocab.Timeline;
import net.fortytwo.rdfagents.data.DatasetFactory;
import net.fortytwo.rdfagents.model.Dataset;
import org.openrdf.model.Literal;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import org.openrdf.model.vocabulary.XMLSchema;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.Rio;
import org.openrdf.rio.ntriples.NTriplesWriter;

import java.io.ByteArrayOutputStream;
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
    public static final String QUERY_FOR_THING_POINTED_TO =
            "PREFIX gesture: <" + ExtendoGesture.NAMESPACE + ">\n" +
                    "PREFIX tl: <" + Timeline.NAMESPACE + ">\n" +
                    "PREFIX foaf: <" + FOAF.NAMESPACE + ">\n" +
                    "PREFIX rdfs: <" + RDFS.NAMESPACE + ">\n" +
                    "SELECT ?person ?pointedTo WHERE {\n" +
                    "?gesture gesture:thingPointedTo ?pointedTo .\n" +
                    "?gesture gesture:expressedBy ?person .\n" +
                    "}";
    public static final String QUERY_FOR_POINT_WITH_COMMON_ORG =
            "PREFIX gesture: <" + ExtendoGesture.NAMESPACE + ">\n" +
                    "PREFIX tl: <" + Timeline.NAMESPACE + ">\n" +
                    "PREFIX foaf: <" + FOAF.NAMESPACE + ">\n" +
                    "PREFIX rdfs: <" + RDFS.NAMESPACE + ">\n" +
                    "SELECT ?personPointedTo ?personPointedToName ?orgLabel WHERE {\n" +
                    "?gesture gesture:thingPointedTo ?personPointedTo .\n" +
                    "?personPointedTo foaf:name ?personPointedToName .\n" +
                    "?org rdfs:label ?orgLabel .\n" +
                    "?org foaf:member <http://fortytwo.net/2014/04/twc#JoshuaShinavier> .\n" +
                    "?org foaf:member ?personPointedTo .\n" +
                    "}";
    public static final String QUERY_FOR_POINT_WITH_COMMON_INTEREST =
            "PREFIX gesture: <" + ExtendoGesture.NAMESPACE + ">\n" +
                    "PREFIX tl: <" + Timeline.NAMESPACE + ">\n" +
                    "PREFIX foaf: <" + FOAF.NAMESPACE + ">\n" +
                    "PREFIX rdfs: <" + RDFS.NAMESPACE + ">\n" +
                    "SELECT ?personPointedTo ?personPointedToName ?interest WHERE {\n" +
                    "?personPointedTo foaf:name ?personPointedToName .\n" +
                    "?gesture gesture:thingPointedTo ?personPointedTo .\n" +
                    "<http://fortytwo.net/2014/04/twc#JoshuaShinavier> foaf:interest ?interest .\n" +
                    "?personPointedTo foaf:interest ?interest .\n" +
                    "}";

    private DatagramSocket oscSocket;
    private InetAddress oscAddress;
    private int oscPort;

    public BrainstemAgent(final String agentUri)
            throws QueryEngine.InvalidQueryException, IOException, QueryEngine.IncompatibleQueryException {

        super(agentUri, true);
    }

    /**
     * Creates a dataset for a pointing event
     *
     * @param timestamp      the moment at which the gesture was recognized, in milliseconds since the Unix epoch
     * @param thingPointedTo the thing which was referenced or physically pointed to by the gesture
     * @return a Dataset describing the gesture event
     */
    public Dataset datasetForPointingGesture(final long timestamp,
                                             final URI thingPointedTo) {
        return datasetForGesture(timestamp, thingPointedTo);
    }

    /**
     * Creates a dataset for a generic baton event
     *
     * @param timestamp the moment at which the gesture was recognized, in milliseconds since the Unix epoch
     * @return a Dataset describing the gesture event
     */
    public Dataset datasetForGenericBatonGesture(final long timestamp) {
        return datasetForGesture(timestamp, null);
    }

    private Dataset datasetForGesture(final long timestamp,
                                      final URI thingPointedTo) {
        Collection<Statement> c = new LinkedList<Statement>();

        URI gesture = factory.randomURI();

        if (null == thingPointedTo) {
            c.add(vf.createStatement(gesture, RDF.TYPE, ExtendoGesture.GenericBatonGesture));
        } else {
            c.add(vf.createStatement(gesture, RDF.TYPE, ExtendoGesture.Point));
            c.add(vf.createStatement(gesture, ExtendoGesture.thingPointedTo, thingPointedTo));
        }

        c.add(vf.createStatement(gesture, ExtendoGesture.expressedBy, agentUri));
        //c.add(vf.createStatement(selfUri, RDF.TYPE, FOAF.AGENT));

        URI instant = factory.randomURI();
        c.add(vf.createStatement(instant, RDF.TYPE, Timeline.Instant));
        Literal dateValue = vf.createLiteral(XSD_DATETIME_FORMAT.format(new Date(timestamp)), XMLSchema.DATETIME);
        c.add(vf.createStatement(instant, Timeline.at, dateValue));
        c.add(vf.createStatement(gesture, ExtendoGesture.recognizedAt, instant));

        return new Dataset(c);
    }

    public void sendDataset(final Dataset d) throws IOException {
        getQueryEngine().addStatements(d.getStatements());

        if (Brainstem.RELAY_OSC) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            // note: direct instantiation of a format-specific writer (as opposed to classloading via Rio)
            // makes things simpler w.r.t. Proguard's shrinking phase
            RDFWriter w = new NTriplesWriter(bos);
            //RDFWriter w = Rio.createWriter(RDFFormat.NTRIPLES, bos);
            try {
                w.startRDF();
                for (Statement s : d.getStatements()) {
                    w.handleStatement(s);
                }
                w.endRDF();
            } catch (RDFHandlerException e) {
                throw new IOException(e);
            }
            OSCMessage m = new OSCMessage("/exo/fctr/tt/rdf");
            m.addArgument(new String(bos.toByteArray()));
            sendOSCMessageToFacilitator(m);
        }
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
