/*
 * #%L
 * Wildfly Camel :: Subsystem
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
package org.wildfly.extension.camel.parser;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.extension.camel.CamelConstants;
import org.wildfly.extension.camel.service.CamelContextRegistryService;

/**
 * {@link AbstractRuntimeOnlyHandler} to return camel context health status
 */
final class CamelHealthRuntimeHandler extends AbstractRuntimeOnlyHandler {

    @Override
    protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
        String operationName = operation.require(ModelDescriptionConstants.OP).asString();
        if (ModelConstants.HEALTH.equals(operationName)) {
            handleCamelHealthOperation(context, operation);
        }
    }

    private void handleCamelHealthOperation(OperationContext context, ModelNode operation) {
        ModelNode result = new ModelNode();
        String contextName = operation.get(ModelConstants.CONTEXT).asStringOrNull();
        CamelHealthState healthState = CamelHealthState.HEALTHY;

        ServiceRegistry serviceRegistry = context.getServiceRegistry(false);
        ServiceController<CamelContextRegistryService.MutableCamelContextRegistry> controller = (ServiceController<CamelContextRegistryService.MutableCamelContextRegistry>) serviceRegistry.getService(
            CamelConstants.CAMEL_CONTEXT_REGISTRY_SERVICE_NAME);
        if (controller == null) {
            healthState = CamelHealthState.UNHEALTHY;
        }

        CamelContextRegistryService.MutableCamelContextRegistry contextRegistry = controller.getValue();
        if (context == null) {
            healthState = CamelHealthState.UNHEALTHY;
        }

        // If health status is UNHEALTHY at this point then we cannot proceed to do further checks
        if (healthState.equals(CamelHealthState.UNHEALTHY)) {
            result.get("status").set(healthState.name());
            context.getResult().set(result);
            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            return;
        }

        Set<CamelContext> contexts = contextRegistry.getCamelContexts();
        if (contextName != null && !contextName.isEmpty()) {
            contexts = contextRegistry.getCamelContexts()
                .stream()
                .filter(camelctx -> camelctx.getName().equals(contextName))
                .collect(Collectors.toSet());
        }

        for (CamelContext camelctx : contexts) {
            ModelNode camelContextNode = new ModelNode();
            ModelNode contextHealthNode = new ModelNode();
            CamelHealthState contextHealthState = CamelHealthState.HEALTHY;

            // If the context is not started then we must be unhealthy
            if (!camelctx.getStatus().equals(ServiceStatus.Started)) {
                contextHealthState = CamelHealthState.UNHEALTHY;
            } else {
                // Check the status of all configured health checks
                Collection<HealthCheck.Result> results = HealthCheckHelper.invoke(camelctx);
                for (HealthCheck.Result healthResult : results) {
                    if (!healthResult.getState().equals(HealthCheck.State.UP)) {
                        contextHealthState = CamelHealthState.UNHEALTHY;
                    }
                }
            }

            // Update the aggregated health status
            if (!contextHealthState.equals(CamelHealthState.HEALTHY)) {
                healthState = CamelHealthState.UNHEALTHY;
            }

            contextHealthNode.get().set("status", contextHealthState.name());
            camelContextNode.get().set(contextHealthNode);
            result.get(camelctx.getName()).set(camelContextNode);
        }

        result.get("status").set(healthState.name());
        context.getResult().set(result);

        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }
}
