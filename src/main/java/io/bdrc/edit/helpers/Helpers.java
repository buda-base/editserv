package io.bdrc.edit.helpers;

import java.io.File;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Helpers {

    public final static Logger log = LoggerFactory.getLogger(Helpers.class.getName());

    public static String getResourceType(String resId, EditPatchHeaders ph) {
        return ph.getResourceType(resId);
    }

    public static AdminData fetchAdminInfo(String graphUri, EditPatchHeaders ph) throws NoSuchAlgorithmException {
        String resId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        AdminData ad = new AdminData(resId, getResourceType(graphUri, ph));
        return ad;
    }

    public static DatasetGraph buildGraphFromTrig(String data) {
        Dataset ds = DatasetFactory.create();
        RDFDataMgr.read(ds, new StringReader(data), "", Lang.TRIG);
        return ds.asDatasetGraph();
    }

    public static void putModelWithInference(RDFConnectionFuseki fusConn, String graph, Model m, Reasoner reasoner) {
        fusConn.begin(ReadWrite.WRITE);
        Model mi = ModelFactory.createInfModel(reasoner, m);
        fusConn.put(graph, ModelFactory.createInfModel(reasoner, mi));
        fusConn.commit();
        fusConn.end();
    }

    public static void putModel(RDFConnectionFuseki fusConn, String graph, Model m) {
        fusConn.begin(ReadWrite.WRITE);
        fusConn.put(graph, m);
        fusConn.commit();
        fusConn.end();
    }

    public static void createDirIfNotExists(String dir) {
        File theDir = new File(dir);
        if (!theDir.exists()) {
            try {
                theDir.mkdir();
            } catch (SecurityException se) {
                log.error("Could not create " + dir, se);
            }
        }
    }

}
