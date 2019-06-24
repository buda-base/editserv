package editservio.bdrc.edit.test;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import io.bdrc.edit.EditConfig;

@RunWith(SpringRunner.class)
public class BasicTest {

    @BeforeClass
    public static void init() {
        EditConfig.init();
    }

    public void sendPutTaskIdRequest() throws ClientProtocolException, IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPut put = new HttpPut("http://localhost:8080/tasks");
        String json = "  {\n" + "    \"id\": \"AAAAAA\",\n" + "    \"saveMsg\": \"Test save message\",\n" + "    \"shortName\": \"Yoga Collection\",\n" + "    \"message\":\"about the task\",\n" + "    \"user\":\"marc\", \n"
                + "    \"patch\":\"here is the latest version of the content of the patch AAAA\" \n" + "    \n" + "  } ";
        StringEntity entity = new StringEntity(json);
        put.setEntity(entity);
        put.setHeader("Accept", "application/json");
        put.setHeader("Content-type", "application/json");
        HttpResponse response = client.execute(put);
        System.out.println(response);
        assert (response.getStatusLine().getStatusCode() == 204);
    }

}
