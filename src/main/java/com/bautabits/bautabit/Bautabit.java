package com.bautabits.bautabit;

import com.bautabits.bautabit.dto.PinConfiguration;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.SerializationConfig;
import com.bautabits.bautabit.dto.BautabitInfo;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Bautabit {

    public static final int DEFAULT_PORT = 5000;

    public static final String DNS_SD_SERVICE_TYPE = "_hombit-http._tcp";
    public static final String DNS_SD_DOMAIN = "local.";
    public static final int DISCOVERY_TIMEOUT = 10000;
    public static final int DISCOVERY_SLEEP = 100;

    Client client;
    WebResource resource;

    public Bautabit(String url) {
        url = url.trim();
        if (!url.startsWith("http://"))  {
            url = "http://" + url;
            if (!url.matches(".*?:\\d+"))
                url = url + ":" + DEFAULT_PORT;
        }
        ClientConfig clientConfig = new DefaultClientConfig();
        clientConfig.getSingletons().add(new JacksonJaxbJsonProvider()
                .configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false));
        clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
        client = Client.create(clientConfig);
        resource = client.resource(url);
    }

    public static List<Bautabit> discover() throws IOException, InterruptedException {
        List<String> urls = discoverUrls();
        ArrayList<Bautabit> bautabits = new ArrayList<Bautabit>();
        for (String url : urls)
            bautabits.add(new Bautabit(url));
        return bautabits;
    }

    public static List<String> discoverUrls() throws IOException, InterruptedException {
        ArrayList<String> urls = new ArrayList<String>();
        String serviceType = DNS_SD_SERVICE_TYPE + "." + DNS_SD_DOMAIN;
        JmDNS bonjourService = JmDNS.create();
        ServiceInfo[] serviceInfos = bonjourService.list(serviceType);
        if (serviceInfos.length > 0) {
            bonjourService.close();
            urls.add(serviceInfos[0].getURLs()[0]);
            //return serviceInfos[0].getURLs()[0];//"http://" + serviceInfos[0].getInet4Addresses()[0] + ":" + serviceInfos[0].getPort();
        }
        bonjourService.close();
        return urls;
    }

    public BautabitInfo fetchInfo() {
        return resource.path("info").get(BautabitInfo.class);
    }

    public void setPin(int pinNumber) {
        resource.path("io").path("pin").path(Integer.toString(pinNumber)).put();
    }

    public void clearPin(int pinNumber) {
        resource.path("io").path("pin").path(Integer.toString(pinNumber)).delete();
    }

    public void setPin(String pinName) {
        resource.path("io").path("name").path(pinName).put();
    }

    public void setPins(Map<String, Boolean> pinNameValues) throws IOException {
        resource.path("io").path("name").header("Content-type", "application/json").put(pinNameValues);
    }

    public void clearPin(String pinName) {
        resource.path("io").path("name").path(pinName).delete();
    }

    public void configurePin(int pinNumber, PinConfiguration configuration) {
        resource.path("io").path(Integer.toString(pinNumber)).header("Content-type", "application/json").put(configuration);
    }

    public void configurePin(String pinName, PinConfiguration configuration) {
        resource.path("io").path("name").path(pinName).header("Content-type", "application/json").put(configuration);
    }

    public void configurePins(Map<Integer, PinConfiguration> configuration) {
        resource.path("conf").path("name").header("Content-type", "application/json").put(configuration);
    }

    public void configureNamedPins(Map<String, PinConfiguration> configuration) {
        resource.path("conf").path("name").header("Content-type", "application/json").put(configuration);
    }

    public WebResource getResource() {
        return resource;
    }
}
