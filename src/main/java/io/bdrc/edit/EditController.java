package io.bdrc.edit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.reasoner.Reasoner;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.modules.FinalizerModule;
import io.bdrc.edit.modules.GitPatchModule;
import io.bdrc.edit.modules.GitRevisionModule;
import io.bdrc.edit.modules.PatchModule;
import io.bdrc.edit.modules.ValidationModule;
import io.bdrc.edit.patch.Session;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.patch.TaskGitManager;
import io.bdrc.edit.txn.BUDATransaction;
import io.bdrc.edit.txn.BUDATransactionManager;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.edit.txn.exceptions.ServiceSequenceException;
import io.bdrc.libraries.BDRCReasoner;
import io.bdrc.libraries.GitHelpers;

@Controller
@RequestMapping("/")
public class EditController {

	public final static Logger log = LoggerFactory.getLogger(EditController.class.getName());
	private Reasoner bdrcReasoner = BDRCReasoner.getReasoner();

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
	@GetMapping(value = "/ping", produces = "text/html")
	public ResponseEntity<String> ping(HttpServletRequest req, HttpServletResponse response) {
		return new ResponseEntity<>(req.getRemoteAddr(), HttpStatus.OK);
	}

	/**
	 * Returns a task for a given user
	 * 
	 */
	@GetMapping(value = "/tasks/{taskId}", produces = "application/json")
	public ResponseEntity<String> getTask(@PathVariable("taskId") String taskId, HttpServletRequest req, HttpServletResponse response) {
		String res = null;
		ObjectMapper mapper = new ObjectMapper();
		HashMap<String, Object> map = new HashMap<>();
		try {
			String userId = getUser(req);
			if (userId == null) {
				return new ResponseEntity<>(getJsonErrorString(new ModuleException("Cannot get the tasks : user is null")), HttpStatus.BAD_REQUEST);

			}
			Task task = TaskGitManager.getTask(taskId, userId);
			map.put("task", task);
			List<Session> list = TaskGitManager.getAllSessions(taskId, userId);
			map.put("sessions", list);
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			mapper.writeValue(os, map);
			res = os.toString();
		} catch (IOException | RevisionSyntaxException e) {
			log.error("A error occured while getting /tasks/" + taskId, e);
			return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.NOT_FOUND);
		}
		return new ResponseEntity<>(res, HttpStatus.OK);
	}

	@DeleteMapping(value = "/tasks", consumes = "application/json")
	public ResponseEntity<String> deleteTask(@RequestBody String jsonTask, String taskId, HttpServletRequest req, HttpServletResponse response) {
		String userId = getUser(req);
		if (userId == null) {
			return new ResponseEntity<>(getJsonErrorString(new ModuleException("Cannot delete the task : user is null")), HttpStatus.BAD_REQUEST);

		}
		Task tk = null;
		try {
			tk = Task.create(jsonTask);
			TaskGitManager.deleteTask(userId, tk.getId());
		} catch (Exception e) {
			e.printStackTrace();
			log.error("A error occured while deleting task" + tk.getId(), e);
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
	@GetMapping(value = "/tasks", produces = "application/json")
	public ResponseEntity<String> getAllOngoingTask(HttpServletRequest req, HttpServletResponse response) {
		String userId = getUser(req);
		if (userId == null) {
			return new ResponseEntity<>(getJsonErrorString(new ModuleException("Cannot process the request : user is null")), HttpStatus.BAD_REQUEST);
		}
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
			log.error("A error occured while listing all tasks", e);
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

	@PutMapping(value = "/tasks", consumes = "application/json", produces = "application/json")
	public ResponseEntity<String> putTask(HttpServletRequest req, HttpServletResponse response, @RequestBody String jsonTask) {
		String userId = getUser(req);
		Task t = null;
		try {
			if (userId == null || jsonTask == null) {
				return new ResponseEntity<>(getJsonErrorString(new ModuleException("Cannot process the task : user is null")),
						HttpStatus.BAD_REQUEST);
			}
			t = Task.create(jsonTask);
			TaskGitManager.saveTask(t);

		} catch (IOException | GitAPIException e) {
			log.error("A error occured while storing task" + t.getId() + "as user :" + userId, e);
			return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.NOT_FOUND);
		}
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

	@PostMapping(value = "/tasks", consumes = "application/json", produces = "application/json")
	public ResponseEntity<String> applyPatch(HttpServletRequest req, HttpServletResponse response, @RequestBody String jsonTask) {
		String userId = getUser(req);
		Task t = null;
		BUDATransaction btx = null;
		try {
			if (userId == null) {
				throw new ModuleException("Cannot save the task : user is null");
			}
			t = Task.create(jsonTask);
			btx = new BUDATransaction(t);
			btx.setStatus(Types.STATUS_PREPARING);
			DataUpdate data = btx.getData();
			if (data != null) {
				btx.addModule(new ValidationModule(data, btx.getLog(), ValidationModule.PRE_VALIDATION), 0);
				btx.addModule(new PatchModule(data, btx.getLog(), bdrcReasoner), 1);
				btx.addModule(new GitPatchModule(data, btx.getLog()), 2);
				btx.addModule(new GitRevisionModule(data, btx.getLog()), 3);
				btx.addModule(new FinalizerModule(data, btx.getLog()), 4);
				btx.setStatus(Types.STATUS_PREPARED);
				BUDATransactionManager.getInstance().queueTxn(btx);
			} else {
				return new ResponseEntity<>("Unknown issue while initializing the transaction for task " + t.getId(),
						HttpStatus.INTERNAL_SERVER_ERROR);
			}
		} catch (Exception e) {
			log.error("A error occured while applying patch for task " + t.getId(), e);
			return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.set("Location", "tasks/" + t.getId());
		return new ResponseEntity<>(responseHeaders, HttpStatus.NO_CONTENT);
	}

	@GetMapping(value = "/queuejob/{id}")
	public ResponseEntity<String> getTxnInfo(HttpServletRequest req, HttpServletResponse response, @PathVariable("id") String id) {
		String status = BUDATransactionManager.getTxnStatus(id);
		log.info("Status for /queuejob endpoint >> {} ", status);
		return new ResponseEntity<>(status, HttpStatus.OK);
	}

	@PostMapping(value = "/pull/{type}")
	public String pullRepo(@PathVariable("type") String type, HttpServletRequest req, HttpServletResponse response)
			throws WrongRepositoryStateException, InvalidConfigurationException, InvalidRemoteException, CanceledException, RefNotFoundException,
			RefNotAdvertisedException, NoHeadException, TransportException, GitAPIException {
		return GitHelpers.pull(type);
	}

	/**
	 * Returns the trig serialization of a resource from the local git repo, either
	 * for a given or the last commit
	 */
	@GetMapping(value = "/gitGraphs/{resType:.+}/**", produces = "text/trig")
	@ResponseBody
	public ResponseEntity<String> getGitGraphCommits(@PathVariable("resType") String resType, HttpServletRequest req, HttpServletResponse response) {
		String info = req.getRequestURL().toString().split("/gitGraphs/")[1];
		if (info.endsWith("/")) {
			info = info.substring(0, info.length() - 1);
		}
		String[] parts = info.split("/");
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.setContentType(new MediaType("text", "trig"));
		String trig = "";
		try {
			if (parts.length == 3) {
				// last commit
				trig = GitHelpers.getGitHeadFileContent(EditConfig.getProperty("gitLocalRoot") + resType.toLowerCase() + "s",
						parts[1] + "/" + parts[2]);

			} else {
				// specific commit
				trig = GitHelpers.getGitHeadFileContent(EditConfig.getProperty("gitLocalRoot") + resType.toLowerCase() + "s",
						parts[1] + "/" + parts[2], parts[3]);
			}
		} catch (IOException | RevisionSyntaxException e) {
			log.error("A error occured while getting git graph at " + req.getRequestURL().toString(), e);
			return new ResponseEntity<>(getJsonErrorString(e), HttpStatus.NOT_FOUND);
		}
		return new ResponseEntity<>(trig, responseHeaders, HttpStatus.OK);
	}

	public String getJsonErrorString(Exception e) {
		return "{ \"exception\": \"" + e.getClass().getCanonicalName() + "\",\n" + "    \"error\": \"" + e.getMessage() + "\"}";
	}

}
