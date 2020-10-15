package editservio.bdrc.edit.test;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import com.auth0.client.auth.AuthAPI;
import com.auth0.net.AuthRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdrc.auth.AuthProps;
import io.bdrc.auth.rdf.RdfAuthModel;
import io.bdrc.edit.EditApplication;
import io.bdrc.edit.EditConfig;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = EditApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CheckUserAPI {

    @Autowired
    Environment environment;

    static AuthAPI auth;
    static String token;
    static String publicToken;
    static String adminToken;
    static String privateToken;
    static String staffToken;

    public final static Logger log = LoggerFactory.getLogger(CheckUserAPI.class.getName());

    @BeforeClass
    public static void init() throws IOException {
        EditConfig.init();
        Properties props = EditConfig.getProperties();
        InputStream input = new FileInputStream("/etc/buda/editserv/editserv.properties");
        // Properties props = new Properties();
        props.load(input);
        input.close();
        InputStream is = new FileInputStream("/etc/buda/share/shared-private.properties");
        props.load(is);
        AuthProps.init(props);
        is.close();
        auth = new AuthAPI("bdrc-io.auth0.com", AuthProps.getProperty("lds-pdiClientID"), AuthProps.getProperty("lds-pdiClientSecret"));
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("https://bdrc-io.auth0.com/oauth/token");
        HashMap<String, String> json = new HashMap<>();
        json.put("grant_type", "client_credentials");
        json.put("client_id", AuthProps.getProperty("lds-pdiClientID"));
        json.put("client_secret", AuthProps.getProperty("lds-pdiClientSecret"));
        json.put("audience", "https://bdrc-io.auth0.com/api/v2/");
        ObjectMapper mapper = new ObjectMapper();
        String post_data = mapper.writer().writeValueAsString(json);
        StringEntity se = new StringEntity(post_data);
        se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
        post.setEntity(se);
        HttpResponse response = client.execute(post);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.getEntity().writeTo(baos);
        String json_resp = baos.toString();
        baos.close();
        JsonNode node = mapper.readTree(json_resp);
        token = node.findValue("access_token").asText();
        RdfAuthModel.init();
        log.info("USERS >> {}" + RdfAuthModel.getUsers());
        // set123Token();
        set456Token();
        // setPrivateToken();
        // setStaffToken();
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
    public void patchPublic() throws ClientProtocolException, IOException, NoSuchAlgorithmException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPatch patch = new HttpPatch("http://localhost:" + environment.getProperty("local.server.port") + "/resource-nc/user/patch/U51251837");
        patch.addHeader("Authorization", "Bearer " + adminToken);
        StringEntity entity = new StringEntity(getResourceFileContent("patch/changePublic.patch"));
        patch.setEntity(entity);
        HttpResponse resp = client.execute(patch);
        System.out.println("RESP STATUS public resource >> " + resp.getStatusLine());
        assert (resp.getStatusLine().getStatusCode() == 200);
        System.out.println("RESULT >> " + EntityUtils.toString(resp.getEntity()));

    }

    @Test
    public void bulkRename() throws ClientProtocolException, IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("http://localhost:" + environment.getProperty("local.server.port") + "/bulk/renameProp");
        post.addHeader("Authorization", "Bearer " + adminToken);
        HashMap<String, String> map = new HashMap<>();
        map.put("oldProp", "http://purl.bdrc.io/ontology/admin/replacedBy");
        map.put("newProp", "http://purl.bdrc.io/ontology/admin/replaceWith");
        map.put("graphs", "");
        map.put("sparql",
                "select distinct ?g ?rep where { graph ?g {?s adm:replacedBy ?o .} ?ad adm:graphId ?g . ?ad adm:gitRepo ?rep .  ?ad adm:adminAbout  ?ab. ?ab rdf:type ?t  . FILTER (?t!=:Role) }");
        map.put("fusekiUrl", "http://buda1.bdrc.io:13180/fuseki/testrw/query");
        String json = new ObjectMapper().writeValueAsString(map);
        System.out.println("Doing REPLACE with request >> " + json);
        StringEntity entity = new StringEntity(json);
        post.setEntity(entity);
        HttpResponse resp = client.execute(post);
        System.out.println("RESP STATUS public resource >> " + resp.getStatusLine());
        assert (resp.getStatusLine().getStatusCode() == 200);
        System.out.println("RESULT >> " + EntityUtils.toString(resp.getEntity()));

    }

    private static void set456Token() throws IOException {
        AuthRequest req = auth.login("tchame@rimay.net", AuthProps.getProperty("tchame@rimay.net"));
        req.setScope("openid offline_access");
        req.setAudience("https://bdrc-io.auth0.com/api/v2/");
        adminToken = req.execute().getIdToken();
        log.info("admin Token >> {}", adminToken);
    }

    public static String getResourceFileContent(String file) throws IOException {
        InputStream stream = CheckUserAPI.class.getClassLoader().getResourceAsStream(file);
        StringWriter writer = new StringWriter();
        IOUtils.copy(stream, writer, "UTF-8");
        return writer.toString();
    }
}
