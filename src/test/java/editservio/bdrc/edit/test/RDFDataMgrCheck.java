package editservio.bdrc.edit.test;

import java.io.StringReader;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

public class RDFDataMgrCheck {

    public static void Check() {
        String test = "@base          <bdg:T1642> .\n" + "@prefix :      <http://purl.bdrc.io/ontology/core/> .\n" + "@prefix adm:   <http://purl.bdrc.io/ontology/admin/> .\n" + "@prefix bda:   <http://purl.bdrc.io/admindata/> .\n"
                + "@prefix bdg:   <http://purl.bdrc.io/graph/> .\n" + "@prefix bdr:   <http://purl.bdrc.io/resource/> .\n" + "@prefix owl:   <http://www.w3.org/2002/07/owl#> .\n" + "@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n"
                + "@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .\n" + "@prefix skos:  <http://www.w3.org/2004/02/skos/core#> .\n" + "@prefix vcard: <http://www.w3.org/2006/vcard/ns#> .\n"
                + "@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .\n" + "\n" + "bdg:T1642 {\n" + "    bda:T1642  a            adm:AdminData ;\n" + "        :isRoot             true ;\n" + "        adm:adminAbout      bdr:T1642 ;\n"
                + "        adm:facetIndex      2 ;\n" + "        adm:gitPath         \"00/T1642.trig\" ;\n" + "        adm:gitRepo         bda:GR0007 ;\n" + "        adm:graphId         bdg:T1642 ;\n"
                + "        adm:metadataLegal   bda:LD_BDRC_CC0 ;\n" + "        adm:status          bda:StatusReleased .\n" + "    \n" + "    bdr:NTF91434BA90B2\n" + "        a                   :Note ;\n"
                + "        :noteText           \"the three black deities (nag po skor gsum) of the padma gling pa gter ma are:\\nthe nag po skor gsum are:\\ngshin rje ka thun\\nmu stegs gu lang\\nphra men phag sha\"@en .\n" + "    \n"
                + "    bdr:T1642  a            :Topic ;\n" + "        skos:prefLabel      \"nag po skor gsum ( pad+ma gling pa)\"@bo-x-ewts ;\n" + "        :isRoot             true ;\n" + "        :note               bdr:NTF91434BA90B2 .\n" + "}\n"
                + "";
        Graph g = ModelFactory.createDefaultModel().getGraph();
        RDFDataMgr.read(g, new StringReader(test), "", Lang.TRIG);
        Model m = ModelFactory.createModelForGraph(g);
        m.write(System.out);
    }

    public static void main(String[] args) {
        RDFDataMgrCheck.Check();
    }

}
