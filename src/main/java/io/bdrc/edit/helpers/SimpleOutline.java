package io.bdrc.edit.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.commons.validator.routines.ISBNValidator;
import org.apache.commons.validator.routines.ISSNValidator;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.bdrc.edit.EditConstants;
import io.bdrc.edit.controllers.RIDController;
import io.bdrc.ewtsconverter.EwtsConverter;
import io.bdrc.libraries.LangStrings;

public class SimpleOutline {

    public final static int MIN_TREE_COLUMNS = 4;
    public final static int NB_NON_TREE_COLUMNS = 13;
    
    public final static int MAX_LVST_DIST = 10; // a bit arbitrary
    
    public static final LevenshteinDistance lev = new LevenshteinDistance(MAX_LVST_DIST);
    
    @JsonSerialize(using = ResourceJSONSerializer.class)
    public Resource outline;
    @JsonSerialize(using = ResourceJSONSerializer.class)
    public Resource root;
    @JsonSerialize(using = ResourceJSONSerializer.class)
    public Resource digitalInstance;
    public List<SimpleOutlineNode> rootChildren;
    public int nbTreeColumns;
    public List<Warning> warns;
    public Map<String,Boolean> reservedLnames;
    public List<Resource> allResources;
    public List<Resource> allResourcesInCsv;
    public boolean testMode = false;
    public Integer maxVolumeNumber = null;
    public boolean isEtextMode = false; // true if working with etext instances (IE), false for image instances (W)
    
    public static final Property partOf = ResourceFactory.createProperty(EditConstants.BDO + "partOf");
    public static final Property inRootInstance = ResourceFactory.createProperty(EditConstants.BDO + "inRootInstance");
    public static final Property partIndex = ResourceFactory.createProperty(EditConstants.BDO + "partIndex");
    public static final Property partTreeIndex = ResourceFactory.createProperty(EditConstants.BDO + "partTreeIndex");
    public static final Property partTypeP = ResourceFactory.createProperty(EditConstants.BDO + "partType");
    public static final Property instanceOf = ResourceFactory.createProperty(EditConstants.BDO + "instanceOf");
    public static final Property colophonP = ResourceFactory.createProperty(EditConstants.BDO + "colophon");
    public static final Property contentLocation = ResourceFactory.createProperty(EditConstants.BDO + "contentLocation");
    public static final Property contentLocationVolume = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationVolume");
    public static final Property contentLocationEndVolume = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationEndVolume");
    public static final Property contentLocationPage = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationPage");
    public static final Property contentLocationEndPage = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationEndPage");
    public static final Property hasTitle = ResourceFactory.createProperty(EditConstants.BDO + "hasTitle");
    public static final Property identifiedBy = ResourceFactory.createProperty(EditConstants.BF + "identifiedBy");
    public static final Property seriesNumber = ResourceFactory.createProperty(EditConstants.BDO + "seriesNumber");
    public static final Property note = ResourceFactory.createProperty(EditConstants.BDO + "note");
    public static final Property noteText = ResourceFactory.createProperty(EditConstants.BDO + "noteText");
    public static final Property contentLocationInstance = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationInstance");
    public static final Property contentLocationEtext = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationEtext");
    public static final Property contentLocationEndEtext = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationEndEtext");
    public static final Property contentLocationIdInEtext = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationIdInEtext");
    public static final Property contentLocationEndIdInEtext = ResourceFactory.createProperty(EditConstants.BDO + "contentLocationEndIdInEtext");
    public static final Resource instance = ResourceFactory.createResource(EditConstants.BDO + "Instance");
    public static final Resource Isbn = ResourceFactory.createResource(EditConstants.BF + "Isbn");
    public static final Resource Issn = ResourceFactory.createResource(EditConstants.BF + "Issn");
    public static final Property authorshipStatementP = ResourceFactory.createProperty(EditConstants.BDO + "authorshipStatement");
    
    static final ISBNValidator isbn_validator = new ISBNValidator();
    static final ISSNValidator issn_validator = new ISSNValidator();
    
    public static final EwtsConverter ewtsConverter = new EwtsConverter();
    
    public static class ResourceJSONSerializer extends JsonSerializer<Resource> {
        @Override
        public void serialize(Resource value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.getLocalName());
        }
    }

    
    public static Literal valueToLiteral(final Model m, final String s) {
        String str = s;
        String lang = null;
        final int at_idx = s.lastIndexOf('@');
        if (at_idx > 1 && at_idx > s.length()-10) {
            str = s.substring(0, at_idx);
            lang = s.substring(at_idx+1);
        }
        if (lang == null)
            lang = LangStrings.guessLangTag(str);
        if ("bo".equals(lang)) {
            str = ewtsConverter.toWylie(str);
            lang = "bo-x-ewts";
        }
        str = str.strip();
        if (lang == null)
            return m.createLiteral(str);
        return m.createLiteral(str, lang);
    }
    
    public static final String toQname(final Resource res) {
        final String uri = res.getURI();
        if (uri.startsWith(EditConstants.BDR)) {
            return "bdr:"+uri.substring(EditConstants.BDR.length());
        }
        return uri;
    }
    
    public static final String litToString(final Literal l) {
        final String lang = l.getLanguage();
        if (lang == null || lang.isEmpty() || lang.startsWith("zh-Han"))
            return l.getLexicalForm();
        if (l.getLanguage().endsWith("-x-ewts"))
            return ewtsConverter.toUnicode(l.getString());
        return l.getString()+"@"+lang;
    }
    
    public static final String partTypeAsString(final Resource res) {
        final Resource pt = res.getPropertyResourceValue(partTypeP);
        if (pt == null)
            return "";
        switch(pt.getLocalName()) {
        case "PartTypeTableOfContent":
            return "E";
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
    
    public static final Resource partTypeAsResource(final String s, final Model m) {
        switch(s) {
        case "TOC":
            return m.createResource(EditConstants.BDR+"PartTypeEditorial");
        case "C":
            return m.createResource(EditConstants.BDR+"PartTypeChapter");
        case "Fa":
            return m.createResource(EditConstants.BDR+"PartTypeFascicle");
        case "S":
            return m.createResource(EditConstants.BDR+"PartTypeSection");
        case "T":
            return m.createResource(EditConstants.BDR+"PartTypeText");
        case "V":
            return m.createResource(EditConstants.BDR+"PartTypeVolume");
        case "E":
            return m.createResource(EditConstants.BDR+"PartTypeEditorial");
        }
        return null;
    }
    
    public static final List<Resource> getOrderedParts(final Resource r, final Model m) {
        final ResIterator rit = m.listResourcesWithProperty(partOf, r);
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
        @JsonSerialize(using = ResourceJSONSerializer.class)
        public Resource res;
        public List<SimpleOutlineNode> children;
        public String work = "";
        public List<String> labels;
        public List<String> titles;
        public List<String> identifiers;
        public List<String> notes;
        public String partType;
        public List<String> colophon;
        public List<String> authorshipStatement;
        public Integer pageStart = null;
        public Integer pageEnd = null;
        public Integer volumeStart = null;
        public Integer volumeEnd = null;
        public String idStart = null; // for etext: empty, {number}, or {number}#{string}
        public String idEnd = null;   // for etext: empty, {number}, or {number}#{string}
        public Integer row_i = null;
        
        public void listAllDescendentResources(final List<Resource> list, final List<Warning> warns) {
            // adds self and descendent resources in res, adds a warn if a resource is already present
            if (this.res != null) {
                if (list.contains(this.res))
                    warns.add(new Warning("Resource "+this.res.getLocalName()+" present twice! invalid csv", this.row_i, 0, true));
                else
                    list.add(this.res);
            }
            for (final SimpleOutlineNode son : this.children)
                son.listAllDescendentResources(list, warns);
        }

        public static Integer getWithException(final String cellContent, final int row_i, final int col_i, final List<Warning> warns) {
            try {
                if (!cellContent.isEmpty())
                    return Integer.valueOf(StringUtils.replaceChars(cellContent, "༠༡༢༣༤༥༦༧༨༩", "0123456789"));
            } catch (NumberFormatException e) {
                warns.add(new Warning("field must be a number", row_i, col_i, true));
            }
            return null;
        }
        
        public static List<String> getStrings(final String csvString) {
            final List<String> res = new ArrayList<>();
            final String[] sp = csvString.split(";;");
            for (int i = 0 ; i < sp.length ; i++) {
                final String normalized = sp[i].strip();
                if (!normalized.isEmpty())
                    res.add(normalized);
            }
            return res;
        }
        
        public static final Pattern RIDpattern = Pattern.compile("^[A-Z0-9_\\-]+$");
        public static Resource resourceFromText(String text, final Model m, final String expectedPrefix, final SimpleOutline outline, final int row_i, final int col_i) {
            // use for works
            text = text.trim();
            if (text.startsWith("bdr:"))
                text = text.substring(4);
            final String prefix = RIDController.getTypePrefix(text);
            if (!expectedPrefix.equals(prefix)) {
                outline.warns.add(new Warning("invalid RID, got "+prefix+" but expected "+expectedPrefix, row_i, col_i, true));
                return null;
            }
            if (!RIDpattern.matcher(text).find()) {
                outline.warns.add(new Warning("invalid RID", row_i, col_i, true));
                return null;
            }
            if (m == null)
                return ResourceFactory.createResource(EditConstants.BDR+text);
            return m.createResource(EditConstants.BDR+text);
        }
        
        public SimpleOutlineNode(final String[] csvRow, final SimpleOutline outline, final int row_i) {
            this.children = new ArrayList<>();
            this.row_i = row_i;
            if (csvRow[0].length() > 4) {
                outline.warnIfSubMWInvalid(csvRow[0].substring(4), row_i);
                this.res = ResourceFactory.createResource(EditConstants.BDR+csvRow[0].substring(4));
            } else {
                this.res = null;
            }
            final int nb_position_columns = outline.nbTreeColumns;
            this.partType = csvRow[nb_position_columns+1];
            this.labels = getStrings(csvRow[nb_position_columns+2]);
            this.titles = getStrings(csvRow[nb_position_columns+3]);
            this.work = csvRow[nb_position_columns+4];
            this.notes = getStrings(csvRow[nb_position_columns+5]);
            this.colophon = getStrings(csvRow[nb_position_columns+6]);
            this.authorshipStatement = getStrings(csvRow[nb_position_columns+7]);
            this.identifiers = getStrings(csvRow[nb_position_columns+8]);
            if (outline.isEtextMode) {
                // For etext mode: id start/end instead of page start/end
                this.idStart = csvRow[nb_position_columns+9].trim();
                this.idEnd = csvRow[nb_position_columns+10].trim();
                validateEtextIds(this.idStart, this.idEnd, outline.warns, row_i, nb_position_columns);
            } else {
                // For image mode: img start/end and img group start/end
                this.pageStart = getWithException(csvRow[nb_position_columns+9], row_i, nb_position_columns+9, outline.warns);
                if (this.pageStart != null && (this.pageStart < 1 || this.pageStart > 9999))
                    outline.warns.add(new Warning("invalid image number, minimum is 1, maximum is 9999", row_i, nb_position_columns+9, true));
                this.pageEnd = getWithException(csvRow[nb_position_columns+10], row_i, nb_position_columns+10, outline.warns);
                if (this.pageEnd != null && (this.pageEnd < 1 || this.pageEnd > 9999))
                    outline.warns.add(new Warning("invalid image number, minimum is 1, maximum is 9999", row_i, nb_position_columns+10, true));
            }
            this.volumeStart = getWithException(csvRow[nb_position_columns+11], row_i, nb_position_columns+11, outline.warns);
            if (this.volumeStart != null && outline.maxVolumeNumber != null && (this.volumeStart > outline.maxVolumeNumber || this.volumeStart < 1))
                outline.warns.add(new Warning("invalid image group number, minimum is 1, maximum is "+outline.maxVolumeNumber, row_i, nb_position_columns+11, true));
            this.volumeEnd = getWithException(csvRow[nb_position_columns+12], row_i, nb_position_columns+12, outline.warns);
            if (this.volumeEnd != null && outline.maxVolumeNumber != null && (this.volumeEnd > outline.maxVolumeNumber || this.volumeEnd < 1))
                outline.warns.add(new Warning("invalid image group number, minimum is 1, maximum is "+outline.maxVolumeNumber, row_i, nb_position_columns+12, true));
            if (this.volumeStart != null && this.volumeEnd != null && this.volumeStart > this.volumeEnd)
                outline.warns.add(new Warning("invalid image group number, start image group number should be lower than end image group number", row_i, nb_position_columns+12, true));
        }
        
        public static void validateEtextIds(final String idStart, final String idEnd, final List<Warning> warns, final int row_i, final int nb_position_columns) {
            // Define the regex pattern for valid IDs
            final String basePattern = "([1-9][0-9]{0,3})";
            final String pattern = "^$|" + basePattern + "(#[A-Za-z_\\-0-9]+)?$";
            // Quick regex validation
            if (!idStart.matches(pattern))
            	warns.add(new Warning("invalid etext id, must be empty, integer or integer#string", row_i, nb_position_columns+9, true));
            if (!idEnd.matches(pattern))
            	warns.add(new Warning("invalid etext id, must be empty, integer or integer#string", row_i, nb_position_columns+10, true));
            // Extract etext numbers if present
            final Integer startNum = extractEtextNumber(idStart);
            final Integer endNum = extractEtextNumber(idEnd);
            // Compare etext numbers if both exist
            if (startNum != null && endNum != null && startNum > endNum)
            	warns.add(new Warning("invalid etext number, start should be before end", row_i, nb_position_columns+10, true));
        }

        private static Integer extractEtextNumber(final String id) {
            if (id == null || id.isEmpty()) return null;
            final String[] parts = id.split("#", 2);
            try {
                int num = Integer.parseInt(parts[0]);
                if (num > 0 && num < 9999) return num;
            } catch (NumberFormatException e) {
                // not a valid number
            }
            return null;
        }
        
        public static Integer combineWith(final Resource r, final Property p, final Integer previousValue, final boolean max) {
            final Statement s = r.getProperty(p);
            if (s == null)
                return previousValue;
            try {
                final int newValue = s.getInt();
                if (previousValue == null || (max && newValue > previousValue) || (!max && newValue < previousValue))
                    return newValue;
            } catch (NumberFormatException e) {
                return previousValue;
            }
            return previousValue;
        }
        
        public static String combineEtextIdWith(final Resource location, final Property etextProp, final Property idInEtextProp, final String previousValue, final boolean isStart) {
            final Statement etextS = location.getProperty(etextProp);
            if (etextS == null)
                return previousValue;
            
            try {
                final int etextNum = etextS.getInt();
                final Statement idInEtextS = location.getProperty(idInEtextProp);
                if (idInEtextS != null) {
                    final String idInEtext = idInEtextS.getString();
                    return etextNum + "#" + idInEtext;
                } else {
                    return String.valueOf(etextNum);
                }
            } catch (Exception e) {
                return previousValue;
            }
        }
        
        // Helper method to add etext location properties from an id string
        // Format: empty, {number}, or {number}#{string}
        public static void addEtextLocationToModel(final Resource cl, final String idStr, final Property etextProp, final Property idInEtextProp, final Model m) {
            if (idStr == null || idStr.isEmpty())
                return;
            
            final int hashIdx = idStr.indexOf('#');
            if (hashIdx == -1) {
                // Just a number
                try {
                    final int etextNum = Integer.parseInt(idStr);
                    cl.addProperty(etextProp, m.createTypedLiteral(etextNum, XSDDatatype.XSDinteger));
                } catch (NumberFormatException e) {
                    // Invalid format - should have been caught during CSV validation
                }
            } else {
                // Format: {number}#{string}
                try {
                    final int etextNum = Integer.parseInt(idStr.substring(0, hashIdx));
                    final String idInEtext = idStr.substring(hashIdx + 1);
                    cl.addProperty(etextProp, m.createTypedLiteral(etextNum, XSDDatatype.XSDinteger));
                    cl.addProperty(idInEtextProp, m.createLiteral(idInEtext));
                } catch (NumberFormatException e) {
                    // Invalid format - should have been caught during CSV validation
                }
            }
        }
        
        public void getSimpleLocation(final Resource worie, final boolean isEtextMode) {
            final StmtIterator locations = this.res.listProperties(contentLocation);
            while (locations.hasNext()) {
                final Resource location = locations.next().getResource();
                if (!location.hasProperty(contentLocationInstance, worie))
                    continue;
                if (isEtextMode) {
                    // For etext mode, get etext and idInEtext properties
                    this.idStart = combineEtextIdWith(location, contentLocationEtext, contentLocationIdInEtext, this.idStart, true);
                    this.idEnd = combineEtextIdWith(location, contentLocationEndEtext, contentLocationEndIdInEtext, this.idEnd, false);
                } else {
                    // For image mode, get page and volume properties
                    // TODO: this is actually wrong in the case of multiple locations... but we can live with it (for now)
                    this.pageStart = combineWith(location, contentLocationPage, this.pageStart, true);
                    this.pageEnd = combineWith(location, contentLocationEndPage, this.pageEnd, false);
                }
                this.volumeStart = combineWith(location, contentLocationVolume, this.volumeStart, false);
                this.volumeEnd = combineWith(location, contentLocationEndVolume, this.volumeEnd, true);
            }
            if (this.volumeEnd == null && this.volumeStart != null)
                this.volumeEnd = this.volumeStart;
        }
        
        public static String getTitlePrefix(final Resource title) {
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
        
        public static String getIDPrefix(final Resource title) {
            // common prefixes expected to be found are bdr:NLMId, bdr:RefCPN, bdr:RefNCLK, bf:Isbn, bf:Issn 
            final Resource titleTypeR = title.getPropertyResourceValue(RDF.type);
            if (titleTypeR == null)
                return "";
            switch(titleTypeR.getLocalName()) {
            case "NLMId":
                return "(NLM) ";
            case "LTWAId":
                return "(LTWA) ";
            case "RefIsIAO":
                return "(IsIAO) ";
            case "RefCPN":
                return "(CPN) ";
            case "Isbn":
                return "(ISBN) ";
            case "Ean":
                return "(EAN) ";
            case "Issn":
                return "(ISSN)";
            case "RefNCLK":
                return "(NCLK) ";
            default:
                return "";
            }
        }
        
        public static Resource getIDResource(final String prefix) {
            switch(prefix.toLowerCase().trim()) {
            case "isbn":
                return ResourceFactory.createResource(EditConstants.BF+"Isbn");
            case "ltwa":
                return ResourceFactory.createResource(EditConstants.BDR+"LTWAId");
            case "ean":
                return ResourceFactory.createResource(EditConstants.BF+"Ean");
            case "issn":
                return ResourceFactory.createResource(EditConstants.BF+"Issn");
            case "cpn":
                return ResourceFactory.createResource(EditConstants.BDR+"RefCPN");
            case "isiao":
                return ResourceFactory.createResource(EditConstants.BDR+"RefIsIAO");
            case "nclk":
            case "drepung":
                return ResourceFactory.createResource(EditConstants.BDR+"RefNCLK");
            case "nlm":
                return ResourceFactory.createResource(EditConstants.BDR+"NLMId");
            case "sn":
                return seriesNumber;
            default:
                return null;
            }
        }
        
        public static Resource getTitleResource(final String prefix) {
            switch(prefix.toLowerCase().trim()) {
            case "tp":
                return ResourceFactory.createResource(EditConstants.BDO+"TitlePageTitle");
            case "c":
                return ResourceFactory.createResource(EditConstants.BDO+"ColophonTitle");
            case "i":
                return ResourceFactory.createResource(EditConstants.BDO+"IncipitTitle");
            case "*":
                return ResourceFactory.createResource(EditConstants.BDO+"ReconstructedTitle");
            case "m":
                return ResourceFactory.createResource(EditConstants.BDO+"RunningTitle");
            default:
                return null;
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
                final String litString = litToString(titleValueS.getLiteral());
                if (this.labels.contains(litString)) {
                    this.labels.remove(litString);
                    this.titles.add(0, getTitlePrefix(title)+litString);
                } else {
                    this.titles.add(getTitlePrefix(title)+litString);
                }
            }
        }
        
        public void getIDs() {
            this.identifiers = new ArrayList<>();
            final StmtIterator ids = this.res.listProperties(identifiedBy);
            while (ids.hasNext()) {
                final Resource id = ids.next().getResource();
                final Statement idValueS = id.getProperty(RDF.value);
                if (idValueS == null)
                    continue;
                final String litString = litToString(idValueS.getLiteral());
                this.identifiers.add(getIDPrefix(id)+litString);
            }
            final StmtIterator sns = this.res.listProperties(seriesNumber);
            while (sns.hasNext()) {
                final String litString = litToString(sns.next().getLiteral());
                this.identifiers.add("(SN) "+litString);                
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
        
        public SimpleOutlineNode(final Resource res, final int parentDepth, final Depth maxDepthPointer, final Resource w, final boolean isEtextMode) {
            this.res = res;
            this.children = new ArrayList<>();
            if (parentDepth+1 > maxDepthPointer.value)
                maxDepthPointer.value = parentDepth+1;
            for (Resource child : getOrderedParts(res, res.getModel())) {
                this.children.add(new SimpleOutlineNode(child, parentDepth+1, maxDepthPointer, w, isEtextMode));
            }
            this.partType = partTypeAsString(res);
            final Resource work = res.getPropertyResourceValue(instanceOf);
            if (work != null)
                this.work = toQname(work);
            this.colophon = listSimpleProperty(res, colophonP);
            this.authorshipStatement = listSimpleProperty(res, authorshipStatementP);
            this.labels = listSimpleProperty(res, SKOS.prefLabel);
            getSimpleLocation(w, isEtextMode);
            getTitles();
            getIDs();
            getNotes();
        }
        
        public static void removeRecursiveSafe(final Model m, final Resource r, final SimpleOutline outline) {
            // needs to be called after reuseExistingIDs
            StmtIterator sit = m.listStatements(r, null, (RDFNode) null);
            if (!sit.hasNext()) {
                //System.out.println("exit early while removing "+r.getLocalName());
                // in that case we don't want to remove back references
                return;
            }
            // list of associated resources to remove
            final Set<Resource> connex_resources = new HashSet<>();
            while (sit.hasNext()) {
                final Statement st = sit.next();
                if (st.getObject().isResource()) {
                    final Resource or = st.getResource();
                    if (!outline.allResourcesInCsv.contains(or) && or != r)
                        connex_resources.add(or);
                }
            }
            m.removeAll(r, null, (RDFNode) null);
            sit = m.listStatements(null, null, r);
            while (sit.hasNext()) {
                final Resource or = sit.next().getSubject();
                if (!outline.allResourcesInCsv.contains(or) && or != r)
                    connex_resources.add(or);
            }
            m.removeAll(null, null, r);
            // remove at the end to avoid concurrent modification exception
            for (final Resource or : connex_resources) {
                removeRecursiveSafe(m, or, outline);
            }
        }
        
        public void cleanupDescendants(Model m, final SimpleOutline outline) {
            // function that fills an empty this.res in the children of a node,
            // recursively
            List<Resource> parts = null;
            if (this.res != null) {
                parts = getOrderedParts(this.res, m);
                for (int i = 0 ; i < parts.size() ; i++) {
                    if (outline.allResourcesInCsv.contains(parts.get(i)))
                        parts.set(i, null);
                }
            }
            // now we have the parts in the model that are not in the csv
            // we assign the resources to their equivalent in the new csv when possible
            for (int i = 0 ; i < this.children.size() ; i++) {
                final SimpleOutlineNode son = this.children.get(i);
                Resource part = null;
                if (parts != null && i < parts.size())
                    part = parts.get(i);
                if (son.res == null && part != null) {
                    son.res = part;
                    outline.allResourcesInCsv.add(part);
                    parts.set(i, null);
                }
                son.cleanupDescendants(m, outline);
            }
            if (parts == null)
                return;
            // remove parts that are not in the csv:
            for (final Resource part : parts) {
                if (part != null && !outline.allResourcesInCsv.contains(part)) {
                    removeRecursiveSafe(m, part, outline);
                }
            }
        }
        
        public static void reinsertSimple(final Model m, final Resource r, final Property p, final List<String> valueList) {
            m.remove(m.listStatements(r, p, (RDFNode) null));
            for (final String value : valueList) {
                final Literal l = valueToLiteral(m, value);
                m.add(r, p, l);
            }
        }
        
        // returns a contentlocation
        public static Resource removeContentLocations(final Model m, final Resource r, final Resource locationInstance) {
            final StmtIterator cli = r.listProperties(contentLocation);
            final List<Statement> toRemove = new ArrayList<>();
            Resource res = null;
            while (cli.hasNext()) {
                final Statement cls = cli.next();
                final Resource cl = cls.getResource();
                if (cl.hasProperty(contentLocationInstance, locationInstance)) {
                    toRemove.add(cls);
                    toRemove.addAll(m.listStatements(cl, null, (RDFNode) null).toList());
                    res = cl;
                }
            }
            m.remove(toRemove);
            return res;
        }
        
        public static List<Integer[]> matchStrings(final List<String> l1, final List<String> l2) {
            // returns an ordered list of pairs of indexes of l1 , l2 that minimize the distance
            // between elements of each pair (to a reasonable extent)
            
            final List<Integer[]> res = new ArrayList<>();
            
            // quick optimization:
            if (l1.size() == 1 && l2.size() == 1) {
                res.add(new Integer[] {0, 0});
                return res;
            }
            
            final Map<Integer,List<Integer[]>> distance_to_idx_pair = new HashMap<>();
            
            // Compute Levenshtein distances for all pairs
            for (int i = 0; i < l1.size(); i++) {
                for (int j = 0; j < l2.size(); j++) {
                    int dst_i_j = 0;
                    if (l1.get(i) != null || l2.get(j) != null) {
                        if (l1.get(i) == null || l2.get(j) == null) {
                            dst_i_j = MAX_LVST_DIST+1;
                        } else {
                            dst_i_j = lev.apply(l1.get(i), l2.get(j));
                        }
                    }
                    if (dst_i_j == -1)
                        dst_i_j = MAX_LVST_DIST+1;
                    final List<Integer[]> list_pairs = distance_to_idx_pair.computeIfAbsent(dst_i_j, x -> new ArrayList<>());
                    list_pairs.add(new Integer[] {i,j});
                }
            }
            final List<Integer> sorted_dst = new ArrayList<>(distance_to_idx_pair.keySet());
            sorted_dst.sort(null);
            final Map<Integer,Boolean> seen_l1 = new HashMap<>();
            final Map<Integer,Boolean> seen_l2 = new HashMap<>();
            
            for (final Integer dst : sorted_dst) {
                for (final Integer[] pair : distance_to_idx_pair.get(dst)) {
                    if (!seen_l1.containsKey(pair[0]) && !seen_l2.containsKey(pair[1])) {
                        res.add(pair);
                        seen_l1.put(pair[0], true);
                        seen_l2.put(pair[1], true);
                    }
                }
            }
            
            // now res contains all existing pairs, we fill it with the remaining singletons
            if (l1.size() < l2.size()) {
                for (int i = 0 ; i < l2.size() ; i++) {
                    if (!seen_l2.containsKey(i))
                        res.add(new Integer[] {null, i});
                }
            }
            if (l2.size() < l1.size()) {
                for (int i = 0 ; i < l1.size() ; i++) {
                    if (!seen_l1.containsKey(i))
                        res.add(new Integer[] {i, null});
                }
            }
            return res;
        }

        
        public void reinsertNotes(final Model m, final SimpleOutline outline) {
            final List<Resource> existing_nodes = new ArrayList<>();
            final List<String> existing_values = new ArrayList<>();
            
            final StmtIterator sti = m.listStatements(this.res, note, (RDFNode) null);
            while (sti.hasNext()) {
                final Resource node = sti.next().getResource();
                final StmtIterator sti2 = m.listStatements(node, noteText, (RDFNode) null);
                if (sti2.hasNext()) {
                    final String value = litToString(sti2.next().getLiteral());
                    existing_nodes.add(node);
                    existing_values.add(value);
                }
            }
            final List<Integer[]> corrs = matchStrings(existing_values, this.notes);
            for (final Integer[] corr : corrs) {
                if (corr[1] == null) {
                    removeRecursiveSafe(m, existing_nodes.get(corr[0]), outline);
                    continue;
                }
                Resource node = null;
                final Literal lit = valueToLiteral(m, this.notes.get(corr[1]));
                if (corr[0] == null) {
                    // new node
                    node = outline.newResource(m, "NT", this.res);
                    m.add(this.res, note, node);
                    m.add(node, RDF.type, m.createResource(EditConstants.BDO+"Note"));
                    m.add(node, noteText, lit);
                } else {
                    node = existing_nodes.get(corr[0]);
                    m.removeAll(node, noteText, (RDFNode) null);
                    m.add(node, noteText, lit);
                }
            }
        }
        
        public void getSplitPrefixedValues(final List<String> str_values, final List<Resource> res_values, final boolean title_type) {
            final List<String> vals = title_type ? this.titles : this.identifiers;
            for (final String val : vals) {
                final int close_par_idx = val.indexOf(')');
                if (val.startsWith("*")) { // only in titles
                    str_values.add(val);
                    res_values.add(getTitleResource("*"));
                    continue;
                }
                if (!val.startsWith("(") || close_par_idx == -1 || close_par_idx > 5) {
                    str_values.add(val);
                    res_values.add(null);
                    continue;
                }
                final String type_str = val.substring(1, close_par_idx);
                final Resource type = title_type ? getTitleResource(type_str) : getIDResource(type_str);
                if (type == null) {
                    str_values.add(val);
                    res_values.add(null);
                    continue;
                }
                str_values.add(val.substring(close_par_idx+1).trim());
                res_values.add(type);
            }
        }
        
        public void reinsertTitles(final Model m, final SimpleOutline outline) {
            final List<Resource> existing_nodes = new ArrayList<>();
            final List<String> existing_values = new ArrayList<>();
            final List<String> str_values = new ArrayList<>();
            final List<Resource> new_types = new ArrayList<>();
            this.getSplitPrefixedValues(str_values, new_types, true);
            // we derive prefLabels from titles when the label column is empty
            final Map<String,Boolean> seenLangTags = new HashMap<>();
            
            final StmtIterator sti = m.listStatements(this.res, hasTitle, (RDFNode) null);
            while (sti.hasNext()) {
                final Resource node = sti.next().getResource();
                final StmtIterator sti2 = m.listStatements(node, RDFS.label, (RDFNode) null);
                if (sti2.hasNext()) {
                    final String value = litToString(sti2.next().getLiteral());
                    existing_nodes.add(node);
                    existing_values.add(value);
                }
            }
            final List<Integer[]> corrs = matchStrings(existing_values, str_values);
            for (final Integer[] corr : corrs) {
                if (corr[1] == null) {
                    removeRecursiveSafe(m, existing_nodes.get(corr[0]), outline);
                    continue;
                }
                Resource node = null;
                final Literal lit = valueToLiteral(m, str_values.get(corr[1]));
                if (this.labels.isEmpty() && !seenLangTags.containsKey(lit.getLanguage())) {
                    m.add(this.res, SKOS.prefLabel, lit);
                    seenLangTags.put(lit.getLanguage(), true);
                }
                final Resource type = new_types.get(corr[1]);
                if (corr[0] == null) {
                    // new node
                    node = outline.newResource(m, "TT", this.res);
                    m.add(this.res, hasTitle, node);
                    m.add(node, RDF.type, type != null ? type : m.createResource(EditConstants.BDO+"Title"));
                    m.add(node, RDFS.label, lit);
                } else {
                    node = existing_nodes.get(corr[0]);
                    m.removeAll(node, RDFS.label, (RDFNode) null);
                    m.add(node, RDFS.label, lit);
                    if (type != null) {
                        m.removeAll(node, RDF.type, (RDFNode) null);
                        m.add(node, RDF.type, type);
                    }
                }
            }
        }
        
        public static String normalize_isxn(final String isxn) {
            return isxn.toUpperCase().replaceAll("-", "");
        }
        
        public void reinsertIDs(final Model m, final SimpleOutline outline) {
            final List<Resource> existing_nodes = new ArrayList<>();
            final List<String> existing_values = new ArrayList<>();
            final List<String> str_values = new ArrayList<>();
            final List<Resource> new_types = new ArrayList<>();
            this.getSplitPrefixedValues(str_values, new_types, false);
            
            // first, handle seriesNumber
            m.remove(m.listStatements(this.res, seriesNumber, (RDFNode) null));
            for (int i = 0 ; i < new_types.size() ; i++) {
                if (seriesNumber.equals(new_types.get(i))) {
                    m.add(this.res, seriesNumber, m.createLiteral(str_values.get(i)));
                    str_values.set(i, null);
                    new_types.set(i, null);
                }
            }
            // then the rest of the IDs
            final StmtIterator sti = m.listStatements(this.res, identifiedBy, (RDFNode) null);
            while (sti.hasNext()) {
                final Resource node = sti.next().getResource();
                final StmtIterator sti2 = m.listStatements(node, RDF.value, (RDFNode) null);
                if (sti2.hasNext()) {
                    final String value = litToString(sti2.next().getLiteral());
                    existing_nodes.add(node);
                    existing_values.add(value);
                }
            }
            final List<Integer[]> corrs = matchStrings(existing_values, str_values);
            for (final Integer[] corr : corrs) {
                if (corr[1] != null && new_types.get(corr[1]) == null && str_values.get(corr[1]) == null) {
                    continue; // series number
                }
                if (corr[1] == null) {
                    removeRecursiveSafe(m, existing_nodes.get(corr[0]), outline);
                    continue;
                }
                Resource node = null;
                final Resource type = new_types.get(corr[1]);
                Literal lit = m.createLiteral(str_values.get(corr[1]));
                if (type != null && (type.equals(Isbn) || type.equals(Issn))) {
                    if (type.equals(Isbn) && !isbn_validator.isValid(str_values.get(corr[1])))
                        outline.warns.add(new Warning("invalid ISBN", this.row_i, outline.nbTreeColumns+7, false));
                    if (type.equals(Issn) && !issn_validator.isValid(str_values.get(corr[1])))
                        outline.warns.add(new Warning("invalid ISSN", this.row_i, outline.nbTreeColumns+7, false));
                    lit = m.createLiteral(normalize_isxn(str_values.get(corr[1])));
                }
                if (corr[0] == null) {
                    // new node
                    node = outline.newResource(m, "ID", this.res);
                    m.add(this.res, identifiedBy, node);
                    if (type == null) {
                        outline.warns.add(new Warning("invalid prefix, should be (ISBN), (ISSN), (NLM), (NCLK), (CPN) or (SN)", this.row_i, outline.nbTreeColumns+7, true));
                    } else {
                        m.add(node, RDF.type, type);
                    }
                    m.add(node, RDF.value, lit);
                } else {
                    node = existing_nodes.get(corr[0]);
                    m.removeAll(node, RDF.value, (RDFNode) null);
                    m.add(node, RDF.value, lit);
                    if (type != null) {
                        m.removeAll(node, RDF.type, (RDFNode) null);
                        m.add(node, RDF.type, type);
                    } else {
                        outline.warns.add(new Warning("invalid prefix, should be (ISBN), (ISSN), (NLM), (NCLK), (CPN)", this.row_i, outline.nbTreeColumns+7, true));
                    }
                }
            }
        }
        
        public void insertInModel(final Model m, final SimpleOutline outline, final Resource parent, final int part_index, final String parentPartIndex, final int nb_siblings) {
            // we assume that reuseExistingIDs and removefromModel have been called
            // on the relevant nodes
            // create res if not present:
            if (this.res == null)
                this.res = outline.newResource(m, "MW", null);
            else
                this.res = m.createResource(this.res.getURI()); // connect to model
            // first, remove and reinsert simple properties:
            reinsertSimple(m, this.res, colophonP, this.colophon);
            reinsertSimple(m, this.res, authorshipStatementP, this.authorshipStatement);
            reinsertSimple(m, this.res, SKOS.prefLabel, this.labels);
            m.remove(m.listStatements(this.res, partOf, (RDFNode) null));
            m.add(this.res, partOf, parent);
            m.add(this.res, inRootInstance, outline.root);
            m.add(this.res, RDF.type, instance);
            // part_index
            m.remove(m.listStatements(this.res, partIndex, (RDFNode) null));
            m.add(this.res, partIndex, m.createTypedLiteral(part_index, XSDDatatype.XSDinteger));
            // partTreeIndex
            String thisPTI = parentPartIndex+(parentPartIndex.isEmpty() ? "" : ".")+String.format("%02d", part_index);
            if (nb_siblings > 100)
                thisPTI = parentPartIndex+(parentPartIndex.isEmpty() ? "" : ".")+String.format("%04d", part_index);
            m.remove(m.listStatements(this.res, partTreeIndex, (RDFNode) null));
            m.add(this.res, partTreeIndex, m.createLiteral(thisPTI));
            final Resource pt = partTypeAsResource(this.partType, m);
            if (pt == null) {
                outline.warns.add(new Warning("cannot interpret "+this.partType+" as a part type", this.row_i, outline.nbTreeColumns+1, true));
            } else {
                m.remove(m.listStatements(this.res, partTypeP, (RDFNode) null));
                m.add(this.res, partTypeP, pt);
            }
            this.reinsertNotes(m, outline);
            this.reinsertTitles(m, outline);
            this.reinsertIDs(m, outline);
            m.remove(m.listStatements(this.res, instanceOf, (RDFNode) null));
            if (this.work != null && !this.work.isEmpty()) {
                final Resource work = resourceFromText(this.work, m, "WA", outline, this.row_i, outline.nbTreeColumns+4);
                if (work != null)
                    m.add(this.res, instanceOf, work);
            }
            // content location
            Resource cl = removeContentLocations(m, this.res, outline.digitalInstance);
            if (outline.isEtextMode) {
                // For etext mode: use idStart/idEnd
                if (this.idStart != null || this.idEnd != null || this.volumeEnd != null || this.volumeStart != null) {
                    if (cl == null)
                        cl = outline.newResource(m, "CL", this.res);
                    cl.addProperty(RDF.type, m.createResource(EditConstants.BDO+"ContentLocation"));
                    m.add(this.res, contentLocation, cl);
                    cl.addProperty(contentLocationInstance, outline.digitalInstance);
                    
                    // Use helper method to add etext location properties
                    addEtextLocationToModel(cl, this.idStart, contentLocationEtext, contentLocationIdInEtext, m);
                    addEtextLocationToModel(cl, this.idEnd, contentLocationEndEtext, contentLocationEndIdInEtext, m);
                    
                    // Volume properties are the same
                    if (this.volumeStart != null)
                        cl.addProperty(contentLocationVolume, m.createTypedLiteral(this.volumeStart, XSDDatatype.XSDinteger));
                    if (this.volumeEnd != null)
                        cl.addProperty(contentLocationEndVolume, m.createTypedLiteral(this.volumeEnd, XSDDatatype.XSDinteger));
                }
            } else {
                // For image mode: use pageStart/pageEnd
                if (this.pageStart != null || this.pageEnd != null || this.volumeEnd != null || this.volumeStart != null) {
                    if (cl == null)
                        cl = outline.newResource(m, "CL", this.res);
                    cl.addProperty(RDF.type, m.createResource(EditConstants.BDO+"ContentLocation"));
                    m.add(this.res, contentLocation, cl);
                    cl.addProperty(contentLocationInstance, outline.digitalInstance);
                    if (this.pageStart != null)
                        cl.addProperty(contentLocationPage, m.createTypedLiteral(this.pageStart, XSDDatatype.XSDinteger));
                    if (this.pageEnd != null)
                        cl.addProperty(contentLocationEndPage, m.createTypedLiteral(this.pageEnd, XSDDatatype.XSDinteger));
                    if (this.volumeStart != null)
                        cl.addProperty(contentLocationVolume, m.createTypedLiteral(this.volumeStart, XSDDatatype.XSDinteger));
                    if (this.volumeEnd != null)
                        cl.addProperty(contentLocationEndVolume, m.createTypedLiteral(this.volumeEnd, XSDDatatype.XSDinteger));
                }
            }
            // children
            for (int i = 0 ; i < this.children.size() ; i++) {
                final SimpleOutlineNode son = this.children.get(i);
                son.insertInModel(m, outline, this.res, i+1, thisPTI, this.children.size()); // part types start at 1
            }
        }
        
        public static String listToCsvCell(final List<String> valueList) {
            if (valueList == null)
                return "";
            return String.join(" ;; ", valueList);
        }
        
        public String[] getRow(final int nb_position_columns, final int depth, final boolean isEtextMode) {
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
            res[nb_position_columns+7] = listToCsvCell(this.authorshipStatement);
            res[nb_position_columns+8] = listToCsvCell(this.identifiers);
            
            if (isEtextMode) {
                // For etext mode: id start/end instead of page start/end
                res[nb_position_columns+9] = this.idStart == null ? "" : this.idStart;
                res[nb_position_columns+10] = this.idEnd == null ? "" : this.idEnd;
            } else {
                // For image mode: img start/end
                res[nb_position_columns+9] = this.pageStart == null ? "" : Integer.toString(this.pageStart);
                res[nb_position_columns+10] = this.pageEnd == null ? "" : Integer.toString(this.pageEnd);
            }
            
            // Volume columns are the same for both modes
            res[nb_position_columns+11] = this.volumeStart == null ? "" : Integer.toString(this.volumeStart);
            res[nb_position_columns+12] = this.volumeEnd == null ? "" : Integer.toString(this.volumeEnd);
            return res;
        }
        
        public void addToCsv(final List<String[]> rows, final int nb_position_columns, final int depth, final boolean isEtextMode) {
            rows.add(this.getRow(nb_position_columns, depth, isEtextMode));
            for (final SimpleOutlineNode son : this.children) {
                son.addToCsv(rows, nb_position_columns, depth+1, isEtextMode);
            }
        }
    }
    
    public static final class Warning {
        public final String msg;
        public final Integer row;
        public final Integer col;
        public final boolean blocking;
        
        public Warning(final String msg, final Integer row, final Integer col, final boolean blocking) {
            this.msg = msg;
            this.row = row;
            this.col = (col == null) ? null : col+1; // we start the columns at 0 in the call but we want to start them at 1 in the warning
            this.blocking = blocking;
        }
    }
    
    public SimpleOutline(final List<String[]> csvData, final Resource o, final Resource mw, final Resource w, final Integer maxVolumeNumber) {
        this.digitalInstance = w;
        this.root = mw;
        this.outline = o;
        this.rootChildren = new ArrayList<>();
        this.warns = new ArrayList<>();
        this.maxVolumeNumber = maxVolumeNumber;
        // Detect etext mode based on digitalInstance identifier
        this.isEtextMode = w != null && w.getLocalName().startsWith("IE");
        if (csvData.size() < 2)
            return;
        this.nbTreeColumns = 0;
        final String[] headers = csvData.get(0);
        for (int i = 1 ; i < headers.length ; i ++) {
            if (headers[i].toLowerCase().startsWith("position")) // sometimes Excel adds a suffix to columns
                this.nbTreeColumns += 1;
            else
                break;
        }
        final SimpleOutlineNode[] levelToParent = new SimpleOutlineNode[this.nbTreeColumns+2];
        levelToParent[0] = null; // kind of nonsensical since levels start at 1 in the csv
        levelToParent[1] = null; // this would be the root
        int previousLevel = 0;
        for (int row_i = 1 ; row_i < csvData.size() ; row_i ++) {
            final String[] row = csvData.get(row_i);
            int rowLevel = 0;
            for (int i = 1 ; i <= this.nbTreeColumns ; i++) {
                if (!row[i].isEmpty()) {
                    // important, we don't allow jumps in level
                    rowLevel = Math.min(i, previousLevel+1);
                    previousLevel = rowLevel;
                    break;
                }
            }
            if (rowLevel == 0) {
                warns.add(new Warning("missing position", row_i, null, true));
                continue;
            }
            final SimpleOutlineNode son = new SimpleOutlineNode(row, this, row_i);
            if (levelToParent[rowLevel] != null) {
                levelToParent[rowLevel].children.add(son);
            } else {
                this.rootChildren.add(son);
            }
            levelToParent[rowLevel+1] = son;
        }
    }
    
    public SimpleOutline(final Resource root, final Resource w) {
        this.root = root;
        this.digitalInstance = w;
        this.rootChildren = new ArrayList<>();
        // Detect etext mode based on digitalInstance identifier
        this.isEtextMode = w != null && w.getLocalName().startsWith("IE");
        final Depth maxDepthPointer = new Depth();
        for (Resource child : getOrderedParts(root, root.getModel())) {
            this.rootChildren.add(new SimpleOutlineNode(child, 0, maxDepthPointer, w, this.isEtextMode));
        }
        this.nbTreeColumns = Math.max(maxDepthPointer.value, MIN_TREE_COLUMNS);
    }
    
    public List<Resource> listAllNodeResources() {
        // returns a list of all the instance resources given in the csv
        final List<Resource> res = new ArrayList<>();
        for (final SimpleOutlineNode son : this.rootChildren)
            son.listAllDescendentResources(res, this.warns);
        return res;
    }
    
    public static final char[] symbols = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    public int cur_random = 0;
    public String randomString(final int nb_chars) {
        if (this.testMode) {
            cur_random += 1;
            return Integer.toString(cur_random);
        }
        final char[] res = new char[nb_chars];
        for (int idx = 0; idx < nb_chars; ++idx)
            res[idx] = symbols[ThreadLocalRandom.current().nextInt(symbols.length)];
        return new String(res);
    }
    
    public static final int MAX_ITER = 200;
    public String newLname(final String prefix, final int nb_chars) {
        for (int i = 0 ; i < MAX_ITER ; i++) {
            final String candidate = prefix+this.randomString(nb_chars);
            if (!this.reservedLnames.containsKey(candidate)) {
                this.reservedLnames.put(candidate, true);
                return candidate;
            }
        }
        this.warns.add(new Warning("error, cannot generate a random ID with prefix "+prefix+" and "+Integer.toString(nb_chars)+" characters after 200 iterations!", 0, 0, true));
        return "";
    }
    
    public Resource newResource(final Model m, final String prefix, final Resource parent) {
        // prefix is "MW" for instance, "TT" for title, "NT" for note, "CL" for content location
        if (prefix.equals("MW")) {
            final String lname = this.newLname(this.root.getLocalName()+"_"+this.outline.getLocalName()+"_" , 6);
            return m.createResource(EditConstants.BDR+lname);
        } else if (prefix.equals("TT") || prefix.equals("NT")) {
            final String lname = prefix+this.newLname(this.outline.getLocalName().substring(1)+"_O_" , 6);
            return m.createResource(EditConstants.BDR+lname);
        } else {
            // CL
            final String lname = prefix+this.newLname(this.outline.getLocalName().substring(1)+"_O_"+this.digitalInstance.getLocalName()+"_" , 6);
            return m.createResource(EditConstants.BDR+lname);
        }
    }
    
    private Pattern submwpattern = null;
    public void warnIfSubMWInvalid(final String submwlname, final int row_i) {
        // adds warnings if an mw from the csv (column 0) has a suspicious name
        if (this.submwpattern == null)
            this.submwpattern = Pattern.compile("^("+this.root.getLocalName()+"_[A-Z0-9]+|"+this.root.getLocalName()+"_"+this.outline.getLocalName()+"_[A-Z0-9]+)$");
        if (!this.submwpattern.matcher(submwlname).find())
            this.warns.add(new Warning("invalid id, should be in the form bdr:"+this.root.getLocalName()+"_"+this.outline.getLocalName()+"_XXX or bdr:"+this.root.getLocalName()+"_XXX, leave the column blank to generate one automatically", row_i, 0, true));
    }
    
    public void cleanupDescendants(final Model m) {
        // function that fills an empty this.res in the children of a node,
        // recursively
        final List<Resource> parts = getOrderedParts(this.root, m);
        for (int i = 0 ; i < parts.size() ; i++) {
            if (this.allResourcesInCsv.contains(parts.get(i)))
                parts.set(i, null);
        }
        // now we have the parts in the model that are not in the csv
        // we assign the resources to their equivalent in the new csv when possible
        for (int i = 0 ; i < this.rootChildren.size() ; i++) {
            final SimpleOutlineNode son = this.rootChildren.get(i);
            final Resource part = (i < parts.size()) ? parts.get(i) : null;
            if (son.res == null && part != null) {
                son.res = part;
                this.allResourcesInCsv.add(part);
                parts.set(i, null);
            }
            son.cleanupDescendants(m, this);
        }
        for (final Resource part : parts) {
            if (part != null && !this.allResourcesInCsv.contains(part)) {
                SimpleOutlineNode.removeRecursiveSafe(m, part, this);
            }
        }
    }
    
    public void insertInModel(final Model m, final Resource mw, final Resource w) {
        // handling RIDs
        this.reservedLnames = new HashMap<>();
        this.allResources = m.listSubjects().toList();
        this.allResourcesInCsv = this.listAllNodeResources();
        this.allResources.addAll(this.allResourcesInCsv);
        // TODO: for the case where we're not operating at the root, we should probably read
        // the partTreeIndex instead of using ""
        for (final Resource r : this.allResources)
            this.reservedLnames.put(r.getLocalName(), true);
        // reusing IDs at the root level
        this.cleanupDescendants(m);
        for (int i = 0 ; i < this.rootChildren.size() ; i++) {
            final SimpleOutlineNode son = this.rootChildren.get(i);
            son.insertInModel(m, this, mw, i+1, "", this.rootChildren.size());
        }
    }
    
    public static Model getModelTemplate(final Resource o, final Resource mw) {
        final Model res = ModelFactory.createDefaultModel();
        return res;
    }
    
    public static String[] getHeaders(final int nbPositionColumns, final boolean isEtextMode) {
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
        headers[nbPositionColumns+7] = "authorshipStatement";
        headers[nbPositionColumns+8] = "identifiers";
        
        if (isEtextMode) {
            headers[nbPositionColumns+9] = "id start";
            headers[nbPositionColumns+10] = "id end";
            headers[nbPositionColumns+11] = "vol start";
            headers[nbPositionColumns+12] = "vol end";
        } else {
            headers[nbPositionColumns+9] = "img start";
            headers[nbPositionColumns+10] = "img end";
            headers[nbPositionColumns+11] = "img grp start";
            headers[nbPositionColumns+12] = "img grp end";
        }
        return headers;
    }
    
    public static String[] getHeaders(final int nbPositionColumns) {
        // Default to image mode for backward compatibility
        return getHeaders(nbPositionColumns, false);
    }
    
    public static List<String[]> getTemplate(final boolean isEtextMode) {
        final List<String[]> res = new ArrayList<>();
        res.add(getHeaders(MIN_TREE_COLUMNS, isEtextMode));
        return res;
    }
    
    public static List<String[]> getTemplate() {
        // Default to image mode for backward compatibility
        return getTemplate(false);
    }
    
    public boolean hasBlockingWarns() {
        for (final Warning warn : this.warns) {
            if (warn.blocking)
                return true;
        }
        return false;
    }
    
    public List<String[]> asCsv() {
        final List<String[]> res = new ArrayList<>();
        res.add(getHeaders(this.nbTreeColumns, this.isEtextMode));
        for (final SimpleOutlineNode son : this.rootChildren) {
            son.addToCsv(res, this.nbTreeColumns, 1, this.isEtextMode);
        }
        return res;
    }
    
}
