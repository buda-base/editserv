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
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.reasoner.Reasoner;
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
import io.bdrc.edit.modules.GitPatchModule;
import io.bdrc.edit.modules.GitRevisionModule;
import io.bdrc.edit.modules.PatchModule;
import io.bdrc.edit.patch.Task;
import io.bdrc.edit.txn.TransactionLog;
import io.bdrc.edit.txn.exceptions.ModuleException;
import io.bdrc.libraries.BDRCReasoner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = EditApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PostTaskTest {

    @Autowired
    Environment environment;

    private Reasoner bdrcReasoner = BDRCReasoner.getReasoner();

    @BeforeClass
    public static void init() {
        EditConfig.init();
    }

    @Test
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
        Task tk = new Task("saveMsg", "message", "1a2b3c4d-5e6f-7a8b-9c0d-WWWWWWWWW", "shortName", patch, "marc");
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("http://localhost:8080/tasks");
        // HttpPost post = new HttpPost("http://localhost:" +
        // environment.getProperty("local.server.port") + "/tasks");
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

    // @Test
    public void putNewTask() throws ClientProtocolException, IOException {
        String patch = getResourceFileContent("patch/create.patch");
        Task tk = new Task("saveMsg", "message", "abcdef-ghijk-lmnopq-rstuvwxyz", "shortName", patch, "marc");
        HttpClient client = HttpClientBuilder.create().build();
        HttpPut put = new HttpPut("http://localhost:" + environment.getProperty("local.server.port") + "/tasks");
        ObjectMapper mapper = new ObjectMapper();
        StringEntity entity = new StringEntity(mapper.writeValueAsString(tk));
        put.setEntity(entity);
        // put.setHeader("Accept", "application/json");
        put.setHeader("Content-type", "application/json");
        HttpResponse response = client.execute(put);
        System.out.println(response);
        assert (response.getStatusLine().getStatusCode() == 204);
        assert (response.getFirstHeader("Location").getValue().equals("tasks/" + tk.getId()));
    }

    // @Test
    public void deletePatch() throws ClientProtocolException, IOException, ModuleException, NoSuchAlgorithmException {

        String patch = getResourceFileContent("patch/createDelete.patch");
        Task tk1 = new Task("saveMsg", "message", "uuid:1vvv3c4d-5zzzf-7a8b-9c0d-e1qqq3b4c5r6", "shortName", patch, "marc");
        DataUpdate data = new DataUpdate(tk1);
        PatchModule tsvc = new PatchModule(new DataUpdate(tk1), new TransactionLog(EditConfig.getProperty("logRootDir") + "marc/", tk1), bdrcReasoner);
        tsvc.run();
        GitPatchModule gps = new GitPatchModule(data, new TransactionLog(EditConfig.getProperty("logRootDir") + "marc/", tk1));
        gps.run();
        GitRevisionModule grs = new GitRevisionModule(data, new TransactionLog(EditConfig.getProperty("logRootDir") + "marc/", tk1));
        grs.run();
    }

    // @Test
    public void createPatch() throws ClientProtocolException, IOException, ModuleException, NoSuchAlgorithmException {
        String patch = getResourceFileContent("patch/mixed.patch");
        Task tk = new Task("saveMsg", "message", "uuid:1xxx3c4d-5yyyf-7a8b-9c0d-e1kkk3bTTTT", "shortName", patch, "marc");
        DataUpdate data = new DataUpdate(tk);
        PatchModule tsvc = new PatchModule(data, new TransactionLog(EditConfig.getProperty("logRootDir") + "marc/", tk), bdrcReasoner);
        tsvc.run();
        GitPatchModule gps = new GitPatchModule(data, new TransactionLog(EditConfig.getProperty("logRootDir") + "marc/", tk));
        gps.run();
        GitRevisionModule grs = new GitRevisionModule(data, new TransactionLog(EditConfig.getProperty("logRootDir") + "marc/", tk));
        grs.run();
    }

    // @Test
    public void simpleAddPatch() throws ClientProtocolException, IOException, ModuleException, NoSuchAlgorithmException {
        String patch = getResourceFileContent("patch/simpleAdd.patch");
        Task tk = new Task("saveMsg", "message", "1a2b3c4d-5e6f-7a8b-9c0d-XXXWWWWWW", "shortName", patch, "marc");
        DataUpdate data = new DataUpdate(tk);
        TransactionLog lg = new TransactionLog(EditConfig.getProperty("logRootDir") + "marc/", tk);
        PatchModule tsvc = new PatchModule(data, lg, bdrcReasoner);
        tsvc.run();
        GitPatchModule gps = new GitPatchModule(data, lg);
        gps.run();
        GitRevisionModule grs = new GitRevisionModule(data, lg);
        grs.run();
    }

    // @Test
    public void dummy() {
        System.out.println("Disabling all tests and running this dummy one for now...");
    }

    // @Test
    public void testBDRCReasoner() {
        Reasoner bdrcReasoner = BDRCReasoner.getReasonerWithSymetry();
        RDFConnectionRemoteBuilder builder = RDFConnectionFuseki.create().destination(EditConfig.getProperty("fusekiData"));
        RDFConnectionFuseki fusConn = ((RDFConnectionFuseki) builder.build());
        Model gp = fusConn.fetch("http://purl.bdrc.io/graph/P1525");
        gp.write(System.out, "TURTLE");
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        InfModel inf = ModelFactory.createInfModel(bdrcReasoner, gp);
        inf.write(System.out, "TURTLE");
    }

    public static String getResourceFileContent(String file) throws IOException {
        InputStream stream = PostTaskTest.class.getClassLoader().getResourceAsStream(file);
        StringWriter writer = new StringWriter();
        IOUtils.copy(stream, writer, "UTF-8");
        return writer.toString();
    }
}
