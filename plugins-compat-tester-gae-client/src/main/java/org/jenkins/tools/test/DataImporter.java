package org.jenkins.tools.test;

import com.google.common.io.Files;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.jenkins.tools.test.model.PluginCompatReport;
import org.jenkins.tools.test.model.PluginCompatResult;
import org.jenkins.tools.test.model.PluginInfos;

import javax.jnlp.FileSaveService;
import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author fcamblor
 */
public class DataImporter {
    public static void main(String[] args) throws IOException {
        File reportFile = new File(args[0]);
        Long startingOffset = args.length>3?Long.valueOf(args[3]):Long.valueOf(0);
        new DataImporter(args[1], args[2]).importExistingReport(reportFile, startingOffset);
    }

    private static final Pattern ID_EXTRACTOR = Pattern.compile("id=(.+)");

    private String baseGAEUrl;
    private String securityToken;
    public DataImporter(String baseGAEUrl, String securityToken){
        this.baseGAEUrl = baseGAEUrl;
        this.securityToken = securityToken;
    }

    public String importPluginCompatResult(PluginCompatResult pluginCompatResult, PluginInfos pluginInfos, File logsBaseDir) throws IOException {
        HttpClient httpClient = new DefaultHttpClient();
        String url = baseGAEUrl+"/writePctResult";

        HttpPost method = new HttpPost(url);
        List <NameValuePair> nvps = new ArrayList <NameValuePair>();
        nvps.add(new BasicNameValuePair("token", securityToken));
        nvps.add(new BasicNameValuePair("pluginName", pluginInfos.pluginName));
        nvps.add(new BasicNameValuePair("pluginVersion", pluginInfos.pluginVersion));
        nvps.add(new BasicNameValuePair("pluginUrl", pluginInfos.pluginUrl));
        nvps.add(new BasicNameValuePair("mavenGAV", pluginCompatResult.coreCoordinates.toGAV()));
        nvps.add(new BasicNameValuePair("status", pluginCompatResult.status.name()));
        if(pluginCompatResult.compatTestExecutedOn != null){
            nvps.add(new BasicNameValuePair("timestamp", String.valueOf(pluginCompatResult.compatTestExecutedOn.getTime())));
        }
        if(pluginCompatResult.errorMessage != null){
            nvps.add(new BasicNameValuePair("errMsg", pluginCompatResult.errorMessage));
        }
        if(pluginCompatResult.warningMessages != null){
            for(String warnMsg : pluginCompatResult.warningMessages){
                nvps.add(new BasicNameValuePair("warnMsgs", warnMsg));
            }
        }
        if(pluginCompatResult.getBuildLogPath() != null && !"".equals(pluginCompatResult.getBuildLogPath())){
            nvps.add(new BasicNameValuePair("buildLogPath", pluginCompatResult.getBuildLogPath()));
            String logContent = Files.toString(new File(logsBaseDir.getAbsolutePath()+File.separator+pluginCompatResult.getBuildLogPath()), Charset.forName("UTF-8"));
            nvps.add(new BasicNameValuePair("logContent", logContent));
        }
        
        method.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

        HttpResponse res = httpClient.execute(method);
        InputStream is = res.getEntity().getContent();
        StringWriter sw = new StringWriter();
        char[] buffer = new char[1024];
        try {
            Reader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            int n;
            while((n = r.read(buffer)) != -1){
                sw.write(buffer, 0, n);
            }
        }finally{
            is.close();
        }
        String responseBody = sw.toString();
        if(res.getStatusLine().getStatusCode() != 200){
            throw new IllegalStateException("Error while importing data : "+responseBody);
        }

        Matcher responseMatcher = ID_EXTRACTOR.matcher(responseBody);
        String key = null;
        if(responseMatcher.matches()){
            key = responseMatcher.group(1);
        }
        return key;
    }

    public void importExistingReport(File reportFile, Long startingOffset) throws IOException {
        PluginCompatReport report = PluginCompatReport.fromXml(reportFile);

        int plannedRequestsCount = 0;
        for (Map.Entry<PluginInfos, List<PluginCompatResult>> test : report.getPluginCompatTests().entrySet()){
            plannedRequestsCount += test.getValue().size();
        }

        long i = 0;
        for (Map.Entry<PluginInfos, List<PluginCompatResult>> test : report.getPluginCompatTests().entrySet()){
            for (PluginCompatResult pluginCompatResult : test.getValue()) {
                if(i >= startingOffset.longValue()){
                    importPluginCompatResult(pluginCompatResult, test.getKey(), reportFile.getParentFile());
                    i++;
                    System.out.println(String.format("Executed request %d / %d", i, plannedRequestsCount));
                }
            }
        }
    }
}
