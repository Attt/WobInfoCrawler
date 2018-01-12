package com.atpexgo.scheduler;

import com.alibaba.fastjson.JSONObject;
import com.atpexgo.services.ForecastService;
import lombok.extern.log4j.Log4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.concurrent.*;

/**
 * the forecast info fetching scheduler
 * Created by atpex on 2018/1/11.
 */
@EnableScheduling
@Component
@Log4j
public class ForecastScheduler {

    private static JSONObject AIRPORTS_CODE;

    private static final ExecutorService FETCHING_TASK_POOL = Executors.newFixedThreadPool(4);

    private static final BlockingQueue<JSONObject> FETCHING_RESULT_QUEUE = new LinkedBlockingDeque<>();

    private final ForecastService forecastService;

    @Autowired
    public ForecastScheduler(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @PostConstruct
    public void init() {
        try {
            AIRPORTS_CODE = JSONObject.parseObject(IOUtils.toString(ForecastScheduler.class.getClassLoader().getResourceAsStream("static/airports.json"), "UTF-8"));
        } catch (IOException e) {
            AIRPORTS_CODE = null;
        }
    }

    /**
     * fetch forecast info every hour
     * 1 sec delayed
     */
    @Retryable(value = {Exception.class}, backoff = @Backoff(delay = 1000L, multiplier = 1))
    @Scheduled(cron = "1 0 0/1 * * ? ")
    public void fetchForecastInfo() throws Exception {
        int airportsCount = AIRPORTS_CODE.values().size();
        CountDownLatch latch = new CountDownLatch(airportsCount);
        AIRPORTS_CODE.forEach((k, v) -> FETCHING_TASK_POOL.submit(() -> {
            JSONObject jsonObject = null;
            try {
                jsonObject = forecastService.getForecastInfo(k, (String) v);
            } catch (Exception e) {
                log.warn("fetch task of airport (" + v + ") failed", e);
            } finally {
                if (jsonObject != null && !jsonObject.isEmpty() && !CollectionUtils.isEmpty(jsonObject.values()) && !((JSONObject) jsonObject.values().toArray()[0]).isEmpty()) {
                    FETCHING_RESULT_QUEUE.offer(jsonObject);
                    if (log.isDebugEnabled())
                        log.info(jsonObject);
                }
                latch.countDown();
            }
        }));

        JSONObject result = new JSONObject();
        latch.await();//wait for all task finished
        for (int i = 0; i < airportsCount; i++) {
            JSONObject jsonObject = FETCHING_RESULT_QUEUE.poll();
            if (jsonObject != null && jsonObject.values().toArray()[0] != null) {
                result.putAll(jsonObject);
            }
        }
        log.info(result);
    }

    public void persistant(JSONObject result){
        // TODO
    }

    @Recover
    public void wenFailed(Exception e) {
        log.warn("fetching forecast info failed", e);
    }

}
