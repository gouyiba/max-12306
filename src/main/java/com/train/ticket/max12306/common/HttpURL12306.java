package com.train.ticket.max12306.common;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.train.ticket.max12306.entity.StationInfo;
import com.train.ticket.max12306.entity.TicketInfo;
import com.train.ticket.max12306.entity.TicketPrice;
import com.train.ticket.max12306.enumeration.HttpHeaderParamter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @ClassName HttpURL12306
 * @Author duxiaoyu
 * @Date 2020/7/28 18:14
 * @Version 1.0
 */
public class HttpURL12306 {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpURL12306.class);

    private static final int SUCCESS = 200;

    private static final String JSESSIONID = "687E4871E1E191B0AFEA96C25ED69639";

    private static final String RAIL_EXPIRATION = "1596163502806";

    private static final String RAIL_DEVICEID = "nBVP2rmFnuqc8JeR6Pu4GHIBLxXaGlDg2jP2mTLakZJ-otNv4aV-albpv3H-uFZznksWqnbPgliWn_Cu6sSdm1qOtaPoUi3VCMHaSDqb8nS-ZkDBmwwPiT4jOt9LlMQC-NvnjzHymbsq2cKKqt4nyhqp-2Ac9M5h";

    /**
     * 车站Map
     */
    public static final Map<String, String> STATION_MAP = new HashMap<>();

    /**
     * 本地cookie实例
     */
    private static CookieStore cookieStore;

    /**
     * HttpClientContext上下文
     */
    private static HttpClientContext context;

    /**
     * 解析车站信息
     *
     * @return
     * @throws Exception
     */
    public static List<StationInfo> parseStationInfo() throws Exception {
        try (CloseableHttpClient httpClient = httpClientBuild()) {
            HttpGet httpGet = new HttpGet(HttpURLConstant12306.STATION_INFO_URL);
            try (CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
                HttpEntity httpEntity = httpResponse.getEntity();
                String result = EntityUtils.toString(httpEntity);
                // 释放资源
                EntityUtils.consume(httpEntity);
                List<StationInfo> list = new ArrayList<>();
                if (StringUtils.isNotBlank(result)) {
                    String stationContent = StringUtils.substringBetween(result, "'", "'");
                    String[] stationArray = stationContent.split("@");
                    for (String item : stationArray) {
                        String[] stationDes = item.split("\\|");
                        if (stationDes.length > 1) {
                            StationInfo stationInfo = new StationInfo();
                            stationInfo.setStationName(stationDes[1]);
                            stationInfo.setStationCode(stationDes[2]);
                            stationInfo.setStationSpell(stationDes[3]);
                            stationInfo.setStationLogogram(stationDes[4]);
                            stationInfo.setStationSort(Integer.valueOf(stationDes[5]));
                            STATION_MAP.put(stationInfo.getStationCode(), stationInfo.getStationName());
                            list.add(stationInfo);
                        }
                    }
                    if (!CollectionUtils.isEmpty(list)) {
                        LOGGER.info("======> 解析车站信息成功...");
                        return list;
                    }
                }
                LOGGER.info("======> 解析车站信息失败...");
            }
        }
        return null;
    }


    /**
     * 解析车票信息
     *
     * @param ticketRequest 查询参数
     * @return
     * @throws Exception
     */
    public static List<TicketInfo> parseTicketInfo(QueryTicketRequest ticketRequest) throws Exception {
        if (Objects.nonNull(ticketRequest)) {
            try (CloseableHttpClient httpClient = httpClientBuild()) {
                HttpGet httpGet = new HttpGet(HttpURLConstant12306.TICKET_QUERY_URL.
                        replace("{1}", ticketRequest.getFromDate()).
                        replace("{2}", ticketRequest.getFromStationCode()).
                        replace("{3}", ticketRequest.getToStationCode()).
                        replace("{4}", ticketRequest.getTicketType().getValue()));
                httpGet.addHeader(HttpHeaderParamter.ACCEPT.getKey(), HttpHeaderParamter.ACCEPT.getValue());
                httpGet.addHeader(HttpHeaderParamter.ACCEPT_ENCODING.getKey(), HttpHeaderParamter.ACCEPT_ENCODING.getValue());
                httpGet.addHeader(HttpHeaderParamter.ACCEPT_LANGUAGE.getKey(), HttpHeaderParamter.ACCEPT_LANGUAGE.getValue());
                httpGet.addHeader(HttpHeaderParamter.USER_AGENT.getKey(), HttpHeaderParamter.USER_AGENT.getValue());
                httpGet.addHeader(HttpHeaderParamter.X_REQUESTED_WITH.getKey(), HttpHeaderParamter.X_REQUESTED_WITH.getValue());
                httpGet.addHeader(HttpHeaderParamter.COOKIE.getKey(), HttpHeaderParamter.COOKIE.getValue().
                        replace("{1}", JSESSIONID).
                        replace("{2}", RAIL_EXPIRATION).
                        replace("{3}", RAIL_DEVICEID));
                try (CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
                    HttpEntity httpEntity = httpResponse.getEntity();
                    String result = EntityUtils.toString(httpEntity);
                    // 释放资源
                    EntityUtils.consume(httpEntity);
                    List<TicketInfo> list = new ArrayList<>();
                    if (StringUtils.isNotBlank(result)) {
                        JSONObject jsonObject = JSONUtil.parseObj(result);
                        int status = (int) jsonObject.get("httpstatus");
                        if (status == SUCCESS) {
                            JSONObject data = (JSONObject) jsonObject.get("data");
                            JSONArray ticketArray = (JSONArray) data.get("result");
                            // 车票信息
                            String[] ticketStrArray = ticketArray.toArray(new String[0]);
                            String map = data.get("map").toString();
                            // 设置车票信息
                            list = settingTicketInfo(ticketStrArray);
                            if (!CollectionUtils.isEmpty(list)) {
                                LOGGER.info("======> 解析车票信息成功...");
                                LOGGER.info("======> {} {} - {} 车次总趟数:{}",
                                        ticketRequest.getFromDate(),
                                        STATION_MAP.get(ticketRequest.getFromStationCode()),
                                        STATION_MAP.get(ticketRequest.getToStationCode()), list.size());
                                return list;
                            }
                        }
                    }
                    LOGGER.info("======> 解析车票信息失败...");
                }
            }
        }
        return null;
    }


    /**
     * 解析车票价格信息
     *
     * @param ticketPriceRequest
     * @return
     */
    public static TicketPrice parseTicketPrice(QueryTicketPriceRequest ticketPriceRequest) throws Exception {
        if (Objects.nonNull(ticketPriceRequest)) {
            try (CloseableHttpClient httpClient = httpClientBuild()) {
                HttpGet httpGet = new HttpGet(HttpURLConstant12306.TICKET_PRICE_QUERY_URL.
                        replace("{1}", ticketPriceRequest.getTrainNo()).
                        replace("{2}", ticketPriceRequest.getFromStationNo()).
                        replace("{3}", ticketPriceRequest.getToStationNo()).
                        replace("{4}", ticketPriceRequest.getSeatTypes()).
                        replace("{5}", ticketPriceRequest.getTrainDate()));
                httpGet.addHeader(HttpHeaderParamter.ACCEPT.getKey(), HttpHeaderParamter.ACCEPT.getValue());
                httpGet.addHeader(HttpHeaderParamter.ACCEPT_ENCODING.getKey(), HttpHeaderParamter.ACCEPT_ENCODING.getValue());
                httpGet.addHeader(HttpHeaderParamter.ACCEPT_LANGUAGE.getKey(), HttpHeaderParamter.ACCEPT_LANGUAGE.getValue());
                httpGet.addHeader(HttpHeaderParamter.USER_AGENT.getKey(), HttpHeaderParamter.USER_AGENT.getValue());
                httpGet.addHeader(HttpHeaderParamter.X_REQUESTED_WITH.getKey(), HttpHeaderParamter.X_REQUESTED_WITH.getValue());
                httpGet.addHeader(HttpHeaderParamter.COOKIE.getKey(), HttpHeaderParamter.COOKIE.getValue().
                        replace("{1}", JSESSIONID).
                        replace("{2}", RAIL_EXPIRATION).
                        replace("{3}", RAIL_DEVICEID));
                try (CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
                    HttpEntity httpEntity = httpResponse.getEntity();
                    String result = EntityUtils.toString(httpEntity);
                    // 释放资源
                    EntityUtils.consume(httpEntity);
                    TicketPrice ticketPrice = new TicketPrice();
                    ticketPrice.setTrainCode(ticketPriceRequest.getTrainCode());
                    ticketPrice.setTrainNo(ticketPriceRequest.getTrainNo());
                    if (StringUtils.isNotBlank(result)) {
                        JSONObject jsonObject = JSONUtil.parseObj(result);
                        int status = (int) jsonObject.get("httpstatus");
                        if (status == SUCCESS) {
                            JSONObject data = (JSONObject) jsonObject.get("data");
                            // 设置车票价格信息
                            settingTicketPrice(data, ticketPriceRequest, ticketPrice);
                            LOGGER.info("======> 解析车票价格成功...");
                            return ticketPrice;
                        }
                    }
                    LOGGER.info("======> 解析车票价格失败...");
                }
            }
        }
        return null;
    }


    /**
     * 获取登录图片验证码
     *
     * @return
     */
    public static String getImgCaptcha() throws Exception {
        try (CloseableHttpClient httpClient = httpClientBuild()) {
            HttpGet httpGet = httpGetBuild(HttpURLConstant12306.GET_CAPTCHA.replace("{1}", String.valueOf(System.currentTimeMillis())));
            try (CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
                HttpEntity entity = httpResponse.getEntity();
                String result = EntityUtils.toString(entity);
                // 释放资源
                EntityUtils.consume(entity);
                if (StringUtils.isNotBlank(result)) {
                    // 验证码json
                    String jsonResult = StringUtils.substringBetween(result, "(", ")");
                    // 验证码回调函数
                    String jqueryCallBack=StringUtils.substringBetween(result, "/**/", "(");
                    JSONObject json = JSONUtil.parseObj(jsonResult);
                    if (json.get("result_code", String.class).equals("0")) {
                        String img = json.get("image", String.class);
                        LOGGER.info("======> 图片验证码获取成功...");
                        return jqueryCallBack+"--"+img;
                    } else {
                        LOGGER.info("======> 图片验证码获取失败...");
                    }
                }
            }
        }
        return null;
    }


    /**
     * 图片验证码校验
     * @param answer
     * @return
     */
    public static String checkImgCapthcha(String answer) throws Exception{
        try (CloseableHttpClient httpClient = httpClientBuild()) {
            HttpGet httpGet = httpGetBuild(HttpURLConstant12306.CHECK_CAPTCHA.replace("{xyz}", answer));
            try (CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
                HttpEntity entity = httpResponse.getEntity();
                String result = EntityUtils.toString(entity);
                // 释放资源
                EntityUtils.consume(entity);
                if (StringUtils.isNotBlank(result)) {
                    JSONObject json = JSONUtil.parseObj(result);
                    if (json.get("result_code", String.class).equals("4")) {
                        String resultCode="4";
                        LOGGER.info("======> 图片验证码校验成功...");
                        return resultCode;
                    } else {
                        LOGGER.info("======> 图片验证码校验失败...");
                    }
                }
            }
        }
        return "5";
    }

    /**
     * 设置车票信息
     *
     * @param ticketStrArray
     * @return
     */
    private static List<TicketInfo> settingTicketInfo(String[] ticketStrArray) {
        List<TicketInfo> list = new ArrayList<>();
        for (String ticket : ticketStrArray) {
            String[] ticketDes = ticket.split("\\|");
            TicketInfo ticketInfo = new TicketInfo();
            ticketInfo.setTicketSecretKey(ticketDes[0]);
            ticketInfo.setRemark(ticketDes[1]);
            ticketInfo.setTrainNo(ticketDes[2]);
            ticketInfo.setTrainCode(ticketDes[3]);
            ticketInfo.setStartStationCode(ticketDes[4]);
            ticketInfo.setEndStationCode(ticketDes[5]);
            ticketInfo.setFromStationCode(ticketDes[6]);
            ticketInfo.setFromeStationName(STATION_MAP.get(ticketDes[6]));
            ticketInfo.setToStationCode(ticketDes[7]);
            ticketInfo.setToStationMame(STATION_MAP.get(ticketDes[7]));
            ticketInfo.setFromTime(ticketDes[8]);
            ticketInfo.setToTime(ticketDes[9]);
            ticketInfo.setLastTime(ticketDes[10]);
            ticketInfo.setCanBuy(ticketDes[11]);
            ticketInfo.setStartDate(ticketDes[13]);
            ticketInfo.setTrainLocation(ticketDes[15]);
            ticketInfo.setFromStationNo(ticketDes[16]);
            ticketInfo.setToStationNo(ticketDes[17]);
            ticketInfo.setIsSupportCard(ticketDes[18]);
            ticketInfo.setHighSoftSleepCount(ticketDes[21]);
            ticketInfo.setOther(ticketDes[22]);
            ticketInfo.setSoftSleepCount(ticketDes[23]);
            ticketInfo.setSoftSeatCount(ticketDes[24]);
            ticketInfo.setSpecialSeatCount(ticketDes[25]);
            ticketInfo.setNoneSeatCount(ticketDes[26]);
            ticketInfo.setYbCount(ticketDes[27]);
            ticketInfo.setHardSleepCount(ticketDes[28]);
            ticketInfo.setHardSeatCount(ticketDes[29]);
            ticketInfo.setSecondSeatCount(ticketDes[30]);
            ticketInfo.setFirstSeatCount(ticketDes[31]);
            ticketInfo.setBusinessSeatCount(ticketDes[32]);
            ticketInfo.setMotorSleepCount(ticketDes[33]);
            ticketInfo.setSeatType(ticketDes[35]);
            ticketInfo.setCanBackup(ticketDes[37]);
            list.add(ticketInfo);
        }
        return list;
    }

    /**
     * 设置车票价格信息
     *
     * @param data
     * @param ticketPriceRequest
     * @return
     */
    public static void settingTicketPrice(JSONObject data, QueryTicketPriceRequest ticketPriceRequest, TicketPrice ticketPrice) {
        switch (ticketPriceRequest.getTrainCode().toCharArray()[0]) {
            case 'G':
                ticketPrice.setA9(data.get("A9", String.class));
                ticketPrice.setM(data.get("M", String.class));
                ticketPrice.setWZ(data.get("WZ", String.class));
                ticketPrice.setO(data.get("O", String.class));

                ticketPrice.setBusinessSeatPrice(ticketPrice.getA9());
                ticketPrice.setFirstSeatPrice(ticketPrice.getM());
                ticketPrice.setSecondSeatPrice(ticketPrice.getWZ());
                ticketPrice.setNoneSeatPrice(ticketPrice.getO());
                break;
            case 'D':
                ticketPrice.setM(data.get("M", String.class));
                ticketPrice.setWZ(data.get("WZ", String.class));
                ticketPrice.setAI(data.get("AI", String.class));
                ticketPrice.setAJ(data.get("AJ", String.class));
                ticketPrice.setO(data.get("O", String.class));

                ticketPrice.setFirstSeatPrice(ticketPrice.getM());
                ticketPrice.setSecondSeatPrice(ticketPrice.getWZ());
                ticketPrice.setSoftSleepPrice(ticketPrice.getAI());
                ticketPrice.setHardSleepPrice(ticketPrice.getAJ());
                ticketPrice.setNoneSeatPrice(ticketPrice.getO());
                break;
            case 'Z':
                ticketPrice.setA6(data.get("A6", String.class));
                ticketPrice.setA4(data.get("A4", String.class));
                ticketPrice.setA3(data.get("A3", String.class));
                ticketPrice.setA1(data.get("A1", String.class));
                ticketPrice.setWZ(data.get("WZ", String.class));

                ticketPrice.setHighSoftSleepPrice(ticketPrice.getA6());
                ticketPrice.setSoftSleepPrice(ticketPrice.getA4());
                ticketPrice.setHardSleepPrice(ticketPrice.getA3());
                ticketPrice.setHardSeatPrice(ticketPrice.getA1());
                ticketPrice.setNoneSeatPrice(ticketPrice.getWZ());
                break;
            case 'T':
                ticketPrice.setA4(data.get("A4", String.class));
                ticketPrice.setA3(data.get("A3", String.class));
                ticketPrice.setA1(data.get("A1", String.class));
                ticketPrice.setWZ(data.get("WZ", String.class));

                ticketPrice.setSoftSleepPrice(ticketPrice.getA4());
                ticketPrice.setHardSleepPrice(ticketPrice.getA3());
                ticketPrice.setHardSeatPrice(ticketPrice.getA1());
                ticketPrice.setNoneSeatPrice(ticketPrice.getWZ());
                break;
            case 'K':
                ticketPrice.setA4(data.get("A4", String.class));
                ticketPrice.setA3(data.get("A3", String.class));
                ticketPrice.setA1(data.get("A1", String.class));
                ticketPrice.setWZ(data.get("WZ", String.class));

                ticketPrice.setSoftSleepPrice(ticketPrice.getA4());
                ticketPrice.setHardSleepPrice(ticketPrice.getA3());
                ticketPrice.setHardSeatPrice(ticketPrice.getA1());
                ticketPrice.setNoneSeatPrice(ticketPrice.getWZ());
                break;
            default:
                ticketPrice.setA4(data.get("A4", String.class));
                ticketPrice.setA3(data.get("A3", String.class));
                ticketPrice.setA1(data.get("A1", String.class));
                ticketPrice.setWZ(data.get("WZ", String.class));

                ticketPrice.setSoftSleepPrice(ticketPrice.getA4());
                ticketPrice.setHardSleepPrice(ticketPrice.getA3());
                ticketPrice.setHardSeatPrice(ticketPrice.getA1());
                ticketPrice.setNoneSeatPrice(ticketPrice.getWZ());
                break;
        }
    }


    /**
     * 创建HttpClient
     *
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    private static CloseableHttpClient httpClientBuild() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        // 创建SSL安全认证
        SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(null, (chain, authType) -> true).build();
        SSLConnectionSocketFactory sslSf = new SSLConnectionSocketFactory(sslcontext, null, null, new NoopHostnameVerifier());
        // 创建cookieStore本地实例
        RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build();
        cookieStore = new BasicCookieStore();
        context = HttpClientContext.create();
        context.setCookieStore(cookieStore);
        return HttpClients.custom().setSSLSocketFactory(sslSf).setDefaultRequestConfig(globalConfig).setDefaultCookieStore(cookieStore).build();
    }

    /**
     * 创建HttpGet
     *
     * @param url
     * @return
     */
    public static HttpGet httpGetBuild(String url) {
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader(HttpHeaderParamter.ACCEPT.getKey(), HttpHeaderParamter.ACCEPT.getValue());
        httpGet.addHeader(HttpHeaderParamter.ACCEPT_ENCODING.getKey(), HttpHeaderParamter.ACCEPT_ENCODING.getValue());
        httpGet.addHeader(HttpHeaderParamter.ACCEPT_LANGUAGE.getKey(), HttpHeaderParamter.ACCEPT_LANGUAGE.getValue());
        httpGet.addHeader(HttpHeaderParamter.USER_AGENT.getKey(), HttpHeaderParamter.USER_AGENT.getValue());
        httpGet.addHeader(HttpHeaderParamter.X_REQUESTED_WITH.getKey(), HttpHeaderParamter.X_REQUESTED_WITH.getValue());
        httpGet.addHeader(HttpHeaderParamter.COOKIE.getKey(), HttpHeaderParamter.COOKIE.getValue().
                replace("{1}", JSESSIONID).
                replace("{2}", RAIL_EXPIRATION).
                replace("{3}", RAIL_DEVICEID));
        return httpGet;
    }

    /**
     * 车站去重
     *
     * @param keyExtractor
     * @param <T>
     * @return
     */
    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }
}
