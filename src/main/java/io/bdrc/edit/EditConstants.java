package io.bdrc.edit;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public class EditConstants {

    public static final int PATCH_SVC_QUEUED = 0;
    public static final int PATCH_SVC_PROCESSING = 1;
    public static final int PATCH_SVC_ACCEPTED = 2;
    public static final int PATCH_SCV_REJECTED = 3;

    public static final String PTC_EXT = ".ptc";

    public static final String BDO = "http://purl.bdrc.io/ontology/core/";
    public static final String BDR = "http://purl.bdrc.io/resource/";
    public static final String ADM = "http://purl.bdrc.io/ontology/admin/";
    public static final String BDA = "http://purl.bdrc.io/admindata/";
    public static final String BDG = "http://purl.bdrc.io/graph/";

    public static Resource ADMIN_DATA = ResourceFactory.createResource("http://purl.bdrc.io/ontology/admin/AdminData");
    public static Resource STATUS_PROV = ResourceFactory.createResource("http://purl.bdrc.io/admindata/StatusProvisional");
    public static Property GIT_REPO = ResourceFactory.createProperty(EditConstants.ADM + "gitRepo");
    public static Property GIT_PATH = ResourceFactory.createProperty(EditConstants.ADM + "gitPath");
    public static Property GIT_REVISION = ResourceFactory.createProperty(EditConstants.ADM + "gitRevision");
    public static Property ADMIN_ABOUT = ResourceFactory.createProperty(EditConstants.ADM + "adminAbout");
    public static Property ADMIN_STATUS = ResourceFactory.createProperty(EditConstants.ADM + "status");
    public static Property ADMIN_GRAPH_ID = ResourceFactory.createProperty(EditConstants.ADM + "graphId");

}
