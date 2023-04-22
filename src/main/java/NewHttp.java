import cn.huihuo.jmeter.TestSummary;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.util.ArrayList;

public class NewHttp {
    public static void main(String[] args) {

        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        TestSummary testSummary = new TestSummary();
        testSummary.setProject("test");

        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.post("http://1.116.137.209:8000/api/test")
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(testSummary))
                    .asJson();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }

        System.out.println(response.getBody());


        ArrayList<String> strings = new ArrayList<>();

        strings.add("123");
        strings.add("123");

        System.out.println(String.join("-", strings.subList(0, 2)));
    }
}
