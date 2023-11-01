package cn.huihuo.jmeter;

import org.apache.jmeter.samplers.SampleResult;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HttpBackendListener extends AbstractBackendListenerClient {

    private static final Logger log = LoggerFactory.getLogger(HttpBackendListener.class);

    private static String SERVER_API;

    private static String TEST_NAME;

    private static String TEST_ENV;

    private TestSummary testSummary;

    private ArrayList<TestCaseInfo> testCases;

    private Integer countSuccess;

    private List<String> scenarioList;


    @Override
    public void setupTest(BackendListenerContext context) {
        log.info(" ---- Test Start ---- ");
        HttpBackendListener.SERVER_API = context.getParameter("host");
        HttpBackendListener.TEST_NAME = context.getParameter("name");
        HttpBackendListener.TEST_ENV = context.getParameter("env");

        this.scenarioList = new ArrayList<>();
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
    public void teardownTest(BackendListenerContext context) {
        this.testSummary.setEndTime(System.currentTimeMillis());
        this.testSummary.setDuration((this.testSummary.getEndTime() - this.testSummary.getStartTime()) / 1000);

        SendReqData sendReqData = new SendReqData();
        sendReqData.setTestSummary(this.testSummary);
        sendReqData.setTestCases(this.testCases);

        // 发送
        this.sendHttp(sendReqData);
        log.info(" ---- Test End ---- ");
    }


    public void handlerResult(SampleResult sampleResult) {
        log.info("当前请求：".concat(sampleResult.getSampleLabel()));
        Class<? extends SampleResult> aClass = sampleResult.getClass();
        if (!aClass.getName().contains("http.sampler")) {
            log.info("非http请求：" + sampleResult.getSampleLabel());
            this.scenarioList.add(sampleResult.getSampleLabel());
            SampleResult[] subResults = sampleResult.getSubResults();
            if (subResults.length != 0) {
                for (SampleResult result : subResults) {
                    handlerResult(result);
                }
                this.scenarioList.remove(this.scenarioList.size() - 1);
            } else {
                log.info("非事务控制器：" + sampleResult.getSampleLabel());
            }

        } else {
            log.info("scenario list: ".concat(this.scenarioList.toString()));

            TestCaseInfo tc = new TestCaseInfo();
            HTTPSampleResult httpSampleResult = (HTTPSampleResult) sampleResult;

            if (this.scenarioList.size() != 0) {
                // deep 获取当前节点以上的控制器名称
                String scenarioName = String.join("-", this.scenarioList);
                tc.setScenarioName(scenarioName);
            }

            String threadName = httpSampleResult.getThreadName();
            // 订单中心 1-1 去除线程编号
            String[] splitName = threadName.split(" ");
            ArrayList<String> nameList = new ArrayList<>(Arrays.asList(splitName));
            nameList.remove(nameList.size() - 1);
            String newName = String.join("", nameList);
            // 上下文类型的测试覆盖场景名称
            if (newName.contains("|")) {
                String[] names = newName.split("\\|");
                tc.setModuleName(names[0].trim());
                tc.setScenarioName(names[1].trim());
            } else {
                tc.setModuleName(newName);
            }

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

            tc.setSuccess(true);
            if (!(tc.getResponseCode().startsWith("2") || tc.getResponseCode().startsWith("3"))) {
                tc.setSuccess(false);
            }
            AssertionResult[] assertionResults = httpSampleResult.getAssertionResults();
            StringBuilder sb = new StringBuilder();
            for (AssertionResult assertionResult : assertionResults) {
                if (assertionResult.isFailure()) {
                    tc.setSuccess(false);
                    sb.append(assertionResult.getName()).append(": ").append(assertionResult.getFailureMessage()).append("\n");
                }
            }
            if (tc.getSuccess()) {
//                tc.setSuccess(true);
                this.countSuccess += 1;
            }
            tc.setFailMessage(sb.toString());

            this.testCases.add(tc);
        }
    }


    @Override
    public void handleSampleResults(List<SampleResult> sampleResults, BackendListenerContext backendListenerContext) {
        for (SampleResult sampleResult : sampleResults) {
            handlerResult(sampleResult);
            this.scenarioList.clear();
        }

        this.testSummary.setTotal(this.testCases.size());
        this.testSummary.setSuccess(this.countSuccess);
        this.testSummary.setFail(this.testCases.size() - this.countSuccess);
        if (this.testSummary.getTotal() == 0) {
            this.testSummary.setPassRate((double) 0);
        } else {
            double i = (double) this.testSummary.getSuccess() / this.testSummary.getTotal();
            BigDecimal bd = new BigDecimal(i);
            double rate = bd.setScale(4, RoundingMode.HALF_UP).doubleValue();
            this.testSummary.setPassRate(rate);
        }
        this.testSummary.setResult(this.testSummary.getFail() == 0);
    }

    private void sendHttp(SendReqData sendReqData) {

        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

        HttpResponse<JsonNode> response = null;

        try {
            response = Unirest.post(HttpBackendListener.SERVER_API.concat("/api/result/save"))
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(sendReqData))
                    .asJson();

            log.info("数据发送成功：".concat(response.getBody().toString()));

        } catch (UnirestException e) {
            log.error("数据发送异常：".concat(e.getMessage()));
        }
    }

    public Arguments getDefaultParameters() {
        Arguments arguments = new Arguments();
        arguments.addArgument("host", "数据收集服务域名如：http://127.0.0.1:8080");
        arguments.addArgument("name", "被测项目名称");
        arguments.addArgument("env", "被测环境名称");
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

