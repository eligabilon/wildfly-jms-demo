wildfly-jms-demo
================

Example of JMS 2 in Java EE using Wildfly 8.2.

In this example, we start with an out-of-the-box standalone configuration of
Wildfly 8.2 and then use the Wildfly CLI to add all of the configuration needed 
for JMS in Java EE using the embedded HornetQ message broker.  We then deploy a 
Java EE application using Message-Driven beans to receive messages and the 
(new to JMS 2) injectable `JMSContext` to send messages.

Wildfly's built in JMS provider is HornetQ.  After getting a basic configuration 
working using HornetQ's in-VM transport, we then reconfigure HornetQ to use HTTP 
Upgrade transport.  This transport option leverages the HTTP Upgrade capability 
of the Undertow subsystem.  HornetQ client connections are established using the 
HTTP protocol, and then upgraded to utilize the HornetQ wire protocol over the 
same socket connection. 

# Configuring HornetQ for JMS

We'll use the JBoss CLI (`jboss-cli.sh`) to do all of our configuration of 
Wildfly.

### Enable the Messaging Subsystem

The following CLI commands are used to add the messaging subsystem to Wildfly.

```
/extension=org.jboss.as.messaging:add
/subsystem=messaging:add
reload
```

### Create the In-VM Transport Acceptor and Connector

We add the In-VM Transport acceptor and connector components using the
following CLI commands.

```
/subsystem=messaging/hornetq-server=default:add
/subsystem=messaging/hornetq-server=default/in-vm-acceptor=in-vm-acceptor:add(server-id=0)
/subsystem=messaging/hornetq-server=default/in-vm-connector=in-vm-connector:add(server-id=0)
reload
```

### Create the Pooled Connection Factory / Resource Adapter  

The pooled connection factory configuration creates a JMS `ConnectionFactory` 
as well as the HornetQ resource adapter.  This configuration provides inbound 
message delivery for message-driven beans, and outbound message delivery 
for EJBs that inject a JMS 2 `JMSContext` to send messages.  The connection 
factory is configured to support container-managed transactions appropriate for 
EJBs.

```
/subsystem=messaging/hornetq-server=default/pooled-connection-factory=hornetq-ra:add(transaction=xa, connector=[in-vm-connector], entries=[java:/JmsXA, java:jboss/DefaultJMSConnectionFactory])
```

### Configure EJB Defaults

When a message-driven bean is not annotated with a `@ResourceAdapter`
specification, the container needs a default resource adapter to use to receive 
inbound messages.  Here, we specify the `hornetq-ra` resource adapter that
we configured in the previous step.  

Additionally, message-driven beans are pooled to support concurrent message 
delivery.  The configuration specified here uses a strict pool that is included 
in the default standalone configuration of Wildfly.

```
/subsystem=ejb3:write-attribute(name=default-resource-adapter-name,value=hornetq-ra)
/subsystem=ejb3:write-attribute(name=default-mdb-instance-pool,value=mdb-strict-max-pool)
```

### Create JMS Queues

Our demo application uses a *request* queue and a *reply* queue.  Each of these
is a simple queue that is located using a JNDI lookup.

```
/subsystem=messaging/hornetq-server=default/jms-queue=demo-request-queue:add(entries=[java:global/jms/queue/demo/request])
/subsystem=messaging/hornetq-server=default/jms-queue=demo-reply-queue:add(entries=[java:global/jms/queue/demo/reply])
```

# Run the Demo Application

The demo application consists of three beans.

* `RequestSender` is a timer-driven singleton bean that sends request messages on
  the request queue.
  Each request is simply an integer value that represents a request identifier.
* `RequestProcessor` is a message-driven bean that consumes messages from the
  request queue.  It "processes" requests by logging a message indicating that
  a request was received and then sending a reply message on the reply queue.
* `ReplyProcessor` is a message-driven mena that consumes messages from the
  reply queue.  When a reply is received, it logs a message.
  
When the application is running, you should see a steady stream of messages
logged to the console by each of these three beans as requests are generated 
and sent, received by the processor and processed, and replies are received.
For example, here's some output generated during a run of the demo application:

```
04:30:28,013 INFO  [ceh.demo.RequestSender] (EJB default - 8) sent request: 0
04:30:28,016 INFO  [ceh.demo.RequestProcessor] (Thread-20 (HornetQ-client-global-threads-604041428)) received request: 0
04:30:28,032 INFO  [ceh.demo.ReplyProcessor] (Thread-19 (HornetQ-client-global-threads-604041428)) received reply: processed request 0
04:30:30,003 INFO  [ceh.demo.RequestSender] (EJB default - 9) sent request: 1
04:30:30,005 INFO  [ceh.demo.RequestProcessor] (Thread-20 (HornetQ-client-global-threads-604041428)) received request: 1
04:30:30,021 INFO  [ceh.demo.ReplyProcessor] (Thread-15 (HornetQ-client-global-threads-604041428)) received reply: processed request 1
```

You can easily build and deploy the application using Maven at the top level
project.

```
mvn wildfly:deploy
```

After running the demo and verifying that it works properly, you should
undeploy it so that it will no longer produce console messages, and so that you
won't see errors logged as we reconfigure for the next steps

```
mvn wildfly:undeploy
```

# Configure HornetQ for HTTP Upgrade Transport

The HTTP Upgrade transport uses additional HornetQ acceptor and connector
components.  These can be configured alongside the In-VM transport components.

When using HTTP Upgrade transport, connections to the HornetQ acceptor must
be successfully authenticated and authorized.  The subject (user) associated 
with the connection must have a role that is authorized with permissions 
appropriate for interacting with the messaging subsystem.  This will require the 
creation of a user, associated password, and role, along with configuration that 
assigns messaging-subsystem-specific permissions to the given role.

### Create the HTTP Upgrade Transport Acceptor and Connector

This configuration adds a HornetQ acceptor and connector for HTTP Upgrade
transport, and then reconfigures the connection factory and resource adapter
to use the HTTP connector.

```
/subsystem=messaging/hornetq-server=default/http-acceptor=http-acceptor:add(http-listener=default)
/subsystem=messaging/hornetq-server=default/http-connector=http-connector:add(socket-binding=http)
/subsystem=messaging/hornetq-server=default/http-connector=http-connector/param=http-upgrade-endpoint:add(value=http-acceptor)
/subsystem=messaging/hornetq-server=default/pooled-connection-factory=hornetq-ra:write-attribute(name=connector, value=[http-connector])
reload   
```

### Create a User and Role 

By default, HornetQ uses Wildfly's default security domain (named "other") for
authentication and authorization.  The "other" security domain delegates to the
security realm named "ApplicationRealm".  This realm is configured to use the
`application-users.properties` and `application-roles.properties` files to 
define users and roles, respectively.

Edit the `application-users.properties` file and add this line, which defines 
a *hornetq* user with password *hornetq*.  Obviously, you'll want a better
password for anything other than this demo.

```
hornetq=77a8417ab6a763ceb7696e871c11a6ae
```

Edit the `application-roles.properties` file and add this line, which assigns
the *hornetq-connector* role to the *hornetq* user.

```
hornetq=hornetq-connector
```

### Specify the User and Password on the Resource Adapter

These CLI commands set the `user` and `password` attributes on the resource 
adapter.  This causes the resource adapter to present these credentials when
connecting to HornetQ's HTTP Upgrade acceptor.

We could also have specified these attributes when we created the pooled 
connection factory (resource adapter).

```
/subsystem=messaging/hornetq-server=default/pooled-connection-factory=hornetq-ra:write-attribute(name=user, value=hornetq)
/subsystem=messaging/hornetq-server=default/pooled-connection-factory=hornetq-ra:write-attribute(name=password, value=hornetq)
```

### Configure HornetQ Security Settings

These commands grant the permissions needed to send and consume messages on
any topic/queue to the *hornetq-connector* role.

```
/subsystem=messaging/hornetq-server=default/security-setting=#:add
/subsystem=messaging/hornetq-server=default/security-setting=#/role=hornetq-connector:add(consume=true, send=true)
```

### Restart Wildfly

Due do a bug in Wildfly (see [WFLY-3355] (https://issues.jboss.org/browse/WFLY-3355)), 
we must stop and restart the Wildfly process after reconfiguring the resource 
adapter with a username and password.

Apparently, the `WorkManager` that is used by resource adapters is marked as
"shutting down" when the `reload` CLI command is used, but its status does not
return to "available" after the container finishes restarting.  If you don't
shutdown here, you'll see messages like this:

```
04:50:41,389 ERROR [org.jboss.msc.service.fail] (ServerService Thread Pool -- 54) MSC000001: Failed to start service jboss.deployment.unit."wildfly-jms-demo-1.0.0-SNAPSHOT.war".component.RequestProcessor.START:
 org.jboss.msc.service.StartException in service jboss.deployment.unit."wildfly-jms-demo-1.0.0-SNAPSHOT.war".component.RequestProcessor.START: 
 java.lang.RuntimeException: javax.resource.spi.work.WorkRejectedException: IJ000263: WorkManager is shutting down
```

# Run the Demo Application Again

Run the demo again and observe that it still functions in exactly the same way,
though it is now using a different transport to interact with the messaging
subsystem.

```
mvn wildfly:deploy
```

After verifying that the demo still works, you should undeploy it.

```
mvn wildfly:undeploy
```

# Reset the Wildfly Configuration

You can easily remove all of the configuration added by this demo using these
CLI commands.

```
/subsystem=ejb3:undefine-attribute(name=default-resource-adapter-name)
/subsystem=ejb3:undefine-attribute(name=default-mdb-instance-pool)
/subsystem=messaging/hornetq-server=default:remove
/subsystem=messaging:remove
/extension=org.jboss.as.messaging:remove
```

The entries added to `application-users.properties` and 
`application-roles.properties` can be removed as well.