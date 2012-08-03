/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.broker;

import java.util.concurrent.TimeUnit;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.broker.region.policy.RedeliveryPolicyMap;
import org.apache.activemq.broker.region.policy.SharedDeadLetterStrategy;
import org.apache.activemq.broker.util.RedeliveryPlugin;
import org.apache.activemq.command.ActiveMQQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrokerRedeliveryTest extends org.apache.activemq.TestSupport {

    static final Logger LOG = LoggerFactory.getLogger(BrokerRedeliveryTest.class);
    BrokerService broker = null;

    final ActiveMQQueue destination = new ActiveMQQueue("Redelivery");
    final String data = "hi";
    final long redeliveryDelayMillis = 2000;
    final int maxBrokerRedeliveries = 2;

    public void testScheduledRedelivery() throws Exception {

        sendMessage();

        ActiveMQConnection consumerConnection = (ActiveMQConnection) createConnection();
        RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
        redeliveryPolicy.setInitialRedeliveryDelay(0);
        redeliveryPolicy.setMaximumRedeliveries(0);
        consumerConnection.setRedeliveryPolicy(redeliveryPolicy);
        consumerConnection.start();
        Session consumerSession = consumerConnection.createSession(true, Session.SESSION_TRANSACTED);
        MessageConsumer consumer = consumerSession.createConsumer(destination);
        Message message = consumer.receive(1000);
        assertNotNull("got message", message);
        LOG.info("got: " + message);
        consumerSession.rollback();

        for (int i=0;i<maxBrokerRedeliveries;i++) {
            Message shouldBeNull = consumer.receive(500);
            assertNull("did not get message after redelivery count exceeded: " + shouldBeNull, shouldBeNull);

            TimeUnit.SECONDS.sleep(3);

            Message brokerRedeliveryMessage = consumer.receive(500);
            LOG.info("got: " + brokerRedeliveryMessage);
            assertNotNull("got message via broker redelivery after delay", brokerRedeliveryMessage);
            assertEquals("message matches", message.getStringProperty("data"), brokerRedeliveryMessage.getStringProperty("data"));
            assertEquals("has expiryDelay specified", redeliveryDelayMillis, brokerRedeliveryMessage.getLongProperty(RedeliveryPlugin.REDELIVERY_DELAY));

            consumerSession.rollback();
        }

        // validate DLQ
        MessageConsumer dlqConsumer = consumerSession.createConsumer(new ActiveMQQueue(SharedDeadLetterStrategy.DEFAULT_DEAD_LETTER_QUEUE_NAME));
        Message dlqMessage = dlqConsumer.receive(2000);
        assertNotNull("Got message from dql", dlqMessage);
        assertEquals("message matches", message.getStringProperty("data"), dlqMessage.getStringProperty("data"));
        consumerSession.commit();
    }

    private void sendMessage() throws Exception {
        ActiveMQConnection producerConnection = (ActiveMQConnection) createConnection();
        producerConnection.start();
        Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(destination);
        Message message = producerSession.createMessage();
        message.setStringProperty("data", data);
        producer.send(message);
        producerConnection.close();
    }

    private void startBroker(boolean deleteMessages) throws Exception {
        broker = new BrokerService();
        broker.setSchedulerSupport(true);


        RedeliveryPlugin redeliveryPlugin = new RedeliveryPlugin();

        RedeliveryPolicy brokerRedeliveryPolicy = new RedeliveryPolicy();
        brokerRedeliveryPolicy.setRedeliveryDelay(redeliveryDelayMillis);
        brokerRedeliveryPolicy.setInitialRedeliveryDelay(redeliveryDelayMillis);
        brokerRedeliveryPolicy.setMaximumRedeliveries(maxBrokerRedeliveries);

        RedeliveryPolicyMap redeliveryPolicyMap = new RedeliveryPolicyMap();
        redeliveryPolicyMap.setDefaultEntry(brokerRedeliveryPolicy);
        redeliveryPlugin.setRedeliveryPolicyMap(redeliveryPolicyMap);

        broker.setPlugins(new BrokerPlugin[]{redeliveryPlugin});

        if (deleteMessages) {
            broker.setDeleteAllMessagesOnStartup(true);
        }
        broker.start();
    }


    private void stopBroker() throws Exception {
        if (broker != null)
            broker.stop();
        broker = null;
    }

    protected ActiveMQConnectionFactory createConnectionFactory() throws Exception {
        return new ActiveMQConnectionFactory("vm://localhost");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        startBroker(true);
    }

    @Override
    protected void tearDown() throws Exception {
        stopBroker();
        super.tearDown();
    }
}