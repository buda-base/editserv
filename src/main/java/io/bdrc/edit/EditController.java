package io.bdrc.edit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.modules.PatchModule;
import io.bdrc.edit.modules.ValidationModule;
import io.bdrc.edit.patch.Session;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.patch.TaskGitManager;
import io.bdrc.edit.txn.BUDATransaction;
import io.bdrc.edit.txn.BUDATransactionManager;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.ServiceSequenceException;

@Controller
@RequestMapping("/")
public class EditController {

    public String getUser(HttpServletRequest req) {
        // User prof = ((Access) req.getAttribute("access")).getUser();
        // if (prof != null) {
        // return prof.getName();
        // } else {
        return "marc";
        // return null;
        // }
    }

    /**
     * Returns all tasks of a given user
     * 
     * @throws ModuleException
     * 
     */
    @RequestMapping(value = "/ping", produces = "text/html", method = RequestMethod.GET)
    public ResponseEntity<String> ping(HttpServletRequest req, HttpServletResponse response) {
        return new ResponseEntity<>(req.getRemoteAddr(), HttpStatus.OK);
    }

    /**
     * Returns a task for a given user
     * 
     */
    @RequestMapping(value = "/tasks/{taskId}", produces = "application/json", method = RequestMethod.GET)
    public ResponseEntity<String> getTask(@PathVariable("taskId") String taskId, HttpServletRequest req, HttpServletResponse response) {
        String res = null;
        ObjectMapper mapper = new ObjectMapper();
        HashMap<String, Object> map = new HashMap<>();
        try {
            String userId = getUser(req);
            if (userId == null) {
                throw new ModuleException("Cannot get the tasks : user is null");
            }
            Task task = TaskGitManager.getTask(taskId, userId);
            map.put("task", task);
            List<Session> list = TaskGitManager.getAllSessions(taskId, userId);
            map.put("sessions", list);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            mapper.writeValue(os, map);
            res = os.toString();
        } catch (ModuleException | IOException | RevisionSyntaxException | GitAPIException e) {
            e.printStackTrace();
            return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @RequestMapping(value = "/tasks/{taskId}", method = RequestMethod.DELETE)
    public ResponseEntity<String> deleteTask(@PathVariable("taskId") String taskId, HttpServletRequest req, HttpServletResponse response) {
        String userId = getUser(req);
        try {
            TaskGitManager.deleteTask(userId, taskId);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Returns all tasks of a given user
     * 
     * @throws ModuleException
     * 
     */
    @RequestMapping(value = "/tasks", produces = "application/json", method = RequestMethod.GET)
    public ResponseEntity<String> getAllOngoingTask(HttpServletRequest req, HttpServletResponse response) {
        String userId = getUser(req);
        String res = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            ArrayList<String> tsk = TaskGitManager.getAllOngoingTaskId(userId);
            HashMap<String, String> tasks = new HashMap<>();
            ArrayList<JsonNode> test = new ArrayList<>();
            for (String s : tsk) {
                tasks.put(s, TaskGitManager.getTaskAsJson(s, userId));
                test.add(mapper.readTree(TaskGitManager.getTaskAsJson(s, userId)));
            }
            res = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(test);
        } catch (IOException | ModuleException e) {
            e.printStackTrace();
            return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    /**
     * Store a user ongoing task
     * 
     * @throws IOException
     * @throws ServiceSequenceException
     */

    @RequestMapping(value = "/tasks", consumes = "application/json", produces = "application/json", method = RequestMethod.PUT)
    public ResponseEntity<String> putTask(HttpServletRequest req, HttpServletResponse response, @RequestBody String jsonTask) {
        String userId = getUser(req);
        Task t = null;
        try {
            if (userId == null) {
                throw new ModuleException("Cannot save the task : user is null");
            }
            t = Task.create(jsonTask);
            System.out.println("IN PUT, task is " + t);
            TaskGitManager.saveTask(t);

            // res = GitTaskService.getTaskAsJson(taskId, userId);
        } catch (ModuleException | IOException | GitAPIException e) {
            e.printStackTrace();
            return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.NOT_FOUND);
        }
        System.out.println("IN PUT, task is " + t);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Location", "tasks/" + t.getId());
        return new ResponseEntity<>(responseHeaders, HttpStatus.NO_CONTENT);
    }

    /**
     * Applies a Patch
     * 
     * @throws NoSuchAlgorithmException
     * 
     * @throws IOException
     * @throws ServiceSequenceException
     */

    @RequestMapping(value = "/tasks", consumes = "application/json", produces = "application/json", method = RequestMethod.POST)
    public ResponseEntity<String> applyPatch(HttpServletRequest req, HttpServletResponse response, @RequestBody String jsonTask) throws NoSuchAlgorithmException {
        String userId = getUser(req);
        Task t = null;
        try {
            if (userId == null) {
                throw new ModuleException("Cannot save the task : user is null");
            }
            t = Task.create(jsonTask);
            DataUpdate data = new DataUpdate(t);
            BUDATransaction btx = new BUDATransaction(data);
            btx.addModule(new ValidationModule(data, btx.getLog(), ValidationModule.PRE_VALIDATION), 0);
            btx.addModule(new PatchModule(data, btx.getLog()), 1);
            // btx.addModule(new GitPatchModule(data, btx.getLog()), 2);
            // btx.addModule(new GitRevisionModule(data, btx.getLog()), 3);
            // btx.addModule(new FinalizerModule(data, btx.getLog()), 4);
            // btx.setStatus(Types.STATUS_PREPARED);
            BUDATransactionManager.getInstance().queueTxn(btx);
        } catch (ModuleException | IOException | ServiceSequenceException e) {
            e.printStackTrace();
            return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Location", "tasks/" + t.getId());
        return new ResponseEntity<>(responseHeaders, HttpStatus.NO_CONTENT);
    }

    @RequestMapping(value = "/queuejob/{id}", method = RequestMethod.GET)
    public ResponseEntity<String> getTxnInfo(HttpServletRequest req, HttpServletResponse response, @PathVariable("id") String id) {
        String status = BUDATransactionManager.getTxnStatus(id);
        System.out.println("Status >>" + status);
        return new ResponseEntity<>(status, HttpStatus.OK);
    }

    public String getJsonErrorString(Exception e) {
        return "{ \"exception\": \"" + e.getClass().getCanonicalName() + "\",\n" + "    \"error\": \"" + e.getMessage() + "\"}";
    }

}
