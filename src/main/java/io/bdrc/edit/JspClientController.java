package io.bdrc.edit;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import io.bdrc.edit.patch.Task;

@Controller
@RequestMapping("/")
public class JspClientController {

    @GetMapping(value = "/taskView/{taskId}")
    public ModelAndView taskView(@PathVariable("taskId") String taskId, HttpServletRequest req, HttpServletResponse response) throws IOException {
        String patch = getResourceFileContent("patch/simpleAdd.patch");
        Task tk = new Task("saveMsg", "message", "1a2b3c4d-5e6f-7a8b-9c0d-WWWWWWWWW", "shortName", patch, "marc");
        return new ModelAndView("task", "task", tk);
    }

    public static String getResourceFileContent(String file) throws IOException {
        InputStream stream = JspClientController.class.getClassLoader().getResourceAsStream(file);
        StringWriter writer = new StringWriter();
        IOUtils.copy(stream, writer, "UTF-8");
        return writer.toString();
    }

}
