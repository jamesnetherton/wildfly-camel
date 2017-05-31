package org.wildfly.camel.test.cxf.ws;

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
import org.wildfly.camel.test.common.types.Endpoint;
import org.wildfly.camel.test.common.utils.TestUtils;
import org.wildfly.extension.camel.CamelAware;
import org.wildfly.extension.camel.CamelContextRegistry;

@CamelAware
@RunWith(Arquillian.class)
public class CXFWSSSLProducerIntegrationTest {

    @ArquillianResource
    CamelContextRegistry contextRegistry;

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "cxf-ws-ssl-producer-tests")
            .addClasses(Endpoint.class, TestUtils.class)
            .addAsResource("cxf/spring/cxfws-ssl-producer-camel-context.xml", "cxfws-ssl-producer-camel-context.xml");
    }

    @Test
    public void testCxfWSSSLProducer() {
        CamelContext camelctx = contextRegistry.getCamelContext("cxfws-ssl-context");
        Assert.assertNotNull("Expected cxfrs-producer-context to not be null", camelctx);
        Assert.assertEquals(ServiceStatus.Started, camelctx.getStatus());

        ProducerTemplate template = camelctx.createProducerTemplate();
        String result = template.requestBody("direct:start", null, String.class);

    }
}
