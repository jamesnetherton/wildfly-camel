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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * {@link AbstractRuntimeOnlyHandler} to return registered endpoint URLs
 */
final class CamelEndpointsRuntimeHandler extends AbstractRuntimeOnlyHandler {

    private final SubsystemState subsystemState;

    CamelEndpointsRuntimeHandler(SubsystemState subsystemState) {
        this.subsystemState = subsystemState;
    }

    @Override
    protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
        String operationName = operation.require(ModelDescriptionConstants.OP).asString();
        if (ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION.equals(operationName)) {
            handleReadAttributeOperation(context, operation);
        }
    }

    private void handleReadAttributeOperation(OperationContext context, ModelNode operation) {
        String name = operation.require(ModelDescriptionConstants.NAME).asString();

        if (ModelConstants.ENDPOINTS.equals(name)) {
            List<ModelNode> values = new ArrayList<>();
            for (URL aux : subsystemState.getRuntimeState().getEndpointURLs()) {
                values.add(new ModelNode(aux.toString()));
            }
            context.getResult().set(values);
        }

        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }
}
