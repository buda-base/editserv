package editservio.bdrc.edit.test;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
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

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.helpers.SimpleOutline;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class CsvOutlineTest {
    
    final static String TESTDIR = "src/test/resources/";
    
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
    
}
