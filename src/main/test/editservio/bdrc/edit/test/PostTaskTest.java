package editservio.bdrc.edit.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.edit.EditApplication;
import io.bdrc.edit.EditConfig;
import io.bdrc.edit.helpers.DataUpdate;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.service.GitPatchService;
import io.bdrc.edit.service.PatchService;
import io.bdrc.edit.txn.exceptions.ServiceException;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = EditApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PostTaskTest {

    @Autowired
    Environment environment;

    @BeforeClass
    public static void init() {
        EditConfig.init();
    }

    // @Test
    public void ping() throws ClientProtocolException, IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpGet get = new HttpGet("http://localhost:" + environment.getProperty("local.server.port") + "/ping");
        HttpResponse response = client.execute(get);
        byte[] b = new byte[(int) response.getEntity().getContentLength()];
        response.getEntity().getContent().read(b);
        System.out.println(new String(b));
        assert (response.getStatusLine().getStatusCode() == 200);
    }

    // @Test
    public void postSimpleTask() throws ClientProtocolException, IOException {
        String patch = getResourceFileContent("patch/simpleAdd.patch");
        Task tk = new Task("saveMsg", "message", "uuid:1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5r6", "shortName", patch, "marc");
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("http://localhost:" + environment.getProperty("local.server.port") + "/tasks");
        ObjectMapper mapper = new ObjectMapper();
        StringEntity entity = new StringEntity(mapper.writeValueAsString(tk));
        post.setEntity(entity);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-type", "application/json");
        HttpResponse response = client.execute(post);
        assert (response.getStatusLine().getStatusCode() == 204);
        client = HttpClientBuilder.create().build();
        HttpGet get = new HttpGet("http://localhost:" + environment.getProperty("local.server.port") + "/queuecjob/uuid:1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5r6");
        response = client.execute(get);
        // System.out.println("RESP >>" + response);
        assert (response.getStatusLine().getStatusCode() == 200);
    }

    @Test
    public void taskService() throws ClientProtocolException, IOException, ServiceException, NoSuchAlgorithmException {
        String patch = getResourceFileContent("patch/simpleAdd.patch");
        Task tk = new Task("saveMsg", "message", "uuid:1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5r6", "shortName", patch, "marc");
        DataUpdate data = new DataUpdate(tk);
        PatchService tsvc = new PatchService(data);
        tsvc.run();
        assert (Checker.checkResourceInConstruct("checks/simpleAdd.check", "bdr:P1583"));
        GitPatchService gps = new GitPatchService(data);
        gps.run();
        patch = getResourceFileContent("patch/simpleDelete.patch");
        Task tk1 = new Task("saveMsg", "message", "uuid:1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5r6", "shortName", patch, "marc");
        tsvc = new PatchService(new DataUpdate(tk1));
        tsvc.run();
        assert (!Checker.checkResourceInConstruct("checks/simpleAdd.check", "bdr:P1583"));
    }

    public static String getResourceFileContent(String file) throws IOException {
        InputStream stream = PostTaskTest.class.getClassLoader().getResourceAsStream(file);
        StringWriter writer = new StringWriter();
        IOUtils.copy(stream, writer, "UTF-8");
        return writer.toString();
    }
}
