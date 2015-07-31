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
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Timer;
import javax.inject.Inject;
import javax.jms.Destination;
import javax.jms.JMSContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RequestSender {

  private static final Logger logger = LoggerFactory.getLogger(RequestSender.class);

  @Inject
  private JMSContext context;

  @Resource(name = Resources.REQUEST_QUEUE_REF)
  private Destination requestQueue;

  private long requestId;

  @Schedule(persistent = false, second = "*/2", minute = "*", hour ="*",
    month = "*", year = "*")
  public void onSchedule(Timer timer) {
    String message = Long.toString(requestId++);
    context.createProducer().send(requestQueue, message);
    logger.info("sent request: {}", message);
  }

}
