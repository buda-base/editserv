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
    public static final String BF = "http://id.loc.gov/ontologies/bibframe/";
    public static final String BDR = "http://purl.bdrc.io/resource/";
    public static final String ADM = "http://purl.bdrc.io/ontology/admin/";
    public static final String SH  = "http://www.w3.org/ns/shacl#";
    public static final String BDA = "http://purl.bdrc.io/admindata/";
    public static final String BDG = "http://purl.bdrc.io/graph/";
    public static final String BDS = "http://purl.bdrc.io/ontology/shapes/core/";
    public static final String RDE = "https://github.com/buda-base/rdf-document-editor/";
    public static final String OWL = "http://www.w3.org/2002/07/owl#";
    public static final String BDU = "http://purl.bdrc.io/resource-nc/user/";
    public static final String BDOU = "http://purl.bdrc.io/ontology/ext/user/";
    public static final String BDGU = "http://purl.bdrc.io/graph-nc/user/";
    public static final String BDGUP = "http://purl.bdrc.io/graph-nc/user-private/";
    public static final String ADR = "http://purl.bdrc.io/resource-nc/auth/";
    public static final String FOAF = "http://xmlns.com/foaf/0.1/";

    public static final Resource ADMIN_DATA = ResourceFactory.createResource("http://purl.bdrc.io/ontology/admin/AdminData");
    public static final Resource STATUS_PROV = ResourceFactory.createResource("http://purl.bdrc.io/admindata/StatusProvisional");
    public static final Property GIT_REPO = ResourceFactory.createProperty(EditConstants.ADM + "gitRepo");
    public static final Property GIT_PATH = ResourceFactory.createProperty(EditConstants.ADM + "gitPath");
    public static final Property GIT_REVISION = ResourceFactory.createProperty(EditConstants.ADM + "gitRevision");
    public static final Property ADMIN_ABOUT = ResourceFactory.createProperty(EditConstants.ADM + "adminAbout");
    public static final Property ADMIN_STATUS = ResourceFactory.createProperty(EditConstants.ADM + "status");
    public static final Resource ADMIN_STATUS_RELEASED = ResourceFactory.createResource(EditConstants.BDA + "StatusReleased");
    public static final Property ADMIN_GRAPH_ID = ResourceFactory.createProperty(EditConstants.ADM + "graphId");
    public static final Property SH_PROPERTY = ResourceFactory.createProperty(EditConstants.SH + "property");
    public static final Property SH_PATH = ResourceFactory.createProperty(EditConstants.SH + "path");
    public static final Property SH_NODE = ResourceFactory.createProperty(EditConstants.SH + "node");
    public static final Property SH_INVERSE_PATH = ResourceFactory.createProperty(EditConstants.SH + "inversePath");
    public static final Property PROPERTY_SHAPE_TYPE = ResourceFactory.createProperty(EditConstants.RDE + "propertyShapeType");
    public static final Resource INTERNAL_SHAPE = ResourceFactory.createResource(EditConstants.RDE + "InternalShape");
    public static final Property SH_TARGETOBJECTSOF = ResourceFactory.createProperty(EditConstants.SH + "targetObjectsOf");
    public static final Property SH_TARGETSUBJECTSOF = ResourceFactory.createProperty(EditConstants.SH + "targetSubjectsOf");
    
    public static final Resource TEST_USER = ResourceFactory.createProperty(EditConstants.BDU + "U0TEST");

    public static Property INVERSE_OF = ResourceFactory.createProperty(EditConstants.OWL + "inverseOf");
    public static Resource SymmetricPROP = ResourceFactory.createResource(EditConstants.OWL + "SymmetricProperty");

}
