package editservio.bdrc.edit.test;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import io.bdrc.edit.EditApplication;
import io.bdrc.edit.EditConfig;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = EditApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BasicTest {

    @Autowired
    Environment environment;

    @BeforeClass
    public static void init() {
        EditConfig.init();

    }

    @Test
    public void sendPutTaskIdRequest() throws ClientProtocolException, IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpGet get = new HttpGet("http://localhost:" + environment.getProperty("local.server.port") + "/ping");
        HttpResponse response = client.execute(get);
        System.out.println(response);
        assert (response.getStatusLine().getStatusCode() == 200);
    }

}
