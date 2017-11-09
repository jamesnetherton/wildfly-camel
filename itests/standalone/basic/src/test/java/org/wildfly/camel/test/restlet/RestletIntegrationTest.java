/*
 * #%L
 * Wildfly Camel :: Testsuite
 * %%
 * Copyright (C) 2013 - 2017 RedHat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wildfly.camel.test.restlet;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.common.http.HttpRequest;
import org.wildfly.camel.test.common.http.HttpRequest.HttpResponse;
import org.wildfly.extension.camel.CamelAware;

@CamelAware
@RunWith(Arquillian.class)
public class RestletIntegrationTest {

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "camel-restlet-tests.jar")
            .addClass(HttpRequest.class);
    }

    @Test
    public void testRestletComponent() throws Exception {
        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("restlet:/say/hello/{name}")
                .setBody(simple("Hello ${header.name}"));

                from("restlet:/say/hello")
                .setBody(simple("Hello unknown"));
            }
        });

        camelctx.start();
        try {
            HttpResponse response = HttpRequest.get("http://localhost:8080/restlet/camel-restlet-tests/say/hello/Kermit").getResponse();
            Assert.assertEquals("Hello Kermit", response.getBody());

            response = HttpRequest.get("http://localhost:8080/restlet/camel-restlet-tests/say/hello").getResponse();
            Assert.assertEquals("Hello unknown", response.getBody());
        } finally {
            camelctx.stop();
        }
    }
}
