package org.wildfly.camel.test.cxf.rs;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.common.types.GreetingService;
import org.wildfly.extension.camel.CamelAware;
import org.wildfly.extension.camel.CamelContextRegistry;

@CamelAware
@RunWith(Arquillian.class)
public class CXFRSSpringProducerIntegrationTest {

    @ArquillianResource
    CamelContextRegistry contextRegistry;

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "cxfrs-producer-tests")
            .addClass(GreetingService.class)
            .addAsResource("cxf/spring/cxfrs-producer-camel-context.xml", "cxfrs-producer-camel-context.xml");
    }

    @Test
    public void testCXFProducer() throws Exception {
        CamelContext camelctx = contextRegistry.getCamelContext("cxfrs-producer-context");
        Assert.assertNotNull("Expected cxfrs-producer-context to not be null", camelctx);
        Assert.assertEquals(ServiceStatus.Started, camelctx.getStatus());

        ProducerTemplate template = camelctx.createProducerTemplate();
        String result = template.requestBodyAndHeader("direct:start", null, Exchange.HTTP_METHOD, "GET",  String.class);

        Assert.assertEquals("Hello Kermit", result);
    }
}
