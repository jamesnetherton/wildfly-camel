/*
 * #%L
 * Wildfly Camel :: Testsuite
 * %%
 * Copyright (C) 2013 - 2018 RedHat
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
package org.wildfly.camel.test.health;

import static org.wildfly.extension.camel.parser.CamelHealthState.HEALTHY;
import static org.wildfly.extension.camel.parser.CamelHealthState.UNHEALTHY;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import com.google.common.collect.ObjectArrays;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ExplicitCamelContextNameStrategy;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.gravia.resource.ManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.common.types.GreetingService;
import org.wildfly.camel.test.common.types.GreetingServiceImpl;
import org.wildfly.camel.test.common.types.RestApplication;
import org.wildfly.camel.test.common.utils.DMRUtils;
import org.wildfly.extension.camel.CamelAware;

@CamelAware
@RunWith(Arquillian.class)
public class HealthEndpointTest {

    @ArquillianResource
    private ManagementClient managementClient;

    @ArquillianResource
    private Deployer deployer;

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "camel-health-tests.jar")
            .addClass(DMRUtils.class)
            .setManifest(() -> {
                ManifestBuilder builder = new ManifestBuilder();
                builder.addManifestHeader("Dependencies", "org.jboss.dmr,org.jboss.as.controller-client,com.google.guava");
                return builder.openStream();
            });
    }

    @Deployment(testable = false, managed = false, name = "no-camel.jar")
    public static JavaArchive createNonCamelDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "no-camel.jar")
            .addClasses(GreetingService.class, GreetingServiceImpl.class, RestApplication.class);
    }

    @Test
    public void testNoDeploymentReturnsNoStatus() throws Exception {
        Assert.assertNull("Expected health status to be null", getHealthStatus());
    }

    @Test
    public void testNonCamelDeploymentReturnsStatusHealthy() throws Exception {
        try {
            deployer.deploy("no-camel.jar");
            Assert.assertNull("Expected health status to be null", getHealthStatus());
        } finally {
            deployer.undeploy("no-camel.jar");
        }
    }

    @Test
    public void testHealthyCamelContextStatus() throws Exception {
        CamelContext camelctx = new DefaultCamelContext();
        camelctx.setNameStrategy(new ExplicitCamelContextNameStrategy("healthy-context"));
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                .to("mock:end");
            }
        });

        camelctx.start();
        try {
            Assert.assertEquals(HEALTHY.name(), getHealthStatus());
            Assert.assertEquals(HEALTHY.name(), getContextHealthStatus("healthy-context"));
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void testUnhealthyCamelContextStatus() throws Exception {
        CamelContext camelctx = new DefaultCamelContext();
        camelctx.setNameStrategy(new ExplicitCamelContextNameStrategy("unhealthy-context"));
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                .to("mock:end");
            }
        });

        camelctx.start();
        try {
            camelctx.suspend();
            Assert.assertEquals(UNHEALTHY.name(), getHealthStatus());
            Assert.assertEquals(UNHEALTHY.name(), getContextHealthStatus("unhealthy-context"));
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void testMixedHealthCamelContextStatus() throws Exception {
        CamelContext healthCamelctx = new DefaultCamelContext();
        healthCamelctx.setNameStrategy(new ExplicitCamelContextNameStrategy("healthy-context"));
        healthCamelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                .to("mock:end");
            }
        });

        CamelContext unhealthyCamelctx = new DefaultCamelContext();
        unhealthyCamelctx.setNameStrategy(new ExplicitCamelContextNameStrategy("unhealthy-context"));
        unhealthyCamelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                .to("mock:end");
            }
        });

        healthCamelctx.start();
        unhealthyCamelctx.start();
        try {
            unhealthyCamelctx.suspend();
            Assert.assertEquals(UNHEALTHY.name(), getHealthStatus());
            Assert.assertEquals(HEALTHY.name(), getContextHealthStatus("healthy-context"));
            Assert.assertEquals(UNHEALTHY.name(), getContextHealthStatus("unhealthy-context"));
        } finally {
            unhealthyCamelctx.stop();
            healthCamelctx.stop();
        }
    }

    @Test
    public void testHealthyHealthRepositoryStatus() throws Exception {
        CamelContext camelctx = new DefaultCamelContext();
        camelctx.setNameStrategy(new ExplicitCamelContextNameStrategy("healthy-context"));
        camelctx.getHealthCheckRegistry().addRepository(() -> Stream.of(new StatusUpHealthCheck()));
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                .to("mock:end");
            }
        });

        camelctx.start();
        try {
            Assert.assertEquals(HEALTHY.name(), getHealthStatus());
            Assert.assertEquals(HEALTHY.name(), getContextHealthStatus("healthy-context"));
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void testUnhealthyHealthRepositoryStatus() throws Exception {
        CamelContext camelctx = new DefaultCamelContext();
        camelctx.setNameStrategy(new ExplicitCamelContextNameStrategy("unhealthy-context"));
        camelctx.getHealthCheckRegistry().addRepository(() -> Stream.of(new StatusDownHealthCheck()));
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                .to("mock:end");
            }
        });

        camelctx.start();
        try {
            Assert.assertEquals(UNHEALTHY.name(), getHealthStatus());
            Assert.assertEquals(UNHEALTHY.name(), getContextHealthStatus("unhealthy-context"));
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void testMixedHealthRepositoryStatus() throws Exception {
        CamelContext camelctx = new DefaultCamelContext();
        camelctx.setNameStrategy(new ExplicitCamelContextNameStrategy("unhealthy-context"));
        camelctx.getHealthCheckRegistry().addRepository(() -> Stream.of(new StatusUpHealthCheck()));
        camelctx.getHealthCheckRegistry().addRepository(() -> Stream.of(new StatusDownHealthCheck()));
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                .to("mock:end");
            }
        });

        camelctx.start();
        try {
            Assert.assertEquals(UNHEALTHY.name(), getHealthStatus());
            Assert.assertEquals(UNHEALTHY.name(), getContextHealthStatus("unhealthy-context"));
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void testCamelSuspendedWithHealthyHealthRepositoryStatus() throws Exception {
        CamelContext camelctx = new DefaultCamelContext();
        camelctx.setNameStrategy(new ExplicitCamelContextNameStrategy("unhealthy-context"));
        camelctx.getHealthCheckRegistry().addRepository(() -> Stream.of(new StatusUpHealthCheck()));
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                .to("mock:end");
            }
        });

        camelctx.start();
        try {
            camelctx.suspend();
            Assert.assertEquals(UNHEALTHY.name(), getHealthStatus());
            Assert.assertEquals(UNHEALTHY.name(), getContextHealthStatus("unhealthy-context"));
        } finally {
            camelctx.stop();
        }
    }

    private String getHealthStatus() throws IOException {
        ModelNode modelNode = DMRUtils.createOpNode("subsystem=camel", "health");
        ModelNode result = client().execute(modelNode);
        Assert.assertEquals("success", result.get("outcome").asString());
        return getResult(result, "status");
    }

    private String getContextHealthStatus(String contextName) throws IOException {
        ModelNode modelNode = DMRUtils.createOpNode("subsystem=camel", "health");
        ModelNode result = client().execute(modelNode);
        Assert.assertEquals("success", result.get("outcome").asString());
        return getResult(result, contextName, "status");
    }

    private ModelControllerClient client() {
        return managementClient.getControllerClient();
    }

    private String getResult(ModelNode modelNode, String ...paths) {
        String[] resultPaths = ObjectArrays.concat("result", paths);
        String result = null;
        for (String path : resultPaths) {
            modelNode = modelNode.get(path);
            result = modelNode.asStringOrNull();
        }
        return result;
    }

    static class StatusUpHealthCheck extends AbstractHealthCheck {
        protected StatusUpHealthCheck() {
            super("camel", "status-up");
            getConfiguration().setEnabled(true);
        }

        @Override
        protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            builder.up();
        }
    }

    static class StatusDownHealthCheck extends AbstractHealthCheck {
        protected StatusDownHealthCheck() {
            super("camel", "status-down");
            getConfiguration().setEnabled(true);
        }

        @Override
        protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            builder.down();
        }
    }
}
