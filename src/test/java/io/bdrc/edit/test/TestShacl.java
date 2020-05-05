package io.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.engine.Targets;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.shacl.validation.ReportEntry;
import org.apache.jena.shacl.validation.ShaclSimpleValidator;
import org.topbraid.shacl.engine.ShapesGraph;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.validation.ValidationEngineFactory;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.CommonsRead;
import io.bdrc.edit.txn.exceptions.ParameterFormatException;

public class TestShacl {

    static String personShapeUri = "https://raw.githubusercontent.com/buda-base/editor-templates/master/templates/core/person.shapes.ttl";
    static Model testMod;

    public TestShacl() {
        // TODO Auto-generated constructor stub
        InputStream in = TestShacl.class.getClassLoader().getResourceAsStream("P707.ttl");
        testMod = ModelFactory.createDefaultModel();
        testMod.read(in, null, "TTL");
        // testMod.write(System.out, "TURTLE");

        // testMod =
        // QueryProcessor.getGraphWithAssoiatedResourcesType("http://purl.bdrc.io/graph/P707");
    }

    public void checkShapesGraph() throws IOException, ParameterFormatException {
        Shapes sh = Shapes.parseAll(CommonsRead.getShapesForType("bdo:Person").getGraph());
    }

    public static Shapes checkShapesGraph(Model m) throws IOException, ParameterFormatException {
        Shapes sh = Shapes.parseAll(m.getGraph());
        return sh;
    }

    public static void whithJenaValidator() throws IOException, ParameterFormatException {
        TestShacl ts = new TestShacl();
        Model m = ModelFactory.createDefaultModel();
        m.read(personShapeUri, null, "TTL");
        Shapes sh = ts.checkShapesGraph(m);
        Collection<Shape> cs = sh.getRootShapes();
        // System.out.println(cs.size());
        Iterator<Shape> it = cs.iterator();
        for (Shape s : cs) {
            System.out.println(s.getClass().getCanonicalName());
        }
        Targets tg = sh.getTargets();
        Set<Node> sn = tg.allTargets;
        for (Object n : sn) {
            System.out.println(n);
        }
        Map<Node, Shape> map = sh.getShapeMap();
        Set<Entry<Node, Shape>> ent = map.entrySet();
        for (Entry<Node, Shape> e : ent) {
            System.out.println(e);
        }

        ShaclValidator valid = ShaclValidator.get();
        ValidationReport vr = valid.validate(sh, testMod.getGraph());
        System.out.println("IS conform :" + vr.conforms());
        Collection<ReportEntry> re = vr.getEntries();
        for (ReportEntry r : re) {
            System.out.println("Msg >> " + r.message());
            System.out.println("Msg >> " + r.toString());
        }
    }

    public static void main(String[] args) throws IOException, ParameterFormatException, URISyntaxException, InterruptedException {
        EditConfig.init();
        TestShacl ts = new TestShacl();
        Model m = ModelFactory.createDefaultModel();
        m.read(personShapeUri, null, "TTL");

        Dataset ds = DatasetFactory.create(testMod);
        Resource shapesGraphNm = ResourceFactory.createResource(EditConstants.BDG + "PersonShapes");
        ds.asDatasetGraph().addGraph(shapesGraphNm.asNode(), m.getGraph());
        URI shapesGraphUri = new URI(EditConstants.BDG + "PersonShapes");

        System.out.println(" model size for shapes>>" + m.size());
        ShapesGraph sg = new ShapesGraph(m);
        System.out.println("Shapes graph root shapes >>" + sg.getRootShapes());
        System.out.println("Shapes graph root Model >>" + sg.getShapesModel().size());
        ValidationEngine ve = ValidationEngineFactory.get().create(DatasetFactory.create(testMod), new URI(EditConstants.BDG + "PersonShapes"), sg,
                null);
        System.out.println("Validation Engine config validateShapes >>" + ve.getConfiguration().getValidateShapes());
        System.out.println("Validation Engine config validateShapes >>" + ve.getConfiguration().setReportDetails(true));
        System.out.println("Validation Engine config ShapesGraphUri >>" + ve.getShapesGraphURI());
        // ve.validateAll();
        ve.validateNode(ResourceFactory.createResource(EditConstants.BDR + "P707").asNode());
        System.out.println("Shapes graph URI >>" + ve.getShapesGraphURI());
        System.out.println("Shapes model >>" + ve.getShapesModel().size());
        System.out.println(ve.getValidationReport().conforms());
        System.out.println(ve.getValidationReport().results());
        // whithJenaValidator();
        System.out.println("****************JENA SIMPLE VALIDATOR********************************");
        ShaclSimpleValidator ssv = new ShaclSimpleValidator();
        boolean conforms = ssv.conforms(m.getGraph(), testMod.getGraph());
        System.out.println("ShaclSimpleValidator conforms all>>" + conforms);
        boolean conformsNode = ssv.conforms(checkShapesGraph(m), testMod.getGraph(),
                ResourceFactory.createResource(EditConstants.BDR + "P707").asNode());
        System.out.println("ShaclSimpleValidator conforms Node>>" + conformsNode);
        ValidationReport vr = ssv.validate(checkShapesGraph(m), testMod.getGraph(),
                ResourceFactory.createResource(EditConstants.BDR + "P707").asNode());
        System.out.println("ValidationReport >>" + vr.getEntries());

    }

}
