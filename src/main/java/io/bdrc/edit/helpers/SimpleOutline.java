package io.bdrc.edit.helpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;

import io.bdrc.edit.EditConstants;
import io.bdrc.ewtsconverter.EwtsConverter;

public class SimpleOutline {

    public final static int MIN_TREE_COLUMNS = 4;
    public final static int NB_NON_TREE_COLUMNS = 11;
    
    public Resource outline;
    public Resource root;
    public Resource digitalInstance;
    public List<SimpleOutlineNode> rootChildren;
    public int nbTreeColumns;
    
    public static final Property partOf = ResourceFactory.createProperty(EditConstants.BDO + "partOf");
    public static final Property partIndex = ResourceFactory.createProperty(EditConstants.BDO + "partIndex");
    public static final Property partType = ResourceFactory.createProperty(EditConstants.BDO + "partType");
    public static final Property instanceOf = ResourceFactory.createProperty(EditConstants.BDO + "instanceOf");
    public static final Property colophonP = ResourceFactory.createProperty(EditConstants.BDO + "colophon");
    public static final Property contentLocation = ResourceFactory.createProperty(EditConstants.BDO + "contentLocation");
    public static final Property contentLocationVolume = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationVolume");
    public static final Property contentLocationEndVolume = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationEndVolume");
    public static final Property contentLocationPage = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationPage");
    public static final Property contentLocationEndPage = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationEndPage");
    public static final Property hasTitle = ResourceFactory.createProperty(EditConstants.BDO + "hasTitle");
    public static final Property note = ResourceFactory.createProperty(EditConstants.BDO + "note");
    public static final Property noteText = ResourceFactory.createProperty(EditConstants.BDO + "noteText");
    public static final Property contentLocationInstance = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationInstance");
    
    public static final EwtsConverter ewtsConverter = new EwtsConverter();
    
    public static final String toQname(final Resource res) {
        final String uri = res.getURI();
        if (uri.startsWith(EditConstants.BDR)) {
            return "bdr:"+uri.substring(EditConstants.BDR.length());
        }
        return uri;
    }
    
    public static final String litToString(final Literal l) {
        final String lang = l.getLanguage();
        if (lang == null || lang.equals("en") || lang.startsWith("zh-Han"))
            return l.getLexicalForm();
        if (l.getLanguage().endsWith("-x-ewts"))
            return ewtsConverter.toUnicode(l.getString());
        return l.getString()+"@"+lang;
    }
    
    public static final String partTypeAsString(final Resource res) {
        final Resource pt = res.getPropertyResourceValue(partType);
        if (pt == null)
            return "";
        switch(pt.getLocalName()) {
        case "PartTypeTableOfContent":
            return "TOC";
        case "PartTypeChapter":
            return "C";
        case "PartTypeFascicle":
            return "Fa";
        case "PartTypeSection":
            return "S";
        case "PartTypeText":
            return "T";
        case "PartTypeVolume":
            return "V";
        case "PartTypeEditorial":
            return "E";
        }
        return "";
    }
    
    public static final List<Resource> getOrderedParts(final Resource r) {
        final ResIterator rit = r.getModel().listResourcesWithProperty(partOf, r);
        final Map<Resource, Integer> resToPartIndex = new HashMap<>();
        final List<Resource> res = new ArrayList<>();
        while (rit.hasNext()) {
            final Resource part = rit.next();
            Integer pi = null;
            final Statement s = part.getProperty(partIndex);
            if (s != null)
                pi = s.getInt();
            resToPartIndex.put(part, pi);
            res.add(part);
        }
        final Comparator<Resource> comp = (final Resource a, final Resource b) -> {
            final Integer pia = resToPartIndex.get(a);
            final Integer pib = resToPartIndex.get(b);
            if (pia == null)
                return -1;
            if (pib == null)
                return 1;
            return Integer.compare(pia, pib);
        };
        res.sort(comp);
        return res;
    }
    
    public static final List<String> listSimpleProperty(final Resource res, final Property p) {
        List<String> values = new ArrayList<>();
        StmtIterator si = res.listProperties(p);
        while (si.hasNext()) {
            values.add(litToString(si.next().getLiteral()));
        }
        return values;
    }
    
    public static final class Depth {
        // pointer class
        // the depth of a node is 0 if it's the root of the outline
        public int value = 0;
    }
    
    public static final class SimpleOutlineNode {
        public Resource res;
        public List<SimpleOutlineNode> children;
        public String work = "";
        public List<String> labels;
        public List<String> titles;
        public List<String> notes;
        public String partType;
        public List<String> colophon;
        public Integer pageStart = null;
        public Integer pageEnd = null;
        public Integer volumeStart = null;
        public Integer volumeEnd = null;
        
        public SimpleOutlineNode(final String[] csvRow, final int nb_position_columns, final int previous_volume) {
            
        }
        
        public static Integer combineWith(final Resource r, final Property p, final Integer previousValue, final boolean max) {
            final Statement s = r.getProperty(p);
            if (s == null)
                return previousValue;
            final int newValue = s.getInt();
            if (previousValue == null || (max && newValue > previousValue) || (!max && newValue < previousValue))
                return newValue;
            return previousValue;
        }
        
        public void getSimpleLocation(final Resource w) {
            final StmtIterator locations = this.res.listProperties(contentLocation);
            while (locations.hasNext()) {
                final Resource location = locations.next().getResource();
                if (!location.hasProperty(contentLocationInstance, w))
                    continue;
                this.volumeStart = combineWith(location, contentLocationVolume, this.volumeStart, false);
                this.volumeEnd = combineWith(location, contentLocationEndVolume, this.volumeEnd, true);
                // TODO: this is actually wrong in the case of multiple locations... but we can live with it (for now)
                this.pageStart = combineWith(location, contentLocationPage, this.pageStart, true);
                this.pageEnd = combineWith(location, contentLocationEndPage, this.pageEnd, false);
            }
            if (this.volumeEnd == null && this.volumeStart != null)
                this.volumeEnd = this.volumeStart;
        }
        
        public String getTitlePrefix(final Resource title) {
            final Resource titleTypeR = title.getPropertyResourceValue(RDF.type);
            if (titleTypeR == null)
                return "";
            switch(titleTypeR.getLocalName()) {
            case "TitlePageTitle":
                return "(TP) ";
            case "ColophonTitle":
                return "(C) ";
            case "IncipitTitle":
                return "(I) ";
            case "ReconstructedTitle":
                return "*";
            case "RunningTitle":
                return "(M) ";
            default:
                    return "";
            }
        }
        
        public void getTitles() {
            this.titles = new ArrayList<>();
            final StmtIterator titles = this.res.listProperties(hasTitle);
            while (titles.hasNext()) {
                final Resource title = titles.next().getResource();
                final Statement titleValueS = title.getProperty(RDFS.label);
                if (titleValueS == null)
                    continue;
                this.titles.add(getTitlePrefix(title)+litToString(titleValueS.getLiteral()));
            }
        }
        
        public void getNotes() {
            this.notes = new ArrayList<>();
            final StmtIterator notes = this.res.listProperties(note);
            while (notes.hasNext()) {
                final Resource note = notes.next().getResource();
                final Statement noteTextS = note.getProperty(noteText);
                if (noteTextS == null)
                    continue;
                this.notes.add(litToString(noteTextS.getLiteral()));
            }
        }
        
        public SimpleOutlineNode(final Resource res, final int parentDepth, final Depth maxDepthPointer, final Resource w) {
            this.res = res;
            this.children = new ArrayList<>();
            if (parentDepth+1 > maxDepthPointer.value)
                maxDepthPointer.value = parentDepth+1;
            for (Resource child : getOrderedParts(res)) {
                this.children.add(new SimpleOutlineNode(child, parentDepth+1, maxDepthPointer, w));
            }
            this.partType = partTypeAsString(res);
            final Resource work = res.getPropertyResourceValue(instanceOf);
            if (work != null)
                this.work = toQname(work);
            this.colophon = listSimpleProperty(res, colophonP);
            this.labels = listSimpleProperty(res, SKOS.prefLabel);
            getSimpleLocation(w);
            getTitles();
            getNotes();
        }
        
        public void insertInModel(final Model m, final List<Resource> donotremove) {
            
        }
        
        public static String listToCsvCell(final List<String> valueList) {
            return String.join(" ;; ", valueList);
        }
        
        public String[] getRow(final int nb_position_columns, final int depth) {
            final String[] res = new String[nb_position_columns+NB_NON_TREE_COLUMNS];
            res[0] = toQname(this.res);
            for (int dci = 1 ; dci <= nb_position_columns ; dci++) {
                res[dci] =  (dci == depth) ? "X" : "";
            }
            res[nb_position_columns+1] = this.partType;
            res[nb_position_columns+2] = listToCsvCell(this.labels);
            res[nb_position_columns+3] = listToCsvCell(this.titles);
            res[nb_position_columns+4] = this.work;
            res[nb_position_columns+5] = listToCsvCell(this.notes);
            res[nb_position_columns+6] = listToCsvCell(this.colophon);
            res[nb_position_columns+7] = this.pageStart == null ? "" : Integer.toString(this.pageStart);
            res[nb_position_columns+8] = this.pageEnd == null ? "" : Integer.toString(this.pageEnd);
            res[nb_position_columns+9] = this.volumeStart == null ? "" : Integer.toString(this.volumeStart);
            res[nb_position_columns+10] = this.volumeEnd == null ? "" : Integer.toString(this.volumeEnd);
            return res;
        }
        
        public void addToCsv(final List<String[]> rows, final int nb_position_columns, final int depth) {
            rows.add(this.getRow(nb_position_columns, depth));
            for (final SimpleOutlineNode son : this.children) {
                son.addToCsv(rows, nb_position_columns, depth+1);
            }
        }
    }
    
    public SimpleOutline(final List<String[]> csvData) {
    }
    
    public SimpleOutline(final Resource root, final Resource w) {
        this.root = root;
        this.digitalInstance = w;
        this.rootChildren = new ArrayList<>();
        final Depth maxDepthPointer = new Depth();
        for (Resource child : getOrderedParts(root)) {
            this.rootChildren.add(new SimpleOutlineNode(child, 0, maxDepthPointer, w));
        }
        this.nbTreeColumns = Math.max(maxDepthPointer.value, MIN_TREE_COLUMNS);
    }
    
    public void insertInModel(final Model m) {
        
    }
    
    public static String[] getHeaders(final int nbPositionColumns) {
        final String[] headers = new String[nbPositionColumns+NB_NON_TREE_COLUMNS];
        headers[0] = "RID";
        for (int i=1 ; i <= nbPositionColumns ; i++) {
            headers[i] = "Position";
        }
        headers[nbPositionColumns+1] = "part type";
        headers[nbPositionColumns+2] = "label";
        headers[nbPositionColumns+3] = "titles";
        headers[nbPositionColumns+4] = "work";
        headers[nbPositionColumns+5] = "notes";
        headers[nbPositionColumns+6] = "colophon";
        headers[nbPositionColumns+7] = "img start";
        headers[nbPositionColumns+8] = "img end";
        headers[nbPositionColumns+9] = "volume start";
        headers[nbPositionColumns+10] = "volume end";
        return headers;
    }
    
    public static List<String[]> getTemplate() {
        final List<String[]> res = new ArrayList<>();
        res.add(getHeaders(MIN_TREE_COLUMNS));
        return res;
    }
    
    public List<String[]> asCsv() {
        final List<String[]> res = new ArrayList<>();
        res.add(getHeaders(this.nbTreeColumns));
        for (final SimpleOutlineNode son : this.rootChildren) {
            son.addToCsv(res, this.nbTreeColumns, 1);
        }
        return res;
    }
    
}
