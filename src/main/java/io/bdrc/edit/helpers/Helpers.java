package io.bdrc.edit.helpers;

import java.io.StringReader;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

public class Helpers {

    public static String getResourceType(String resId, EditPatchHeaders ph) {
        return ph.getResourceType(resId);
    }

    public static AdminData fetchAdminInfo(String graphUri, EditPatchHeaders ph) {
        String resId = graphUri.substring(graphUri.lastIndexOf("/") + 1);
        AdminData ad = new AdminData(resId, getResourceType(graphUri, ph));
        return ad;
    }

    public static Graph buildGraphFromTrig(String data) {
        Dataset ds = DatasetFactory.create();
        RDFDataMgr.read(ds, new StringReader(data), "", Lang.TRIG);
        return ds.asDatasetGraph().getUnionGraph();
    }

}
