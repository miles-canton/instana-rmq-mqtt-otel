package com.example.svc_a;

import java.util.ArrayList;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import org.springframework.rocketmq.annotation.RocketMQMessageListener;
import org.springframework.rocketmq.core.RocketMQListener;
import org.springframework.util.StringUtils;

import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import com.instana.sdk.annotation.Span;
import com.instana.sdk.support.SpanSupport;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootApplication
@EnableScheduling
public class SvcAApplication {

    private static final Logger logger = LoggerFactory.getLogger(SvcAApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SvcAApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public MqttAsyncClient mqttAsyncClient(@Value("${MQTT_BROKER_HOST:localhost}") String mqttHost,
                                            @Value("${MQTT_BROKER_PORT:1883}") int mqttPort,
                                            @Value("${MQTT_CLIENT_ID:javaclient}") String mqttClientId) throws MqttException {
        String brokerUrl = "tcp://" + mqttHost + ":" + mqttPort;
        MqttAsyncClient client = new MqttAsyncClient(brokerUrl, mqttClientId);
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);
        try {
            client.connect(options).waitForCompletion();
            logger.info("Connected to MQTT Broker: {}", brokerUrl);
        } catch (MqttException e) {
            throw new RuntimeException("Failed to connect to MQTT broker", e);
        }
        return client;
    }
    @Service
    @RocketMQMessageListener(topic = "your-topic-name", consumerGroup = "test-consumer")
    public class RocketMQConsumerService implements RocketMQListener<MessageExt> {

        @Override
        public void onMessage(MessageExt messageExt) {
            // 获取消息体
            String body = new String(messageExt.getBody());
            
            // 获取扩展消息中的 X-INSTANA-T 信息
            String instanaT = messageExt.getProperties().get("X-INSTANA-T");

            // 打印消息体和 Instana Trace 信息
            System.out.println("Received message body: " + body);
            if (!StringUtils.isEmpty(instanaT)) {
                System.out.println("Instana Trace ID: " + instanaT);
            } else {
                System.out.println("No Instana Trace ID found.");
            }
        }
    }
    
    @RestController
    public class SvcAController {

        private static final Logger logger = LoggerFactory.getLogger(SvcAController.class);

        @Value("${SVC_B_URL:http://svc-b}")
        private String svcBUrl;

        @Value("${SVC_B_PORT:8080}")
        private String svcBPort;

        @Value("${MQTT_TOPIC:pollingResults}")
        private String mqttTopic;

        private final RestTemplate restTemplate;
        private final MqttAsyncClient mqttClient;

        public SvcAController(RestTemplate restTemplate, MqttAsyncClient mqttClient) {
            this.restTemplate = restTemplate;
            this.mqttClient = mqttClient;
        }

        @GetMapping("/poll")
        public String poll(@RequestParam(name = "taskId") String taskId) {
            String url = UriComponentsBuilder.fromHttpUrl(svcBUrl)
                .port(svcBPort)
                .path("/status")
                .queryParam("taskId", taskId)
                .toUriString();

            Boolean result;
            try {
                result = restTemplate.getForObject(url, Boolean.class);
                logger.info("get result form svc-b: {}", result);
            } catch (Exception e) {
                logger.error("Error calling svc-b: {}", e.getMessage());
                return "Error calling svc-b: " + e.getMessage();
            }

            public static final String CONSUMER_GROUP = "please_rename_unique_group_name_4";
            public static final String DEFAULT_NAMESRVADDR = "10.88.0.1:9876";
            public static final String TOPIC = "TopicTest";
            DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
            consumer.subscribe(TOPIC, "*");

            /*
                *  Register callback to execute on arrival of messages fetched from brokers.
                */
            consumer.registerMessageListener((MessageListenerConcurrently) (msg, context) -> {
                System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), msg);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();
            System.out.printf("Consumer Started.%n");
            
            long traceId = SpanSupport.currentTraceId(Span.Type.ENTRY);
            logger.info("currentTraceId: {}", Long.toHexString(traceId));

            String traceIdHex = Long.toHexString(traceId);

            // If no traceId exists, generate a new one
            if (traceId == 0) {
                traceId = Long.parseLong(SpanSupport.traceId()); // Assuming this method generates a new trace ID
                logger.info("Generated new traceId: {}", Long.toHexString(traceId));
            } else {
                logger.info("Using existing traceId: {}", traceIdHex);
            }

            // Create spanId
            String spanId = Long.toHexString(SpanSupport.currentSpanId(Span.Type.ENTRY));
            logger.info("Current spanId: {}", spanId);



            if (Boolean.TRUE.equals(result)) {
                publishToMqtt(taskId, result, traceIdHex, spanId);
                return "Polling task " + taskId + " success.";
            }

            return "Polling task " + taskId + " failed.";
        }

        private final String SPAN_NAME = "write to mqtt";

        @Span(type = Span.Type.EXIT, value = SPAN_NAME)
        private void publishToMqtt(String taskId, Boolean result, String traceId, String spanId) {
            SpanSupport.annotate(Span.Type.EXIT, SPAN_NAME,"tags.mqtt.url", "mqtt://localhost:1883/greeting");
            SpanSupport.annotate(Span.Type.EXIT, SPAN_NAME,"tags.taskid", taskId);

            String payload = "Task ID: " + taskId + ", Result: " + result;
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            message.setRetained(false);

            // Add W3C Trace Context attributes
            String w3cTraceId = "0000000000000000" + traceId;
            String traceParent = String.format("00-%s-%s-01", w3cTraceId, spanId);
            String traceState = "taskid=" + taskId;

            MqttProperties properties = new MqttProperties();
            ArrayList<UserProperty> userDefinedProperties = new ArrayList<>();

            userDefinedProperties.add(new UserProperty("traceparent", traceParent));
            userDefinedProperties.add(new UserProperty("tracestate", traceState));

            properties.setUserProperties(userDefinedProperties);
            message.setProperties(properties);

            try {
                mqttClient.publish(mqttTopic, message);
                logger.info("Message published to MQTT topic: {}", mqttTopic);
                logger.info("Publishing message with w3cTraceId: {}, spanId: {}", w3cTraceId, spanId);
                logger.info("Publishing message with traceParent: {}, traceState: {}", traceParent, traceState);
            } catch (MqttException e) {
                logger.error("Failed to publish message to MQTT: {}", e.getMessage());
            }
        }
    }

    @Service
    public class PollingService {

        private static final Logger logger = LoggerFactory.getLogger(PollingService.class);

        private final RestTemplate restTemplate;
        private final MqttAsyncClient mqttClient;

        @Value("${SVC_B_URL:http://svc-b}")
        private String svcBUrl;

        @Value("${SVC_B_PORT:8080}")
        private String svcBPort;

        @Value("${MQTT_TOPIC:pollingResults}")
        private String mqttTopic;

        @Value("${POLL_INTERVAL:5000}")
        private long pollInterval; // in milliseconds

        @Value("${TASK_ID:defaultTaskId}")
        private String taskId;

        public PollingService(RestTemplate restTemplate, MqttAsyncClient mqttClient) {
            this.restTemplate = restTemplate;
            this.mqttClient = mqttClient;
        }

        @Scheduled(fixedDelayString = "${POLL_INTERVAL:5000}")
        public String pollSvcB() {
            String url = UriComponentsBuilder.fromHttpUrl(svcBUrl)
                .port(svcBPort)
                .path("/check")
                .queryParam("taskId", taskId)
                .toUriString();

            Boolean result;
            try {
                result = restTemplate.getForObject(url, Boolean.class);
                if (result == null) {
                    logger.error("Received null response from svc-b for taskId: {}", taskId);
                    return "No response from svc-b for taskId: " + taskId;
              }
            } catch (Exception e) {
                 logger.error("Error calling svc-b: {}", e.getMessage());
                return "Error calling svc-b: " + e.getMessage();
             }
             // Check for existing traceId
            long traceId = SpanSupport.currentTraceId(Span.Type.ENTRY);
            String traceIdHex = Long.toHexString(traceId);

            // If no traceId exists, generate a new one
            if (traceId == 0) {
                traceId = Long.parseLong(SpanSupport.traceId()); // Assuming this method generates a new trace ID
                logger.info("Generated new traceId: {}", Long.toHexString(traceId));
            } else {
                logger.info("Using existing traceId: {}", traceIdHex);
            }

            // Create spanId
            String spanId = Long.toHexString(SpanSupport.currentSpanId(Span.Type.ENTRY));
            logger.info("Current spanId: {}", spanId);


            if (result != null && result) {
                publishToMqtt(taskId, result, traceIdHex, spanId);
            }
            return " ";
        }

        private final String SPAN_NAME = "push to mqtt";
        @Span(type = Span.Type.EXIT, value = SPAN_NAME)
        private void publishToMqtt(String taskId, Boolean result, String traceId, String spanId) {
            SpanSupport.annotate(Span.Type.EXIT, SPAN_NAME,"tags.mqtt.url", "mqtt://localhost:1883/greeting");
            SpanSupport.annotate(Span.Type.EXIT, SPAN_NAME,"tags.taskid", taskId);

            String payload = "Task ID: " + taskId + ", Result: " + result;
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            message.setRetained(false);

            // // Get current Trace and Span ID
            // String traceId = SpanSupport.traceId();
            // // String traceId = Long.toHexString(SpanSupport.currentTraceId(Span.Type.ENTRY));
            // String spanId  = Long.toHexString(SpanSupport.currentSpanId(Span.Type.ENTRY));

            // Add W3C Trace Context attributes
            String w3cTraceId = "0000000000000000" + traceId;
            String traceParent = String.format("00-%s-%s-01", w3cTraceId, spanId);
            String traceState = "taskid=" + taskId;

            MqttProperties properties = new MqttProperties();
            ArrayList<UserProperty> userDefinedProperties = new ArrayList<>();

            userDefinedProperties.add(new UserProperty("traceparent", traceParent));
            userDefinedProperties.add(new UserProperty("tracestate", traceState));

            properties.setUserProperties(userDefinedProperties);
            message.setProperties(properties);

            try {
                mqttClient.publish(mqttTopic, message);
                logger.info("Message published to MQTT topic: {}", mqttTopic);
                logger.info("Publishing message with w3cTraceId: {}, spanId: {}", w3cTraceId, spanId);
                logger.info("Publishing message with traceParent: {}, traceState: {}", traceParent, traceState);

            } catch (MqttException e) {
                logger.error("Failed to publish message to MQTT: {}", e.getMessage());
            }
        }
    }
}


