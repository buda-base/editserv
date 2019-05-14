package io.bdrc.edit.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.jena.graph.Node;
import org.seaborne.patch.PatchHeader;

public class EditPatchHeaders {

    public static final String KEY_GRAPH = "graph";
    public static final String KEY_ID = "id";

    private PatchHeader ph;

    public EditPatchHeaders(PatchHeader header) {
        this.ph = header;
        // TODO Auto-generated constructor stub
    }

    public List<String> getGraphUris() {
        List<String> gph = new ArrayList<>();
        Node graphs = ph.get(KEY_GRAPH);
        if (graphs != null) {
            gph = Arrays.asList(graphs.getLiteral().toString().split(","));
        }
        return gph;
    }

    public String getPatchId() {
        return ph.get(KEY_ID).toString();
    }

}
