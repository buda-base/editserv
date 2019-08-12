package io.bdrc.edit.patch;

import java.io.ByteArrayInputStream;

import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Quad;
import org.seaborne.patch.text.RDFPatchReaderText;

import io.bdrc.edit.helpers.EditPatchHeaders;

public class PatchContent {

    private String content;
    private EditPatchHeaders ph;

    public PatchContent(String content) {
        super();
        this.content = content;
        this.ph = new EditPatchHeaders(RDFPatchReaderText.readerHeader(new ByteArrayInputStream(content.getBytes())));
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void appendQuad(String command, Quad q, String type, boolean create) {
        String to_append = command + " " + PatchContent.tag(q.asTriple().getSubject().getURI()) + " " + PatchContent.tag(q.asTriple().getPredicate().getURI()) + " " + PatchContent.tag(q.asTriple().getObject().getURI()) + " "
                + PatchContent.tag(q.getGraph().getURI());
        String deb = content.substring(0, content.lastIndexOf("TC ."));
        String end = content.substring(content.lastIndexOf("TC .") + 1);
        setContent(deb + System.lineSeparator() + to_append + System.lineSeparator() + end);
    }

    public boolean headerContains(String uri, String headerType) {
        Node graphs = ph.getPatchHeader().get(headerType);
        return graphs.getLiteral().toString().contains(uri);
    }

    public static String tag(String s) {
        return "<" + s + ">";
    }

}
