/*
 * File created on Jul 29, 2015
 *
 * Copyright (c) 2015 Carl Harris, Jr
 * and others as noted
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ceh.demo;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup",
        propertyValue = Resources.REQUEST_QUEUE_REF),
    @ActivationConfigProperty(propertyName = "destinationType",
        propertyValue = "javax.jms.Queue")
})
public class RequestProcessor implements MessageListener {

  private static final Logger logger = LoggerFactory.getLogger(RequestProcessor.class);

  @Inject
  private JMSContext context;

  @Resource(name = Resources.REPLY_QUEUE_REF)
  private Destination replyQueue;

  public void onMessage(Message message) {
    try {
      String request = message.getBody(String.class);
      logger.info("received request: {}", request);
      context.createProducer().send(replyQueue, "processed request " + request);
    }
    catch (JMSException ex) {
      logger.error("error receiving JMS message", ex);
    }
  }

}
