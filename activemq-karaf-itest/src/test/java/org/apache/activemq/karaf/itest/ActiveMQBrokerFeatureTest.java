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
package org.apache.activemq.karaf.itest;

import java.util.concurrent.Callable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.junit.PaxExam;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(PaxExam.class)
public class ActiveMQBrokerFeatureTest extends AbstractJmsFeatureTest {

    @Configuration
    public static Option[] configure() {
        return configureBrokerStart(configure("activemq"));
    }

    @Test(timeout=5 * 60 * 1000)
    public void test() throws Throwable {

        withinReason(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                assertEquals("brokerName = amq-broker", executeCommand("activemq:list").trim());
                return true;
            }
        });


        withinReason(new Callable<Boolean>(){
            @Override
            public Boolean call() throws Exception {
                assertTrue(executeCommand("activemq:bstat").trim().contains("BrokerName = amq-broker"));
                return true;
            }
        });

        // produce and consume
        final String nameAndPayload = String.valueOf(System.currentTimeMillis());
        produceMessage(nameAndPayload);

        executeCommand("activemq:bstat", COMMAND_TIMEOUT, false).trim();

        withinReason(new Callable<Boolean>(){
            @Override
            public Boolean call() throws Exception {
                assertEquals("JMS_BODY_FIELD:JMSText = " + nameAndPayload, executeCommand("activemq:browse --amqurl tcp://localhost:61616 --user karaf --password karaf -Vbody " + nameAndPayload).trim());
                return true;
            }
        });

        assertEquals("got our message", nameAndPayload, consumeMessage(nameAndPayload));
    }

    @Test(timeout = 5 * 60 * 1000)
    public void testTemporaryDestinations() throws Throwable {
        Connection connection = getConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        TemporaryQueue temporaryQueue = session.createTemporaryQueue();
        session.createProducer(temporaryQueue).send(session.createTextMessage("TEST"));
        Message msg = session.createConsumer(temporaryQueue).receive(3000);
        assertNotNull("Didn't receive the message", msg);
        connection.close();
    }

}
