package org.wildfly.extension.camel.restlet;

import io.undertow.server.HttpHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletInfo;

import org.apache.camel.component.restlet.RestletComponent;
import org.apache.camel.component.restlet.RestletEndpoint;
import org.apache.camel.spi.RestConfiguration;
import org.jboss.gravia.runtime.ServiceLocator;
import org.jboss.modules.ModuleClassLoader;
import org.restlet.Component;
import org.restlet.ext.servlet.ServerServlet;
import org.wildfly.extension.undertow.Host;

public class WildFlyRestletComponent extends RestletComponent {

    private Component component;
    private Host host = ServiceLocator.getRequiredService(Host.class);
    private DeploymentManager manager;

    public WildFlyRestletComponent(Component component) {
        super(component);
        this.component = component;
    }

    @Override
    protected void addServerIfNecessary(RestletEndpoint endpoint) throws Exception {
        if (manager == null) {
            String contextPath;

            ServletInfo servletInfo = Servlets.servlet("RestletServlet", ServerServlet.class).addMapping("/*");

            RestConfiguration config = getCamelContext().getRestConfiguration("restlet", true);
            if (config.getComponentProperties() != null && !config.getComponentProperties().isEmpty()) {
                contextPath = config.getContextPath();
            } else {
                // Try to figure out the deployment context root path from the ClassLoader module name
                ModuleClassLoader classLoader = (ModuleClassLoader) getCamelContext().getApplicationContextClassLoader();
                String name = classLoader.getModule().getName();
                contextPath = "/restlet/" + name.substring(11, name.lastIndexOf('.'));
            }

            component.getContext().getAttributes().putIfAbsent("org.restlet.ext.servlet.offsetPath", contextPath);

            DeploymentInfo servletBuilder = Servlets.deployment()
                .setClassLoader(WildFlyRestletComponent.class.getClassLoader())
                .setContextPath(contextPath)
                .setDeploymentName("restletconsumer.war")
                .addServlets(servletInfo)
                .addServletContextAttribute("org.restlet.ext.servlet.ServerServlet.component.RestletServlet", component);

            manager = Servlets.defaultContainer().addDeployment(servletBuilder);
            manager.deploy();

            HttpHandler servletHandler = manager.start();
            host.registerDeployment(manager.getDeployment(), servletHandler);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (manager != null) {
            host.unregisterDeployment(manager.getDeployment());
            manager = null;
        }
    }
}
