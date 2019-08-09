package io.bdrc.edit;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import io.bdrc.edit.patch.Task;
import io.bdrc.edit.patch.TaskGitManager;

@Controller
@RequestMapping("/")
public class JspClientController {

    @GetMapping(value = "/taskView/{taskId}")
    public ModelAndView taskView(@PathVariable("taskId") String taskId, HttpServletRequest req, HttpServletResponse response) throws IOException, RevisionSyntaxException, NoHeadException, GitAPIException {
        String patch = getResourceFileContent("patch/simpleAdd.patch");
        Task tk = new Task("saveMsg", "message", "2531329f-fb09-4ef7-887e-84e648214436", "shortName", patch, "marc");
        HashMap<String, Object> model = new HashMap<>();
        model.put("task", tk);
        model.put("sessions", TaskGitManager.getAllSessions(taskId, "marc"));
        return new ModelAndView("task", model);
    }

    public static String getResourceFileContent(String file) throws IOException {
        InputStream stream = JspClientController.class.getClassLoader().getResourceAsStream(file);
        StringWriter writer = new StringWriter();
        IOUtils.copy(stream, writer, "UTF-8");
        return writer.toString();
    }

}
