package editservio.bdrc.edit.test;

import java.io.StringReader;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

public class RDFDataMgrCheck {

    public static void CheckGraph() {
        String testTrig = "@base          <bdg:T1642> .\n" + "@prefix :      <http://purl.bdrc.io/ontology/core/> .\n" + "@prefix adm:   <http://purl.bdrc.io/ontology/admin/> .\n" + "@prefix bda:   <http://purl.bdrc.io/admindata/> .\n"
                + "@prefix bdg:   <http://purl.bdrc.io/graph/> .\n" + "@prefix bdr:   <http://purl.bdrc.io/resource/> .\n" + "@prefix owl:   <http://www.w3.org/2002/07/owl#> .\n" + "@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n"
                + "@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .\n" + "@prefix skos:  <http://www.w3.org/2004/02/skos/core#> .\n" + "@prefix vcard: <http://www.w3.org/2006/vcard/ns#> .\n"
                + "@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .\n" + "\n" + "bdg:T1642 {\n" + "    bda:T1642  a            adm:AdminData ;\n" + "        :isRoot             true ;\n" + "        adm:adminAbout      bdr:T1642 ;\n"
                + "        adm:facetIndex      2 ;\n" + "        adm:gitPath         \"00/T1642.trig\" ;\n" + "        adm:gitRepo         bda:GR0007 ;\n" + "        adm:graphId         bdg:T1642 ;\n"
                + "        adm:metadataLegal   bda:LD_BDRC_CC0 ;\n" + "        adm:status          bda:StatusReleased .\n" + "    \n" + "    bdr:NTF91434BA90B2\n" + "        a                   :Note ;\n"
                + "        :noteText           \"the three black deities (nag po skor gsum) of the padma gling pa gter ma are:\\nthe nag po skor gsum are:\\ngshin rje ka thun\\nmu stegs gu lang\\nphra men phag sha\"@en .\n" + "    \n"
                + "    bdr:T1642  a            :Topic ;\n" + "        skos:prefLabel      \"nag po skor gsum ( pad+ma gling pa)\"@bo-x-ewts ;\n" + "        :isRoot             true ;\n" + "        :note               bdr:NTF91434BA90B2 .\n" + "}\n"
                + "";
        Dataset ds = DatasetFactory.create();
        RDFDataMgr.read(ds, new StringReader(testTrig), "", Lang.TRIG);
        ds.getUnionModel().write(System.out, Lang.TRIG.getLabel());
    }

    public static void CheckModel() {
        String testTrig = "@base          <bdg:G1741> .\n" + "@prefix :      <http://purl.bdrc.io/ontology/core/> .\n" + "@prefix adm:   <http://purl.bdrc.io/ontology/admin/> .\n" + "@prefix bda:   <http://purl.bdrc.io/admindata/> .\n"
                + "@prefix bdg:   <http://purl.bdrc.io/graph/> .\n" + "@prefix bdr:   <http://purl.bdrc.io/resource/> .\n" + "@prefix owl:   <http://www.w3.org/2002/07/owl#> .\n" + "@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n"
                + "@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .\n" + "@prefix skos:  <http://www.w3.org/2004/02/skos/core#> .\n" + "@prefix vcard: <http://www.w3.org/2006/vcard/ns#> .\n"
                + "@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .\n" + "\n" + "bdg:G1741 {\n" + "    bda:G1741  a            adm:AdminData ;\n" + "        :isRoot             true ;\n" + "        adm:adminAbout      bdr:G1741 ;\n"
                + "        adm:facetIndex      4 ;\n" + "        adm:gitPath         \"09/G1741.trig\" ;\n" + "        adm:gitRepo         bda:GR0005 ;\n" + "        adm:graphId         bdg:G1741 ;\n"
                + "        adm:logEntry        bda:LG4727DB5B1BB2 , bda:LGE6331A2007CA ;\n" + "        adm:metadataLegal   bda:LD_BDRC_CC0 ;\n" + "        adm:status          bda:StatusReleased .\n" + "    \n" + "    bda:LG4727DB5B1BB2\n"
                + "        a                   adm:LogEntry ;\n" + "        adm:logDate         \"2013-07-12T12:55:01.567Z\"^^xsd:dateTime ;\n" + "        adm:logMessage      \"added location\"@en ;\n" + "        adm:logWho          bdr:U00007 .\n"
                + "    \n" + "    bda:LGE6331A2007CA\n" + "        a                   adm:LogEntry ;\n" + "        adm:logDate         \"2012-12-11T17:11:51.984Z\"^^xsd:dateTime ;\n"
                + "        adm:logMessage      \"type changed from monastery to dgonPa\"@en ;\n" + "        adm:logWho          bdr:U00020 .\n" + "    \n" + "    bdr:G1741  a            :Place ;\n"
                + "        skos:altLabel       \"mgo log 'bos pa dgon/\"@bo-x-ewts ;\n" + "        skos:prefLabel      \"'bos pa dgon/\"@bo-x-ewts ;\n" + "        :isRoot             true ;\n" + "        :note               bdr:NT9BED02606C5E ;\n"
                + "        :placeLocatedIn     bdr:G1752 , bdr:G1787 ;\n" + "        :placeType          bdr:PT0037 .\n" + "    \n" + "    bdr:NT9BED02606C5E\n" + "        a                   :Note ;\n"
                + "        :noteLocationStatement  \"p. 191\" ;\n" + "        :noteWork           bdr:W20140 .\n" + "}";

        Model m = ModelFactory.createDefaultModel();
        m.read(new StringReader(testTrig), "", Lang.TRIG.getLabel());
        m.write(System.out, Lang.TRIG.getLabel());
    }

    public static void CheckGraphTTL() {
        String testTTL = "@base          <file:///usr/local/ldspdi/> .\n" + "@prefix :      <http://purl.bdrc.io/ontology/core/> .\n" + "@prefix adm:   <http://purl.bdrc.io/ontology/admin/> .\n"
                + "@prefix adr:   <http://purl.bdrc.io/resource-nc/auth/> .\n" + "@prefix as:    <http://www.w3.org/ns/activitystreams#> .\n" + "@prefix aut:   <http://purl.bdrc.io/ontology/ext/auth/> .\n"
                + "@prefix bda:   <http://purl.bdrc.io/admindata/> .\n" + "@prefix bdac:  <http://purl.bdrc.io/anncollection/> .\n" + "@prefix bdan:  <http://purl.bdrc.io/annotation/> .\n" + "@prefix bdg:   <http://purl.bdrc.io/graph/> .\n"
                + "@prefix bdgu:  <http://purl.bdrc.io/graph-nc/user/> .\n" + "@prefix bdgup: <http://purl.bdrc.io/graph-nc/user-private/> .\n" + "@prefix bdo:   <http://purl.bdrc.io/ontology/core/> .\n"
                + "@prefix bdou:  <http://purl.bdrc.io/ontology/ext/user/> .\n" + "@prefix bdr:   <http://purl.bdrc.io/resource/> .\n" + "@prefix bdu:   <http://purl.bdrc.io/resource-nc/user/> .\n"
                + "@prefix bf:    <http://id.loc.gov/ontologies/bibframe/> .\n" + "@prefix dcterms: <http://purl.org/dc/terms/> .\n" + "@prefix dila:  <http://purl.dila.edu.tw/resource/> .\n"
                + "@prefix eftr:  <http://purl.84000.co/resource/core/> .\n" + "@prefix f:     <java:io.bdrc.ldspdi.sparql.functions.> .\n" + "@prefix foaf:  <http://xmlns.com/foaf/0.1/> .\n"
                + "@prefix iiif2: <http://iiif.io/api/presentation/2#> .\n" + "@prefix iiif3: <http://iiif.io/api/presentation/3#> .\n" + "@prefix ldp:   <http://www.w3.org/ns/ldp#> .\n" + "@prefix mbbt:  <http://mbingenheimer.net/tools/bibls/> .\n"
                + "@prefix oa:    <http://www.w3.org/ns/oa#> .\n" + "@prefix ola:   <https://openlibrary.org/authors/> .\n" + "@prefix owl:   <http://www.w3.org/2002/07/owl#> .\n" + "@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n"
                + "@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .\n" + "@prefix rsh:   <http://purl.bdrc.io/shacl/core/shape/> .\n" + "@prefix sh:    <http://www.w3.org/ns/shacl#> .\n"
                + "@prefix skos:  <http://www.w3.org/2004/02/skos/core#> .\n" + "@prefix tbr:   <http://purl.bdrc.io/ontology/toberemoved/> .\n" + "@prefix text:  <http://jena.apache.org/text#> .\n"
                + "@prefix tmp:   <http://purl.bdrc.io/ontology/tmp/> .\n" + "@prefix vcard: <http://www.w3.org/2006/vcard/ns#> .\n" + "@prefix viaf:  <http://viaf.org/viaf/> .\n" + "@prefix wd:    <http://www.wikidata.org/entity/> .\n"
                + "@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .\n" + "\n" + "bdr:EV4C0248DD5B2C  a  bdo:PersonBirth ;\n" + "   bdo:notAfter  \"1099\"^^xsd:gYear ;\n" + "   bdo:notBefore  \"1000\"^^xsd:gYear .\n" + "\n"
                + "bdr:NM47BB639E0C2F  a  bdo:PersonPrimaryName ;\n" + "   rdfs:label    \"'phan grags/\"@bo-x-ewts .\n" + "\n" + "bdr:NMB7CE05D057A9  a  bdo:PersonPrimaryTitle ;\n" + "   rdfs:label    \"gzu ston 'phan grags/\"@bo-x-ewts .\n" + "\n"
                + "bdr:NT640DD344824B  a  bdo:Note ;\n" + "   bdo:noteText  \"Seats: Sgre Lce-mtshams and Rgyan-mkhar Stag-lung.\\nTradition called the Gzu-tsho.\"@en .\n" + "\n" + "bdr:NT8D9C070E54CF  a  bdo:Note ;\n"
                + "   bdo:noteText  \"Old TSD Schools table tree: \\n\\n-- Other transmissions\\n\\n-- Specialized Exoteric lineages\\n\\n-- the Vinaya transmissions of the Smad-'dul\"@en .\n" + "\n" + "bdr:P1525  a     bdo:Person ;\n"
                + "   skos:prefLabel  \"'phan grags/\"@bo-x-ewts ;\n" + "   bdo:isRoot    true ;\n" + "   bdo:note      bdr:NT640DD344824B , bdr:NT8D9C070E54CF ;\n" + "   bdo:personEvent  bdr:EV4C0248DD5B2C ;\n"
                + "   bdo:personGender  bdr:GenderMale ;\n" + "   bdo:personName  bdr:NM47BB639E0C2F , bdr:NMB7CE05D057A9 ;\n" + "   bdo:personStudentOf  bdr:P1522 .";

        Graph g = ModelFactory.createDefaultModel().getGraph();
        RDFDataMgr.read(g, new StringReader(testTTL), "", Lang.TURTLE);
        Model m = ModelFactory.createModelForGraph(g);
        m.write(System.out, Lang.TURTLE.getLabel());
    }

    public static void CheckModelTTL() {
        String testTTL = "@base          <file:///usr/local/ldspdi/> .\n" + "@prefix :      <http://purl.bdrc.io/ontology/core/> .\n" + "@prefix adm:   <http://purl.bdrc.io/ontology/admin/> .\n"
                + "@prefix adr:   <http://purl.bdrc.io/resource-nc/auth/> .\n" + "@prefix as:    <http://www.w3.org/ns/activitystreams#> .\n" + "@prefix aut:   <http://purl.bdrc.io/ontology/ext/auth/> .\n"
                + "@prefix bda:   <http://purl.bdrc.io/admindata/> .\n" + "@prefix bdac:  <http://purl.bdrc.io/anncollection/> .\n" + "@prefix bdan:  <http://purl.bdrc.io/annotation/> .\n" + "@prefix bdg:   <http://purl.bdrc.io/graph/> .\n"
                + "@prefix bdgu:  <http://purl.bdrc.io/graph-nc/user/> .\n" + "@prefix bdgup: <http://purl.bdrc.io/graph-nc/user-private/> .\n" + "@prefix bdo:   <http://purl.bdrc.io/ontology/core/> .\n"
                + "@prefix bdou:  <http://purl.bdrc.io/ontology/ext/user/> .\n" + "@prefix bdr:   <http://purl.bdrc.io/resource/> .\n" + "@prefix bdu:   <http://purl.bdrc.io/resource-nc/user/> .\n"
                + "@prefix bf:    <http://id.loc.gov/ontologies/bibframe/> .\n" + "@prefix dcterms: <http://purl.org/dc/terms/> .\n" + "@prefix dila:  <http://purl.dila.edu.tw/resource/> .\n"
                + "@prefix eftr:  <http://purl.84000.co/resource/core/> .\n" + "@prefix f:     <java:io.bdrc.ldspdi.sparql.functions.> .\n" + "@prefix foaf:  <http://xmlns.com/foaf/0.1/> .\n"
                + "@prefix iiif2: <http://iiif.io/api/presentation/2#> .\n" + "@prefix iiif3: <http://iiif.io/api/presentation/3#> .\n" + "@prefix ldp:   <http://www.w3.org/ns/ldp#> .\n" + "@prefix mbbt:  <http://mbingenheimer.net/tools/bibls/> .\n"
                + "@prefix oa:    <http://www.w3.org/ns/oa#> .\n" + "@prefix ola:   <https://openlibrary.org/authors/> .\n" + "@prefix owl:   <http://www.w3.org/2002/07/owl#> .\n" + "@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n"
                + "@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .\n" + "@prefix rsh:   <http://purl.bdrc.io/shacl/core/shape/> .\n" + "@prefix sh:    <http://www.w3.org/ns/shacl#> .\n"
                + "@prefix skos:  <http://www.w3.org/2004/02/skos/core#> .\n" + "@prefix tbr:   <http://purl.bdrc.io/ontology/toberemoved/> .\n" + "@prefix text:  <http://jena.apache.org/text#> .\n"
                + "@prefix tmp:   <http://purl.bdrc.io/ontology/tmp/> .\n" + "@prefix vcard: <http://www.w3.org/2006/vcard/ns#> .\n" + "@prefix viaf:  <http://viaf.org/viaf/> .\n" + "@prefix wd:    <http://www.wikidata.org/entity/> .\n"
                + "@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .\n" + "\n" + "bdr:EV4C0248DD5B2C  a  bdo:PersonBirth ;\n" + "   bdo:notAfter  \"1099\"^^xsd:gYear ;\n" + "   bdo:notBefore  \"1000\"^^xsd:gYear .\n" + "\n"
                + "bdr:NM47BB639E0C2F  a  bdo:PersonPrimaryName ;\n" + "   rdfs:label    \"'phan grags/\"@bo-x-ewts .\n" + "\n" + "bdr:NMB7CE05D057A9  a  bdo:PersonPrimaryTitle ;\n" + "   rdfs:label    \"gzu ston 'phan grags/\"@bo-x-ewts .\n" + "\n"
                + "bdr:NT640DD344824B  a  bdo:Note ;\n" + "   bdo:noteText  \"Seats: Sgre Lce-mtshams and Rgyan-mkhar Stag-lung.\\nTradition called the Gzu-tsho.\"@en .\n" + "\n" + "bdr:NT8D9C070E54CF  a  bdo:Note ;\n"
                + "   bdo:noteText  \"Old TSD Schools table tree: \\n\\n-- Other transmissions\\n\\n-- Specialized Exoteric lineages\\n\\n-- the Vinaya transmissions of the Smad-'dul\"@en .\n" + "\n" + "bdr:P1525  a     bdo:Person ;\n"
                + "   skos:prefLabel  \"'phan grags/\"@bo-x-ewts ;\n" + "   bdo:isRoot    true ;\n" + "   bdo:note      bdr:NT640DD344824B , bdr:NT8D9C070E54CF ;\n" + "   bdo:personEvent  bdr:EV4C0248DD5B2C ;\n"
                + "   bdo:personGender  bdr:GenderMale ;\n" + "   bdo:personName  bdr:NM47BB639E0C2F , bdr:NMB7CE05D057A9 ;\n" + "   bdo:personStudentOf  bdr:P1522 .";
        Model m = ModelFactory.createDefaultModel();
        m.read(new StringReader(testTTL), "", Lang.TURTLE.getLabel());
        m.write(System.out, Lang.TURTLE.getLabel());
    }

    public static void main(String[] args) {
        RDFDataMgrCheck.CheckGraph();
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        // RDFDataMgrCheck.CheckGraphTTL();
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        // RDFDataMgrCheck.CheckModel();
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        // RDFDataMgrCheck.CheckModelTTL();
    }

}
