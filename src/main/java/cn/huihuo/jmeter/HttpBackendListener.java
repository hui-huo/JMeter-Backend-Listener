package cn.huihuo.jmeter;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class HttpBackendListener extends AbstractBackendListenerClient {

    private static final Logger log = LoggerFactory.getLogger(HttpBackendListener.class);

    private static String SERVER_API;

    private static String TEST_NAME;

    private static String TEST_ENV;

    private TestSummary testSummary;

    private ArrayList<TestCaseInfo> testCases;

    private Integer countSuccess;

    @Override
    public void setupTest(BackendListenerContext context) throws Exception {
        log.info(" ---- Test Start ---- ");
        HttpBackendListener.SERVER_API = context.getParameter("server");
        HttpBackendListener.TEST_NAME = context.getParameter("name");
        HttpBackendListener.TEST_ENV = context.getParameter("env");

        this.testCases = new ArrayList<>();
        this.testSummary = new TestSummary();
        this.countSuccess = 0;

        this.testSummary.setProject(HttpBackendListener.TEST_NAME);
        this.testSummary.setEnv(HttpBackendListener.TEST_ENV);

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win") || os.contains("mac")) {
            // 手动构建
            this.testSummary.setType(1);
        } else {
            // 自动构建
            this.testSummary.setType(0);
        }
        this.testSummary.setStartTime(System.currentTimeMillis());
    }

    @Override
    public void teardownTest(BackendListenerContext context) throws Exception {
        this.testSummary.setEndTime(System.currentTimeMillis());
        this.testSummary.setDuration((this.testSummary.getEndTime() - this.testSummary.getStartTime()) / 1000);

        SendReqData sendReqData = new SendReqData();
        sendReqData.setTestSummary(this.testSummary);
        sendReqData.setTestCases(this.testCases);

        // 发送
        this.sendHttp(sendReqData);
        log.info(" ---- Test End ---- ");
    }

    @Override
    public void handleSampleResults(List<SampleResult> sampleResults, BackendListenerContext backendListenerContext) {

        for (SampleResult sampleResult : sampleResults) {
            TestCaseInfo tc = new TestCaseInfo();
            HTTPSampleResult httpSampleResult = (HTTPSampleResult) sampleResult;

            tc.setModuleName(httpSampleResult.getThreadName().split(" ")[0]);
            tc.setCaseName(httpSampleResult.getSampleLabel());
            tc.setStartTime(httpSampleResult.getStartTime());
            tc.setEndTime(httpSampleResult.getEndTime());

            tc.setRequest_url(httpSampleResult.getUrlAsString());
            tc.setRequestHeader(httpSampleResult.getRequestHeaders());
            tc.setRequestBody(httpSampleResult.getSamplerData());
            tc.setRequestMethod(httpSampleResult.getHTTPMethod());

            tc.setResponseCode(httpSampleResult.getResponseCode());
            tc.setResponseHeader(httpSampleResult.getResponseHeaders());
            tc.setResponseBody(httpSampleResult.getResponseDataAsString());

            tc.setTestResult(true);
            if (!(tc.getResponseCode().startsWith("2") || tc.getResponseCode().startsWith("3"))) {
                tc.setTestResult(false);
            }
            AssertionResult[] assertionResults = httpSampleResult.getAssertionResults();
            StringBuilder sb = new StringBuilder();
            for (AssertionResult assertionResult : assertionResults) {
                if (assertionResult.isFailure()) {
                    tc.setTestResult(false);
                    sb.append(assertionResult.getName()).append(": ").append(assertionResult.getFailureMessage()).append("\n");
                }
            }
            if (tc.getTestResult()) {
                tc.setTestResult(true);
                this.countSuccess += 1;
            }
            tc.setFailMessage(sb.toString());

            this.testCases.add(tc);
        }


        this.testSummary.setTotal(this.testCases.size());
        this.testSummary.setSuccess(this.countSuccess);
        this.testSummary.setFail(this.testCases.size() - this.countSuccess);
        if (this.testSummary.getTotal() == 0) {
            this.testSummary.setPassRate((double) 0);
        } else {
            double i = (double) this.testSummary.getSuccess() / this.testSummary.getTotal();
            BigDecimal bd = new BigDecimal(i);
            double rate = bd.setScale(2, RoundingMode.HALF_UP).doubleValue();
            this.testSummary.setPassRate(rate);
        }
        this.testSummary.setResult(this.testSummary.getFail() == 0);
    }

    private void sendHttp(SendReqData sendReqData) {
        OkHttpClient client = new OkHttpClient();

        // 构建请求体
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        String json = gson.toJson(sendReqData);
        RequestBody requestBody = RequestBody.create(JSON, json);

        // 构建请求
        Request request = new Request.Builder().url(HttpBackendListener.SERVER_API.concat("/api/save_results")).post(requestBody).build();

        // 发送请求并获取响应
        try {
            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();
            if (response.isSuccessful()) {
                log.info("Send success：" + responseBody);
            } else {
                log.info("Send fail：" + responseBody);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Arguments getDefaultParameters() {
        Arguments arguments = new Arguments();
        arguments.addArgument("server", "server url");
        arguments.addArgument("name", "项目名称");
        arguments.addArgument("env", "环境名称");
        return arguments;
    }
}

class SendReqData {
    private TestSummary testSummary;
    private ArrayList<TestCaseInfo> testCases;

    public TestSummary getTestSummary() {
        return testSummary;
    }

    public void setTestSummary(TestSummary testSummary) {
        this.testSummary = testSummary;
    }

    public ArrayList<TestCaseInfo> getTestCases() {
        return testCases;
    }

    public void setTestCases(ArrayList<TestCaseInfo> testCases) {
        this.testCases = testCases;
    }

}
