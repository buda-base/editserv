package editservio.bdrc.edit.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.junit.BeforeClass;
import org.junit.Test;

import io.bdrc.edit.EditConfig;

public class BasicTest {

    @BeforeClass
    public static void init() {
        EditConfig.init();
    }

    @Test
    public void sendRequest() throws ClientProtocolException, IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("http://localhost:8080/graph");
        post.setHeader("Slug", "P1523");
        post.setHeader("Pragma", "Final");
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        List<String> doc = IOUtils.readLines(BasicTest.class.getClassLoader().getResourceAsStream("basic.patch"), StandardCharsets.UTF_8);
        String s = "";
        for (String l : doc) {
            s = s + System.lineSeparator() + l;
        }
        params.add(new BasicNameValuePair("Payload", s));
        post.setEntity(new UrlEncodedFormEntity(params));
        HttpResponse response = client.execute(post);
        System.out.println(response);
        assert (response.getStatusLine().getStatusCode() == 202);
    }

    public static void main(String[] args) throws ClientProtocolException, IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("http://localhost:8080/graph");
        post.setHeader("Slug", "P1523");
        post.setHeader("Pragma", "Final");
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        List<String> doc = IOUtils.readLines(BasicTest.class.getClassLoader().getResourceAsStream("basic.patch"), StandardCharsets.UTF_8);
        String s = "";
        for (String l : doc) {
            s = s + System.lineSeparator() + l;
        }
        params.add(new BasicNameValuePair("Payload", s));
        post.setEntity(new UrlEncodedFormEntity(params));
        HttpResponse response = client.execute(post);
        System.out.println(response);
        assert (response.getStatusLine().getStatusCode() == 202);
    }

}
