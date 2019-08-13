package io.bdrc.edit;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.Quad;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import io.bdrc.edit.patch.PatchContent;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.patch.TaskGitManager;

@Controller
@RequestMapping("/")
public class JspClientController {

    @GetMapping(value = "/taskView/{taskId}")
    public ModelAndView taskView(@PathVariable("taskId") String taskId, HttpServletRequest req, HttpServletResponse response) throws IOException, RevisionSyntaxException, NoHeadException, GitAPIException {
        String patch = getResourceFileContent("patch/simpleAdd.patch");
        Task tk = TaskGitManager.getTask(taskId, "marc");
        HashMap<String, Object> model = new HashMap<>();
        model.put("task", tk);
        model.put("sessions", TaskGitManager.getAllSessions(taskId, "marc"));
        return new ModelAndView("task", model);
    }

    @GetMapping(value = "/taskList")
    public ModelAndView taskList(HttpServletRequest req, HttpServletResponse response) throws IOException, RevisionSyntaxException, NoHeadException, GitAPIException {
        ArrayList<Task> tsk = TaskGitManager.getAllOngoingTask("marc");
        HashMap<String, Object> model = new HashMap<>();
        model.put("tasks", tsk);
        return new ModelAndView("taskList", model);
    }

    @GetMapping(value = "/taskEdit/{taskId}")
    public ModelAndView taskEdit(@PathVariable("taskId") String taskId, @RequestParam Map<String, String> params, HttpServletRequest req, HttpServletResponse response) throws IOException, RevisionSyntaxException, NoHeadException, GitAPIException {
        Task tk = TaskGitManager.getTask(taskId, "marc");
        ModelMap mod = new ModelMap();
        mod.put("task", tk);
        mod.addAllAttributes(params);
        mod.put("sessions", TaskGitManager.getAllSessions(taskId, "marc"));
        System.out.println("MODEL MAP >>" + mod);
        if (!params.isEmpty()) {
            PatchContent pc = new PatchContent((String) mod.get("patch"));
            Quad q = new Quad(NodeFactory.createURI((String) mod.get("graph")), NodeFactory.createURI((String) mod.get("subj")), NodeFactory.createURI("http://purl.bdrc.io/ontology/core/" + (String) mod.get("predicate")),
                    NodeFactory.createURI((String) mod.get("obj")));
            String ptc = pc.appendQuad((String) mod.get("command"), q, (String) mod.get("type"), ((String) mod.get("create")).contentEquals("on"));
            tk = (Task) mod.get("task");
            tk.setPatch(ptc);
            mod.put("task", tk);
        }
        return new ModelAndView("editTask", mod);
    }

    public static String getResourceFileContent(String file) throws IOException {
        InputStream stream = JspClientController.class.getClassLoader().getResourceAsStream(file);
        StringWriter writer = new StringWriter();
        IOUtils.copy(stream, writer, "UTF-8");
        return writer.toString();
    }

}
