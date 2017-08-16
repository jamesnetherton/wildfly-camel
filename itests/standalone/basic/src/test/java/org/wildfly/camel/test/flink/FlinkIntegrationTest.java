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
package org.wildfly.camel.test.flink;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.naming.InitialContext;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.flink.DataSetCallback;
import org.apache.camel.component.flink.Flinks;
import org.apache.camel.component.flink.annotations.AnnotatedDataSetCallback;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.common.utils.TestUtils;
import org.wildfly.extension.camel.CamelAware;
import static org.apache.camel.component.flink.FlinkConstants.FLINK_DATASET_CALLBACK_HEADER;

@CamelAware
@RunWith(Arquillian.class)
public class FlinkIntegrationTest {

    private static final String DATA_SET = "dataSet";
    private static final Long DATA_SET_LINES = 19L;
    private static final Path DATA_PATH = Paths.get(System.getProperty("jboss.server.data.dir"), "flink");
    private static ExecutionEnvironment executionEnvironment = Flinks.createExecutionEnvironment();

    @ArquillianResource
    private InitialContext initialContext;

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "camel-flink-tests.jar")
            .addClass(TestUtils.class)
            .addAsResource("flink/dataset.txt", "dataset.txt");
    }

    @Before
    public void setUp() throws Exception {
        DATA_PATH.toFile().mkdir();
        String resource = TestUtils.getResourceValue(FlinkIntegrationTest.class, "/dataset.txt");
        Files.write(DATA_PATH.resolve("dataset.txt"), resource.getBytes());

        initialContext.bind(DATA_SET, executionEnvironment.readTextFile(DATA_PATH.toString()));
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.deleteFile(DATA_PATH.resolve("dataset.txt").toFile().getParentFile());
        initialContext.unbind(DATA_SET);
    }

    @Test
    public void testFlinkDatasetCallback() throws Exception {
        CamelContext camelctx = new DefaultCamelContext();
        camelctx.start();

        try {
            ProducerTemplate template = camelctx.createProducerTemplate();
            Long linesCounted = template.requestBodyAndHeader("flink:dataSet?dataSet=#" + DATA_SET, null, FLINK_DATASET_CALLBACK_HEADER, new DataSetCallback() {
                @Override
                public Object onDataSet(DataSet ds, Object... payloads) {
                    try {
                        return ds.count();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            }, Long.class);

            Assert.assertEquals(DATA_SET_LINES, linesCounted);
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void testFlinkDatasetCallbackWithMessageBody() throws Exception {
        CamelContext camelctx = new DefaultCamelContext();
        camelctx.start();
        try {
            ProducerTemplate template = camelctx.createProducerTemplate();
            Long linesCounted = template.requestBodyAndHeader("flink:dataSet?dataSet=#" + DATA_SET, 10, FLINK_DATASET_CALLBACK_HEADER, new DataSetCallback() {
                @Override
                public Object onDataSet(DataSet ds, Object... payloads) {
                    try {
                        return ds.count() * (int) payloads[0];
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            }, Long.class);

            Assert.assertEquals((Long)(DATA_SET_LINES * 10), linesCounted);
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void testAnnotatedDatasetCallback() throws Exception {
        CamelContext camelctx = new DefaultCamelContext();
        camelctx.start();
        try {
            ProducerTemplate template = camelctx.createProducerTemplate();
            DataSetCallback dataSetCallback = new AnnotatedDataSetCallback(new Object() {
                @org.apache.camel.component.flink.annotations.DataSetCallback
                Long countLines(DataSet<String> textFile, int first, int second) {
                    try {
                        return textFile.count() * first * second;
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            });

            Long linesCounted = template.requestBodyAndHeader("flink:dataSet?dataSet=#"+ DATA_SET, Arrays.asList(10, 10),
                FLINK_DATASET_CALLBACK_HEADER, dataSetCallback, Long.class);

            Assert.assertEquals((Long)(DATA_SET_LINES * 10 * 10), linesCounted);
        } finally {
            camelctx.stop();
        }
    }
}
