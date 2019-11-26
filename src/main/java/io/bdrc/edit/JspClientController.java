package io.bdrc.edit;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.Quad;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.patch.PatchContent;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.patch.TaskGitManager;

@Controller
@RequestMapping("/")
public class JspClientController {

    public final static Logger log = LoggerFactory.getLogger(JspClientController.class.getName());

    @GetMapping(value = "/taskView/{taskId}")
    public ModelAndView taskView(@PathVariable("taskId") String taskId, HttpServletRequest req, HttpServletResponse response) throws IOException, RevisionSyntaxException, NoHeadException, GitAPIException {
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

    @GetMapping(value = "/taskDelete/{taskId}")
    public ModelAndView taskDelete(@PathVariable("taskId") String taskId, HttpServletRequest req, HttpServletResponse response) throws IOException, RevisionSyntaxException, NoHeadException, GitAPIException {
        try {
            TaskGitManager.deleteTask("marc", taskId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ArrayList<Task> tsk = TaskGitManager.getAllOngoingTask("marc");
        HashMap<String, Object> model = new HashMap<>();
        model.put("tasks", tsk);
        return new ModelAndView("taskList", model);
    }

    @GetMapping(value = "/taskEdit/{taskId}")
    public ModelAndView taskEdit(@PathVariable("taskId") String taskId, @RequestParam Map<String, String> params, HttpServletRequest req, HttpServletResponse response) throws IOException, RevisionSyntaxException, NoHeadException, GitAPIException {
        String put = params.get("put");
        boolean save = false;
        if (null != put) {
            if ("save".equals(put)) {
                save = true;
            }
        }
        Task tk = TaskGitManager.getTask(taskId, "marc");
        ModelMap mod = new ModelMap();
        mod.put("task", tk);
        mod.addAllAttributes(params);
        mod.put("sessions", TaskGitManager.getAllSessions(taskId, "marc"));
        log.info("MODEL MAP >> {} ", mod);
        String ptc = null;
        if (!params.isEmpty()) {
            log.info("REQUEST PARAMS >> {} ", params);
            PatchContent pc = new PatchContent((String) mod.get("patch"));
            Quad q = new Quad(NodeFactory.createURI((String) mod.get("graph")), NodeFactory.createURI((String) mod.get("subj")), NodeFactory.createURI("http://purl.bdrc.io/ontology/core/" + (String) mod.get("predicate")),
                    NodeFactory.createURI((String) mod.get("obj")));
            if (!save) {
                boolean literal = false;
                if (mod.get("literal") != null && ((String) mod.get("literal")).equals("on")) {
                    literal = true;
                }
                boolean create = false;
                if (mod.get("create") != null && ((String) mod.get("create")).equals("on")) {
                    create = true;
                }
                ptc = pc.appendQuad((String) mod.get("command"), q, (String) mod.get("type"), literal, create);
                log.info("New CONTENT in controller >> {} ", pc.getContent());
            } else {
                ptc = params.get("patch");
            }
            tk = new Task(params.get("saveMsg"), params.get("msg"), params.get("tskid"), params.get("shortName"), ptc, "marc");
            mod.put("task", tk);
            if (save) {
                saveTask(tk, req);
                HashMap<String, Object> model = new HashMap<>();
                model.put("task", tk);
                model.put("sessions", TaskGitManager.getAllSessions(tk.getId(), "marc"));
                return new ModelAndView("task", model);
            }
        }
        return new ModelAndView("editTask", mod);
    }

    @GetMapping(value = "/taskSubmit/{taskId}")
    public ModelAndView taskSubmit(@PathVariable("taskId") String taskId, @RequestParam Map<String, String> params, HttpServletRequest req, HttpServletResponse response) throws IOException, RevisionSyntaxException, NoHeadException, GitAPIException {
        Task tk = TaskGitManager.getTask(taskId, "marc");
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("http://" + req.getServerName() + ":" + req.getServerPort() + "/tasks");
        ObjectMapper mapper = new ObjectMapper();
        StringEntity entity = new StringEntity(mapper.writeValueAsString(tk));
        post.setEntity(entity);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-type", "application/json");
        HttpResponse resp = client.execute(post);
        ModelMap mod = new ModelMap();
        if (resp.getStatusLine().getStatusCode() == 204) {
            client = HttpClientBuilder.create().build();
            String loc = resp.getFirstHeader("Location").getValue();
            HttpGet get = new HttpGet("http://" + req.getServerName() + ":" + req.getServerPort() + "/queuejob/" + tk.getId());
            resp = client.execute(get);
            byte[] b = new byte[(int) resp.getEntity().getContentLength()];
            resp.getEntity().getContent().read(b);
            mod.put("status", new String(b));
        } else {
            mod.put("status", "Something wrong happened");
        }
        mod.put("task", tk);
        return new ModelAndView("queue", mod);
    }

    @GetMapping(value = "/taskStatus/{taskId}")
    public ModelAndView taskStatus(@PathVariable("taskId") String taskId, @RequestParam Map<String, String> params, HttpServletRequest req, HttpServletResponse response) throws IOException, RevisionSyntaxException, NoHeadException, GitAPIException {
        // Task tk = TaskGitManager.getTask(taskId, "marc");
        HttpClient client = HttpClientBuilder.create().build();
        String loc = "http://" + req.getServerName() + ":" + req.getServerPort() + "/queuejob/" + taskId;
        ModelMap mod = new ModelMap();
        mod.put("taskId", taskId);
        HttpGet get = new HttpGet(loc);
        HttpResponse resp = client.execute(get);
        if (resp.getStatusLine().getStatusCode() == 200) {
            byte[] b = new byte[(int) resp.getEntity().getContentLength()];
            resp.getEntity().getContent().read(b);
            mod.put("status", new String(b));
        } else {
            mod.put("status", "Something wrong happened");
        }
        // mod.put("task", tk);
        return new ModelAndView("queue", mod);
    }

    @GetMapping(value = "/createTask")
    public ModelAndView taskCreate(@RequestParam Map<String, String> params, HttpServletRequest req, HttpServletResponse response) throws IOException, RevisionSyntaxException, NoHeadException, GitAPIException {
        String put = params.get("put");
        boolean save = false;
        if (null != put) {
            if ("save".equals(put)) {
                save = true;
            }
        }
        ModelMap mod = new ModelMap();
        if (!save) {
            String patchId = UUID.randomUUID().toString();
            // String patchId = Integer.toString(Objects.hash(System.currentTimeMillis()));
            Task tk = new Task("", "new task", patchId, "", PatchContent.getEmptyPatchContent(patchId), "marc");
            mod = new ModelMap();
            mod.put("task", tk);
            mod.addAllAttributes(params);
            mod.put("sessions", TaskGitManager.getAllSessions(patchId, "marc"));
            log.info("MODEL MAP >> {} ", mod);
            String ptc = null;
            if (!params.isEmpty()) {
                log.info("REQUEST PARAMS >> {} ", params);
                boolean literal = false;
                if (mod.get("literal") != null && ((String) mod.get("literal")).equals("on")) {
                    literal = true;
                }
                boolean create = false;
                if (mod.get("create") != null && ((String) mod.get("create")).equals("on")) {
                    create = true;
                }
                PatchContent pc = new PatchContent((String) mod.get("patch"));
                Quad q = new Quad(NodeFactory.createURI((String) mod.get("graph")), NodeFactory.createURI((String) mod.get("subj")), NodeFactory.createURI("http://purl.bdrc.io/ontology/core/" + (String) mod.get("predicate")),
                        NodeFactory.createURI((String) mod.get("obj")));
                ptc = pc.appendQuad((String) mod.get("command"), q, (String) mod.get("type"), literal, create);
                log.info("New CONTENT in controller >> {} ", pc.getContent());
                tk = new Task(params.get("saveMsg"), params.get("msg"), params.get("tskid"), params.get("shortName"), ptc, "marc");
                mod.put("task", tk);
            }
        } else {
            Task tk = new Task(params.get("saveMsg"), params.get("msg"), params.get("tskid"), params.get("shortName"), params.get("patch"), "marc");
            saveTask(tk, req);
            HashMap<String, Object> model = new HashMap<>();
            model.put("task", tk);
            model.put("sessions", TaskGitManager.getAllSessions(tk.getId(), "marc"));
            return new ModelAndView("task", model);
        }
        return new ModelAndView("editTask", mod);
    }

    private void saveTask(Task tk, HttpServletRequest req) throws ClientProtocolException, IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPut put = new HttpPut("http://" + req.getServerName() + ":" + req.getServerPort() + "/tasks");
        ObjectMapper mapper = new ObjectMapper();
        StringEntity entity = new StringEntity(mapper.writeValueAsString(tk));
        put.setEntity(entity);
        put.setHeader("Content-type", "application/json");
        HttpResponse response = client.execute(put);
        log.info(response.toString());
    }

    public static String getResourceFileContent(String file) throws IOException {
        InputStream stream = JspClientController.class.getClassLoader().getResourceAsStream(file);
        StringWriter writer = new StringWriter();
        IOUtils.copy(stream, writer, "UTF-8");
        return writer.toString();
    }

}
