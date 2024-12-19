package com.example.svc_a;


import java.util.ArrayList;

import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import com.instana.sdk.annotation.Span;
import com.instana.sdk.support.SpanSupport;

@SpringBootApplication
public class UserConsumer {
    private static final Logger logger = LoggerFactory.getLogger(UserConsumer.class);
    public static void main(String[] args) {
        SpringApplication.run(UserConsumer.class, args);
    }

    @Bean
    public MqttAsyncClient mqttAsyncClient(@Value("${MQTT_BROKER_HOST:broker.emqx.io}") String mqttHost,
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

    @Value("${MQTT_TOPIC:pollingResults}")
    private String mqttTopic;


    @Service
    @RocketMQMessageListener(topic = "TopicTest", consumerGroup = "user_consumer")
    public class Consumer implements RocketMQListener<MessageExt> {

        private final MqttAsyncClient mqttClient;

        public Consumer(MqttAsyncClient mqttClient) { // 构造器注入
            this.mqttClient = mqttClient;
        }

        @Override
        // @Span(value = "Process rmq message")
        public void onMessage(MessageExt msg) {
            
            logger.info("msg: " + msg);
            String tracdId = msg.getProperty("X-INSTANA-T");
            String parentId = msg.getProperty("X-INSTANA-S");


            if (tracdId == null || parentId == null) {
                logger.warn("Missing Instana trace properties in message.");

                long ctraceId = SpanSupport.currentTraceId(Span.Type.ENTRY);
                logger.info("currentTraceId: {}", Long.toHexString(ctraceId));
                
                String traceIdHex = Long.toHexString(ctraceId);

                // If no traceId exists, generate a new one
                if (ctraceId == 0) {
                    ctraceId = Long.parseLong(SpanSupport.traceId()); // Assuming this method generates a new trace ID
                    logger.info("Generated new traceId: {}", Long.toHexString(ctraceId));
                } else {
                    logger.info("Using existing traceId: {}", traceIdHex);
                }
            }else{
                logger.info("Trace ID: " + tracdId);
                logger.info("Parent ID: " + parentId);
                // logger.info("Span ID: " + spanId);
            }
            // send to mqtt logic
            // 获取消息的 msgId
            String msgId = msg.getMsgId();
            // 获取消息的 body 并转换为字符串
            String msgBody = new String(msg.getBody());

            logger.info("Message ID: " + msgId);
            logger.info("Message Body: " + msgBody);


            logger.info("Instana Tracing status:" + SpanSupport.isTracing())  ;

            // Create spanId
            String spanId = Long.toHexString(SpanSupport.currentSpanId(Span.Type.ENTRY));
            logger.info("Current consumer spanId: {}", spanId);

            publishToMqtt(mqttClient, msgId, msgBody, tracdId, parentId);

        }
    }


    private final String SPAN_NAME = "write to mqtt";
    @Span(type = Span.Type.EXIT, value = SPAN_NAME)
    private void publishToMqtt(MqttAsyncClient mqttClient, String msgId, String result, String traceId, String spanId) {

        logger.info("Instana Tracing status:" + SpanSupport.isTracing())  ;

        SpanSupport.annotate(Span.Type.EXIT, SPAN_NAME, "tags.msdId", msgId);

        String cspanId = Long.toHexString(SpanSupport.currentSpanId(Span.Type.EXIT));
        logger.info("Current  sdk spanId: {}", cspanId);
        
        String payload = "Msg ID: " + msgId + ", Result: " + result;
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1);
        message.setRetained(false);

        // Add W3C Trace Context attributes
        String w3cTraceId = "0000000000000000" + traceId;
        String traceParent = String.format("00-%s-%s-01", w3cTraceId, cspanId);
        String traceState = "msgid=" + msgId;

        MqttProperties properties = new MqttProperties();
        ArrayList<UserProperty> userDefinedProperties = new ArrayList<>();

        userDefinedProperties.add(new UserProperty("traceparent", traceParent));
        userDefinedProperties.add(new UserProperty("tracestate", traceState));

        properties.setUserProperties(userDefinedProperties);
        message.setProperties(properties);
        
        try {
             if (mqttClient == null || !mqttClient.isConnected()) {
                logger.error("MQTT client is not connected!");
                return;
            }
            mqttClient.publish(mqttTopic, message);
            logger.info("Message published to MQTT topic: {}", mqttTopic);
            logger.info("Publishing message with w3cTraceId: {}, spanId: {}", w3cTraceId, cspanId);
            logger.info("Publishing message with traceParent: {}, traceState: {}", traceParent, traceState);
        } catch (MqttException e) {
            logger.error("Failed to publish message to MQTT: {}", e.getMessage());
        }
    }

}
