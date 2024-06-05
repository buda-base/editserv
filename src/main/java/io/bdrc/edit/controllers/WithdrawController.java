package io.bdrc.edit.controllers;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.bdrc.auth.AccessInfo;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.EditConstants;
import io.bdrc.edit.commons.data.FusekiWriteHelpers;
import io.bdrc.edit.commons.data.QueryProcessor;
import io.bdrc.edit.commons.ops.CommonsGit;
import io.bdrc.edit.commons.ops.CommonsGit.GitInfo;
import io.bdrc.edit.helpers.ModelUtils;
import io.bdrc.edit.txn.exceptions.EditException;
import io.bdrc.edit.user.BudaUser;
import io.bdrc.libraries.Models;

@Controller
@RequestMapping("/")
public class WithdrawController {
    
    public final static Logger log = LoggerFactory.getLogger(WithdrawController.class.getName());
    
    public boolean is_released(final Resource r) throws IOException, EditException {
        final GitInfo gi = CommonsGit.gitInfoForResource(r, true);
        if (gi.ds == null)
            return false;
        final Model m = ModelUtils.getMainModel(gi.ds);
        return m.contains(null, ModelUtils.admStatus, ModelUtils.StatusReleased);
    }
    
    public void add_log_entry(final Model m, final Resource graph, final Resource lg, final Resource user, final String now, final String commitMessage) {
        final Resource adm = m.createResource(Models.BDA + graph.getLocalName());
        m.add(adm, ModelUtils.logEntry, lg);
        m.add(lg, RDF.type, ModelUtils.WithdrawData);
        m.add(lg, ModelUtils.logWho, user);
        m.add(lg, ModelUtils.logDate, m.createTypedLiteral(now, XSDDatatype.XSDdateTime));
        m.add(lg, ModelUtils.logMessage, m.createLiteral(commitMessage, "en"));
        m.add(lg, ModelUtils.logMethod, ModelUtils.BatchMethod);
        
    }
    
    public Resource mark_withdrawn_for(final Resource from, final Resource to, final Resource user, final String now, final String commitMessage) throws IOException, EditException, GitAPIException {
        final GitInfo gi = CommonsGit.gitInfoForResource(from, true);
        if (gi.ds == null)
            throw new EditException("can't find graph "+ from.getURI());
        final Model m = ModelUtils.getMainModel(gi.ds);
        final Resource lg = ModelUtils.newSubject(m, EditConstants.BDR+"LG0"+from.getLocalName()+"_");
        final Resource adm = m.createResource(Models.BDA + from.getLocalName());
        if (m.contains(adm, ModelUtils.admReplaceWith, to) && m.contains(adm, ModelUtils.admStatus, ModelUtils.StatusWithdrawn))
            return lg;
        m.removeAll(adm, ModelUtils.admStatus, (RDFNode) null);
        m.add(adm, ModelUtils.admStatus, ModelUtils.StatusWithdrawn);
        m.add(adm, ModelUtils.admReplaceWith, to);
        final Resource graph = ModelUtils.getMainGraph(gi.ds); 
        add_log_entry(m, graph, lg, user, now, commitMessage);
        CommonsGit.commitAndPush(gi, CommonsGit.getCommitMessage(graph, commitMessage, user));
        if (!EditConfig.dryrunmodefuseki)
            FusekiWriteHelpers.putDataset(gi);
        // returns the log entry resource
        return lg;
    }
    
    public Resource update_references(final Resource from, final Resource to, final Resource graph, final Resource lg, final Resource user, final String now, final String commitMessage) throws IOException, EditException, GitAPIException {
        final GitInfo gi = CommonsGit.gitInfoForResource(graph, true);
        if (gi.ds == null)
            throw new EditException("can't find graph "+ graph.getURI());
        final Model m = ModelUtils.getMainModel(gi.ds);
        // get a list so that we can remove triples without disturbing the
        // iterator
        final List<Statement> sl = m.listStatements(null, null, from).toList();
        for (final Statement s : sl) {
            m.add(s.getSubject(), s.getPredicate(), to);
            m.remove(s);
        }
        if (sl.size() == 0)
            throw new EditException("could not find references to " + from.getURI() + " in " + graph.getURI() );
        add_log_entry(m, graph, lg, user, now, commitMessage);
        CommonsGit.commitAndPush(gi, CommonsGit.getCommitMessage(graph, commitMessage, user));
        if (!EditConfig.dryrunmodefuseki)
            FusekiWriteHelpers.putDataset(gi);
        return null;
    }
    
    public List<Resource> get_graphs_with_reference_to(final Resource r) {
        final String sparqlStr = "select distinct ?g { ?adm <"+ModelUtils.admAbout.getURI()+"> <"+r.getURI()+"> ; <"+ModelUtils.admGraphId.getURI()+"> ?thisg . graph ?g { ?s ?p <"+r.getURI()+"> FILTER(EXISTS{?a <"+ModelUtils.admGitPath.getURI()+"> ?b}) }  FILTER(?g != ?thisg) }";
        ResultSet rs = QueryProcessor.getSelectResultSet(sparqlStr, FusekiWriteHelpers.FusekiSparqlEndpoint);
        final List<Resource> res = new ArrayList<>();
        while (rs.hasNext()) {
            final QuerySolution qs = rs.next();
            final Resource resr = qs.getResource("g");
            res.add(resr);
        }
        return res;
    }
    
    public static final List<String> authorizedPrefixesForWithdrawal = Arrays.asList("WA", "P", "G", "WAS");
    public static boolean withdrawalOk(final String fromRID, final String toRID) {
        final String fromTPrefix = RIDController.getTypePrefix(fromRID);
        final String toTPrefix = RIDController.getTypePrefix(fromRID);
        if (fromTPrefix == null || !fromTPrefix.equals(toTPrefix)) {
            return false;
        }
        return authorizedPrefixesForWithdrawal.contains(fromTPrefix);
    }
    
    @PostMapping(value = "/withdraw")
    public synchronized ResponseEntity<List<String>> withdraw(
            @RequestParam(value = "from") String from_qname,
            @RequestParam(value = "to") String to_qname,
            HttpServletRequest req, 
            HttpServletResponse response
            ) throws IOException, EditException, GitAPIException {
        if (!from_qname.startsWith("bdr:") || !to_qname.startsWith("bdr:") || from_qname.equals(to_qname))
            throw new EditException(400, "can't understand notifysync arguments "+ from_qname + ", " + to_qname);
        if (!withdrawalOk(from_qname.substring(4), to_qname.substring(4)))
            throw new EditException(400, "cannot withdraw "+ from_qname + " in favor of " + to_qname);
        final Resource from = ResourceFactory.createResource(Models.BDR+from_qname.substring(4));
        final Resource to = ResourceFactory.createResource(Models.BDR+to_qname.substring(4));
        if (!is_released(to))
            throw new EditException(400, "target resource " + to_qname + " is not released");
        final String now = ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT );
        Resource user = null;
        if (EditConfig.useAuth) {
            AccessInfo acc = (AccessInfo) req.getAttribute("access");
            if (!acc.isAdmin() && !acc.isEditor() && !acc.isContributor())
                throw new EditException(403, "this requires being logged in with an admin, editor or contributor account");
            String authId = acc.getId();
            if (authId == null) {
                log.error("couldn't find authId for {}"+acc.toString());
                throw new EditException(500, "couldn't find authId");
            }
            final String auth0Id = authId.substring(authId.lastIndexOf("|") + 1);
            try {
                user = BudaUser.getRdfProfile(auth0Id);
            } catch (IOException e) {
                throw new EditException(500, "couldn't get RDF profile", e);
            }
            if (user == null) {
                throw new EditException(500, "couldn't get RDF profile");
            }
        } else {
            user = EditConstants.TEST_USER;
        }
        final String commitMessage = "withdraw "+from.getLocalName()+" in favor of "+to.getLocalName();
        final Resource lg = mark_withdrawn_for(from, to, user, now, commitMessage);
        final List<Resource> graphs_to_update = get_graphs_with_reference_to(from);
        final List<String> res = new ArrayList<>();
        for (final Resource g : graphs_to_update) {
            if (g.getURI().startsWith(Models.BDR+"O")) {
                res.add("!"+g.getLocalName());
            } else {
                update_references(from, to, g, lg, user, now, commitMessage);
                res.add(g.getLocalName());
            }
        }
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                .body(res);
    }
}
