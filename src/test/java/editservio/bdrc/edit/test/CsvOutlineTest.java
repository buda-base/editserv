package editservio.bdrc.edit.test;

import java.io.IOException;
import java.util.List;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.StreamRDFLib;
import org.junit.BeforeClass;
import org.junit.Test;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.helpers.SimpleOutline;

public class CsvOutlineTest {
    
    final static String TESTDIR = "src/test/resources/";
    
    @BeforeClass
    public static void init() throws Exception {
        EditConfig.initForTests(null);
        
    }

    @Test
    public void tocsv() throws IOException {
        Model outline = ModelFactory.createDefaultModel();
        Graph g = outline.getGraph();
        RDFParser.create()
            .source(TESTDIR+"O1GS118327.ttl")
            .lang(RDFLanguages.TTL)
            .parse(StreamRDFLib.graph(g));
        Resource root = outline.createResource(EditConstants.BDR + "MW01CT0060");
        SimpleOutline so = new SimpleOutline(root);
        List<String[]> rows = so.asCsv();
        System.out.println(rows);
    }
    
}
