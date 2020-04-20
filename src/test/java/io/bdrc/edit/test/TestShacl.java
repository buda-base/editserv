package io.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.engine.Targets;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.shacl.validation.ReportEntry;

import io.bdrc.edit.EditConfig;
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
    }

    public void checkShapesGraph() throws IOException, ParameterFormatException {
        Shapes sh = Shapes.parseAll(CommonsRead.getAllShapesForType("bdo:Person").getGraph());
    }

    public Shapes checkShapesGraph(Model m) throws IOException, ParameterFormatException {
        Shapes sh = Shapes.parseAll(m.getGraph());
        return sh;
    }

    public static void main(String[] args) throws IOException, ParameterFormatException {
        EditConfig.init();
        TestShacl ts = new TestShacl();
        // ts.checkShapesGraph();
        Model m = ModelFactory.createDefaultModel();
        m.read(personShapeUri, "TURTLE");
        Shapes sh = ts.checkShapesGraph(m);
        Collection<Shape> cs = sh.getRootShapes();
        // System.out.println(cs.size());
        Iterator<Shape> it = cs.iterator();
        for (Shape s : cs) {
            // System.out.println(s.getClass().getCanonicalName());
        }
        Targets tg = sh.getTargets();
        Set<Node> sn = tg.allTargets;
        for (Object n : sn) {
            // System.out.println(n);
        }
        Map<Node, Shape> map = sh.getShapeMap();
        Set<Entry<Node, Shape>> ent = map.entrySet();
        for (Entry<Node, Shape> e : ent) {
            // System.out.println(e);
        }

        ShaclValidator valid = ShaclValidator.get();
        ValidationReport vr = valid.validate(sh, testMod.getGraph());
        System.out.println("IS conform :" + vr.conforms());
        Collection<ReportEntry> re = vr.getEntries();
        for (ReportEntry r : re) {
            // System.out.println("Msg >> " + r.message());
            System.out.println("Msg >> " + r.toString());
        }
    }

}
