package com.example.svc_b;


import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class SvcBApplication {

    public static void main(String[] args) {
        SpringApplication.run(SvcBApplication.class, args);
    }

    @RestController
    public class RocketMQController {

        private static final Logger logger = LoggerFactory.getLogger(RocketMQController.class);

        @Value("${rocketmq.namesrv.addr}")
        private String namesrvAddr;

        @Value("${rocketmq.producer.group}")
        private String producerGroup;

        @Value("${rocketmq.default.topic}")
        private String defaultTopic;

        private DefaultMQProducer producer;

        /**
         * init RocketMQ Producer
         */
        @PostConstruct
        public void init() {
            producer = new DefaultMQProducer(producerGroup);
            producer.setNamesrvAddr(namesrvAddr);

            try {
                producer.start();
                logger.info("RocketMQ Producer started successfully with NameServer: {}", namesrvAddr);
            } catch (MQClientException e) {
                logger.error("Failed to start RocketMQ Producer", e);
                throw new RuntimeException(e);
            }
        }

        /**
         * destroy Producer
         */
        @PreDestroy
        public void destroy() {
            if (producer != null) {
                producer.shutdown();
                logger.info("RocketMQ Producer shut down");
            }
        }

        /**
         * sendMessage
         */
        @GetMapping("/rmq")
        public String sendMessage(
                @RequestParam(value = "topic", required = false) String topic,
                @RequestParam("msg") String messageBody) {
            
            // use default topic if RequestParam topic is null 
            String resolvedTopic = (topic == null || topic.isEmpty()) ? defaultTopic : topic;

            try {
                Message message = new Message(resolvedTopic, "TagA", messageBody.getBytes());
                
                SendResult sendResult = producer.send(message);

                logger.info("Message sent successfully: topic={}, msgId={}, msg={}", resolvedTopic, sendResult.getMsgId(), message);
                return "Message sent successfully to topic " + resolvedTopic + ", msgId=" + sendResult.getMsgId();
            } catch (Exception e) {
                logger.error("Failed to send message: topic={}", resolvedTopic, e);
                return "Failed to send message to topic " + resolvedTopic + ": " + e.getMessage();
            }
        }
    }
}
