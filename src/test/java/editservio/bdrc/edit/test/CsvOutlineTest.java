package editservio.bdrc.edit.test;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.StreamRDFLib;
import org.hamcrest.MatcherAssert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.helpers.SimpleOutline;

import static org.hamcrest.CoreMatchers.*;

public class CsvOutlineTest {
    
    final static String TESTDIR = "src/test/resources/";
    
    ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
    
    @BeforeClass
    public static void init() throws Exception {
        EditConfig.initForTests(null);   
    }
    
    public void debug_csv(final String fname, final List<String[]> rows) {
        try (CSVWriter csvWriter = new CSVWriter(new FileWriter(new File(fname)))) {
            csvWriter.writeAll(rows);
        } catch (IOException e) {
            System.err.println("error writing csv");
        }
    }

    @Test
    public void tocsv() throws IOException, CsvException {
        Model outline = ModelFactory.createDefaultModel();
        Graph g = outline.getGraph();
        RDFParser.create()
            .source(TESTDIR+"O1GS118327.ttl")
            .lang(RDFLanguages.TTL)
            .parse(StreamRDFLib.graph(g));
        final Resource root = outline.createResource(EditConstants.BDR + "MW01CT0060");
        final Resource w = outline.createResource(EditConstants.BDR + "W01CT0060");
        final SimpleOutline so = new SimpleOutline(root, w);
        final List<String[]> rows = so.asCsv();
        //debug_csv("/tmp/test.csv", rows);
        final CSVReader reader = new CSVReader(new FileReader(TESTDIR+"O1GS118327-W01CT0060.csv"));
        final List<String[]> fromFile = reader.readAll();
        assert(fromFile.size() == rows.size());
        for (int i = 0 ; i < fromFile.size() ; i++) {
            MatcherAssert.assertThat(fromFile.get(i), is(rows.get(i)));
        }
    }
    
    public static void debug_diff(final Model m1, final Model m2) {
        System.out.println("in m1 not m2\n");
        System.out.println(m1.difference(m2).write(System.out, "TTL"));
        System.out.println("\n\nin m2 not m1\n\n");
        System.out.println(m2.difference(m1).write(System.out, "TTL"));
    }
    
    @Test
    public void fromCsvIdentical() throws IOException, CsvException {
        // read csv and reinsert it in the original model. We expect the initial and final
        // model to be the same
        Model outline = ModelFactory.createDefaultModel();
        Graph g = outline.getGraph();
        RDFParser.create()
            .source(TESTDIR+"O1GS118327.ttl")
            .lang(RDFLanguages.TTL)
            .parse(StreamRDFLib.graph(g));
        final Resource root = outline.createResource(EditConstants.BDR + "MW01CT0060");
        final Resource w = outline.createResource(EditConstants.BDR + "W01CT0060");
        final Resource o = outline.createResource(EditConstants.BDR + "O1GS118327");
        final CSVReader reader = new CSVReader(new FileReader(TESTDIR+"O1GS118327-W01CT0060.csv"));
        final List<String[]> fromFile = reader.readAll();
        final SimpleOutline so = new SimpleOutline(fromFile, o, root, w);
        //System.out.println(ow.writeValueAsString(so));
        final Model result = ModelFactory.createDefaultModel();
        result.add(outline);
        so.insertInModel(result, root, w);
        //result.write(System.out, "TTL");
        //debug_diff(outline, result);
        assert(result.isIsomorphicWith(outline));
    }
    
}
