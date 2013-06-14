/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.camel.test.smoke;

import java.io.InputStream;
import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.osgi.metadata.ManifestBuilder;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.wildfly.camel.CamelContextFactory;
import org.wildfly.camel.test.smoke.subA.BeanTransformActivator;
import org.wildfly.camel.test.smoke.subA.HelloBean;

/**
 * Deploys a module/bundle which contain a {@link HelloBean}.
 *
 * The tests then build a route that uses the bean through the Camel API.
 * This verifies access to beans within the same deployemnt that uses the Camel API.
 *
 * @author thomas.diesler@jboss.com
 * @since 24-Apr-2013
 */
@RunWith(Arquillian.class)
public class BeanTransformTestCase {

    static final String CAMEL_BUNDLE = "camel-bundle.jar";

    @ArquillianResource
    BundleContext context;

    @ArquillianResource
    CamelContextFactory contextFactory;

    @ArquillianResource
    Deployer deployer;

    @Deployment
    public static JavaArchive createdeployment() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bean-transform-tests");
        archive.addClasses(HelloBean.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                ManifestBuilder builder = ManifestBuilder.newInstance();
                builder.addManifestHeader("Dependencies", "org.apache.camel");
                return builder.openStream();
            }
        });
        return archive;
    }

    @Test
    public void testSimpleTransformFromModule() throws Exception {
        CamelContext camelctx = contextFactory.createWilflyCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(HelloBean.class);
            }
        });
        camelctx.start();
        ProducerTemplate producer = camelctx.createProducerTemplate();
        String result = producer.requestBody("direct:start", "Kermit", String.class);
        Assert.assertEquals("Hello Kermit", result);
    }

    @Test
    public void testSimpleTransformFromBundle() throws Exception {
        InputStream input = deployer.getDeployment(CAMEL_BUNDLE);
        Bundle bundle = context.installBundle(CAMEL_BUNDLE, input);
        try {
            bundle.start();
            String filter = "(name=" + CAMEL_BUNDLE + ")";
            BundleContext context = bundle.getBundleContext();
            Collection<ServiceReference<CamelContext>> srefs = context.getServiceReferences(CamelContext.class, filter);
            CamelContext camelctx = context.getService(srefs.iterator().next());
            ProducerTemplate producer = camelctx.createProducerTemplate();
            String result = producer.requestBody("direct:start", "Kermit", String.class);
            Assert.assertEquals("Hello Kermit", result);
        } finally {
            bundle.uninstall();
        }
    }

    @Deployment(name = CAMEL_BUNDLE, managed = false, testable = false)
    public static JavaArchive getBundle() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, CAMEL_BUNDLE);
        archive.addClasses(BeanTransformActivator.class, HelloBean.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addBundleActivator(BeanTransformActivator.class);
                builder.addImportPackages(CamelContext.class, RouteBuilder.class, DefaultCamelContext.class, RouteDefinition.class);
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}