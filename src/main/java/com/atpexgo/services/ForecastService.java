package com.atpexgo.services;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.atpexgo.utils.DecryptionUtil;
import com.atpexgo.utils.HttpClientConnectionMonitorThread;
import lombok.extern.log4j.Log4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * forecast info service
 * Created by atpex on 2018/1/11.
 */
@Service
@Log4j
public class ForecastService {

    /**
     * the user agent header of the newest version of Fcz App
     */
    private static final String USER_AGENT = "android-async-http/1.4.5 (http://loopj.com/android-async-http)";

    /**
     * common http client instance
     */
    private static CloseableHttpClient httpClient;

    /**
     * URL
     */
    private static final String URL = "http://api.cdmcaac.com/airport/wob/index";

    private static final int WOB = 2;

    private static final int TAF = 1;

    @PostConstruct
    public void init() {
        try {
            // support https connections
            SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(null,new TrustSelfSignedStrategy()).build();
            HostnameVerifier hostnameVerifier = SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslcontext, hostnameVerifier);
            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslConnectionSocketFactory)
                    .build();
            // initial connection manager
            PoolingHttpClientConnectionManager poolConnManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            // Increase max total connection to 200
            poolConnManager.setMaxTotal(200);
            // Increase default max connection per route to 20
            poolConnManager.setDefaultMaxPerRoute(20);

            SocketConfig.Builder socketConfigBuilder = SocketConfig.custom().setSoTimeout(3000);
            poolConnManager.setDefaultSocketConfig(socketConfigBuilder.build());

            // start to run connection monitor
            new HttpClientConnectionMonitorThread(poolConnManager);

            httpClient = HttpClientBuilder.create().setConnectionManager(poolConnManager).setUserAgent(USER_AGENT).build();

        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            log.warn("gg", e);
        }
    }

    /**
     * get forecast info by the airport id
     *
     * @param id   the airport id
     * @param name the airport name
     * @return metar data & taf data
     * @throws Exception bla bla
     */
    public JSONObject getForecastInfo(String id, String name) throws Exception {
        JSONObject result = new JSONObject();
        JSONObject wobAndTaf = new JSONObject();
        wobAndTaf.put("WOB", getWobOrTaf(id, WOB));
        wobAndTaf.put("TAF", getWobOrTaf(id, TAF));
        wobAndTaf.put("DESC", name);
        result.put(id, wobAndTaf);
        return result;
    }

    /**
     * get specific wob info
     *
     * @param id   the airport id
     * @param type wob type
     * @return metar data & taf data
     * @throws Exception bla bla
     */
    private JSONArray getWobOrTaf(String id, int type) throws Exception {
        HttpGet httpGet = new HttpGet(URL + generateParam(id, "1", type));
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(3000)
                .setConnectTimeout(3000)
                .setSocketTimeout(3000)
                .build();
        httpGet.setConfig(requestConfig);
        HttpClientContext clientContext = HttpClientContext.create();
        try (CloseableHttpResponse response = httpClient.execute(httpGet, clientContext)) {
            String responseString = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
            JSONObject originalResponse = JSONObject.parseObject(responseString);
            JSONObject appResult = DecryptionUtil.decrypt(originalResponse.getString("data"), originalResponse.getString("iv"), originalResponse.getString("key"));
            if (appResult.containsKey("data")) {
                return appResult.getJSONArray("data");
            } else {
                throw new Exception("gg");
            }
        }
    }

    /**
     * generate request param string start with '?' and concat with '&'
     *
     * @param id      the airport id
     * @param pageNum the page number
     * @param type    the wob type
     * @return request param string
     */
    private static String generateParam(String id, String pageNum, int type) {
        Map<String, String> map = new HashMap<>();
        map.put("uid", "1504872");
        map.put("airport", id);
        map.put("type", String.valueOf(type));

        Set<Map.Entry<String, String>> set = map.entrySet();
        List<Map.Entry<String, String>> list = new ArrayList<>(set);
        list.sort(Comparator.comparing(Map.Entry::getKey));
        String p = "qw46CuXjPC!utUGwK";
        for (Map.Entry<String, String> next : list) {
            p = p + "&" + next.getKey() + "=" + next.getValue();
        }
        map.put("key", DecryptionUtil.md5(p));
        map.put("device_info", "SM-G955F4.4.2");
        map.put("version", "2.2.7");
        map.put("device", "1");
        map.put("page", pageNum);
        map.put("device_token", "cceadbbc33b2f84090b6a9092761eab9");
        map.put("device_id", "0ec3e8e726a7af4a8a5b8ceb7d993dbe");

        StringBuilder stringBuilder = new StringBuilder("?");
        map.forEach((k, v) -> stringBuilder.append("&").append(k).append("=").append(v));
        return stringBuilder.toString().replace("?&", "?");
    }
}
