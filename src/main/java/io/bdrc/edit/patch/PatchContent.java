package io.bdrc.edit.patch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Quad;
import org.assertj.core.util.Arrays;
import org.seaborne.patch.text.RDFPatchReaderText;

import io.bdrc.edit.EditConfig;
import io.bdrc.edit.helpers.EditPatchHeaders;

public class PatchContent {

    private String content;
    private EditPatchHeaders ph;

    public PatchContent(String content) {
        setContent(normalizeContent(content));
        this.ph = new EditPatchHeaders(RDFPatchReaderText.readerHeader(new ByteArrayInputStream(content.getBytes())));
    }

    public static String getEmptyPatchContent(String patchId) {
        String st = "H id \"" + patchId + "\" ." + System.lineSeparator();
        st = st + "TX ." + System.lineSeparator();
        st = st + "TC ." + System.lineSeparator();
        return st;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String appendQuad(String command, Quad q, String type, boolean create) throws IOException {
        String to_append = command + " " + PatchContent.tag(q.asTriple().getSubject().getURI()) + " " + PatchContent.tag(q.asTriple().getPredicate().getURI()) + " " + PatchContent.tag(q.asTriple().getObject().getURI()) + " "
                + PatchContent.tag(q.getGraph().getURI()) + " .";
        String deb = content.substring(0, content.lastIndexOf("TC ."));
        setContent(normalizeContent(deb + System.lineSeparator() + to_append + System.lineSeparator() + "TC ."));
        if (!headerContains(q.getGraph().getURI(), EditPatchHeaders.KEY_MAPPING)) {
            String mapping = getHeaderLine(EditPatchHeaders.KEY_MAPPING);
            String replace = "";
            if (mapping != null) {
                replace = mapping.substring(0, mapping.lastIndexOf('"')).trim() + ";" + q.getGraph().getURI() + "-" + type + "\" .";
                content = content.replace(mapping, replace);
            }

        }
        if (create && !headerContains(q.getGraph().getURI(), EditPatchHeaders.KEY_CREATE)) {
            String cr = getHeaderLine(EditPatchHeaders.KEY_CREATE);
            String replace = "";
            if (cr != null) {
                replace = cr.substring(0, cr.lastIndexOf('"')).trim() + ";" + q.getGraph().getURI() + "\" .";
                content = content.replace(cr, replace);
            }
        }
        content = normalizeContent(content);
        return content;
    }

    public boolean headerContains(String uri, String headerType) {
        Node graphs = ph.getPatchHeader().get(headerType);
        if (graphs != null) {
            return graphs.getLiteral().toString().contains(uri);
        }
        return false;
    }

    public static String tag(String s) {
        return "<" + s + ">";
    }

    public String normalizeContent(String s) {
        List<Object> chunks = Arrays.asList(s.split(Pattern.compile(" \\.").pattern()));
        String ret = null;
        StringWriter sw = new StringWriter();
        BufferedWriter bw = new BufferedWriter(sw);
        try {
            for (Object o : chunks) {
                bw.write(((String) o).trim() + " .");
                bw.newLine();
            }
            bw.flush();
            ret = sw.getBuffer().toString();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    public String getHeaderLine(String key) throws IOException {
        BufferedReader br = new BufferedReader(new StringReader(content));
        String line = br.readLine();
        while (line != null) {
            if (line.startsWith("H") && line.contains(" " + key + " ")) {
                return line;
            }
            line = br.readLine();
        }
        return null;
    }

    @Override
    public String toString() {
        return "PatchContent [content=" + content + ", ph=" + ph + "]";
    }

    public static void main(String[] args) throws IOException {
        EditConfig.init();
        Task tk = TaskGitManager.getTask("abcdef-ghijk-lmnopq-rstuvwxyz", "marc");
        PatchContent pc = new PatchContent(tk.getPatch());
        System.out.println(pc.getContent());
    }

}
