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
package org.wildfly.camel.test.deployment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.gravia.resource.ManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.common.http.HttpRequest;
import org.wildfly.camel.test.common.http.HttpRequest.HttpResponse;
import org.wildfly.camel.test.common.utils.LogUtils;
import org.wildfly.extension.camel.CamelAware;
import org.wildfly.extension.camel.CamelContextRegistry;

@CamelAware
@RunWith(Arquillian.class)
public class CamelLibraryFilterTest {

    private static final String CAMEL_WAR_A = "camel-a.war";
    private static final String CAMEL_WAR_B = "camel-b.war";
    private static final String CAMEL_WAR_C = "camel-c.war";

    @ArquillianResource
    private Deployer deployer;

    @ArquillianResource
    private CamelContextRegistry contextRegistry;

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "camel-library-filter-tests.jar")
            .addClasses(HttpRequest.class, LogUtils.class);
    }

    @Deployment(name = CAMEL_WAR_A, testable = false, managed = false)
    public static WebArchive createDeploymentWithCamelJar() {
        File[] libraryDependencies = Maven.configureResolverViaPlugin().
            resolve("org.apache.camel:camel-core").
            withTransitivity().
            asFile();

        return ShrinkWrap.create(WebArchive.class, CAMEL_WAR_A)
            .addAsManifestResource("deployment/simple-camel-context.xml", "spring/camel-context.xml")
            .addAsLibraries(libraryDependencies);
    }

    @Deployment(name = CAMEL_WAR_B, testable = false, managed = false)
    public static WebArchive createAvroDeployment() {
        File[] libraryDependencies = Maven.configureResolverViaPlugin().
            resolve("org.apache.avro:avro").
            withoutTransitivity().
            asFile();

        return ShrinkWrap.create(WebArchive.class, CAMEL_WAR_B)
            .addAsManifestResource("deployment/simple-camel-context.xml", "spring/camel-context.xml")
            .addAsLibraries(libraryDependencies);
    }

    @Deployment(name = CAMEL_WAR_C, testable = false, managed = false)
    public static WebArchive createCamelJettyDeployment() {
        File[] camelJettyDependencies = Maven.configureResolverViaPlugin().
            resolve("org.apache.camel:camel-jetty").
            withTransitivity().
            asFile();

        // Filter out any camel libraries except camel-jetty
        List<File> libraryDependencies = new ArrayList<>();

        for (File file : camelJettyDependencies) {
            if (file.getName().startsWith("camel-")) {
                if (file.getName().matches("camel-(jetty|jetty-common).*")) {
                    libraryDependencies.add(file);
                }
            } else {
                libraryDependencies.add(file);
            }
        }

        return ShrinkWrap.create(WebArchive.class, CAMEL_WAR_C)
            .addAsManifestResource("deployment/jetty-camel-context.xml", "jetty-camel-context.xml")
            .addAsLibraries(libraryDependencies.toArray(new File[0]))
            .setManifest(() -> {
                ManifestBuilder builder = new ManifestBuilder();
                builder.addManifestHeader("Dependencies", "org.apache.camel.core,org.apache.camel.component.http.common");
                return builder.openStream();
            });
    }

    @Test
    public void testDeploymentWithOverlappingCamelPaths() throws Exception {
        /**
         * Deployment contains camel-core JAR which should conflict with subsystem exported path org/apache/camel
         *
         * The deployment should fail to deploy
         */
        try {
            deployer.deploy(CAMEL_WAR_A);
            Assert.fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            boolean logMessagePresent = LogUtils.awaitLogMessage(".*IllegalStateException.*Apache Camel library \\(camel-core-.*.jar\\) was detected within the deployment.*", 5000);
            Assert.assertTrue("Verify log message", logMessagePresent);

            CamelContext camelctx = contextRegistry.getCamelContext("simple-context");
            Assert.assertNull("Expected simple-context to be null", camelctx);
        }
    }

    @Test
    public void testDeploymentWithOverlappingOtherPaths() throws Exception {
        try {
            /**
             * Deployment contains apache-avro JAR which should conflict with subsystem exported path org/apache/avro
             *
             * Expect a WARN level message in the logs
             */
            deployer.deploy(CAMEL_WAR_B);

            boolean logMessagePresent = LogUtils.awaitLogMessage(".*WARN.*avro.*.jar contains package paths that are already exported by Camel subsystem.*", 5000);
            Assert.assertTrue("Verify log message", logMessagePresent);

            CamelContext camelctx = contextRegistry.getCamelContext("simple-context");
            Assert.assertNotNull("Expected simple-context to not be null", camelctx);
            Assert.assertEquals(ServiceStatus.Started, camelctx.getStatus());
        } finally {
            deployer.undeploy(CAMEL_WAR_B);
        }
    }

    @Test
    public void testUnsupportedCamelComponentWithModuleDependencies() throws Exception {
        try {
            /**
             * Deployment contains camel-jetty JAR which is unsupported by WFC.
             *
             * Other camel dependencies are fulfilled via JBoss modules through manifest dependencies.
             *
             * Thus no overlapping paths should be detected and we should be able to hit a Jetty consumer endpoint.
             */
            deployer.deploy(CAMEL_WAR_C);

            CamelContext camelctx = contextRegistry.getCamelContext("jetty-context");
            Assert.assertNotNull("Expected jetty-context to not be null", camelctx);
            Assert.assertEquals(ServiceStatus.Started, camelctx.getStatus());

            HttpResponse response = HttpRequest.get("http://localhost:8081/hello").getResponse();
            Assert.assertEquals("Hello Kermit", response.getBody());
        } finally {
            deployer.undeploy(CAMEL_WAR_C);
        }
    }
}
