/*
 * #%L
 * Wildfly Camel :: Subsystem
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
package org.wildfly.extension.camel.deployment;

import static org.wildfly.extension.camel.CamelLogger.LOGGER;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.AttachmentList;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.jandex.Index;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.wildfly.extension.camel.CamelConstants;

/**
 * DeploymentUnitProcessor to detect libraries that contain packages overlapping with those exported by the Camel Subsystem.
 *
 * The following scenarios are handled:
 *
 * 1. The deployment contains libraries where its packages overlap with camel packages exported by the Camel Subsystem (i.e ones prefixed with org/apache/camel).
 * In this case, an exception is thrown and the deployment is not processed any further.
 *
 * 2. The deployment contains a (non camel) library where its packages overlap with non-camel packages (i.e ones not prefixed with org/apache/camel) exported
 * by the camel subsystem. In this case, a WARN log message informing the user that overlapping paths were detected is . The deployment is allowed to proceed further.
 *
 * 3. The deployment contains libraries where none of its packages overlap with those exported by the Camel Subsystem. Deployment processing proceeds as per normal.
 */
public class CamelLibraryFilterProcessor implements DeploymentUnitProcessor {

    private static final String[] CAMEL_BASE_MODULES = {CamelConstants.CAMEL_MODULE, CamelConstants.CAMEL_COMPONENT_MODULE};

    private static final String MESSAGE_CAMEL_LIBS_DETECTED = "Apache Camel library (%s) was detected within the deployment. This library is "
            + "provided by the Camel subsystem. Either modify your deployment by replacing embedded libraries with module dependencies. "
            + "Or disable the Camel subsystem for the current deployment by adding a "
            + "jboss-deployment-structure.xml or jboss-all.xml descriptor to it.";

    private static final String MESSAGE_OVERLAPPING_PATHS_DETECTED = "{} contains package paths that are already exported by Camel subsystem. "
            + "Either provide a deployment replacing embedded libraries with module dependencies. "
            + "Or disable the Camel subsystem for the current deployment by adding a "
            + "jboss-deployment-structure.xml or jboss-all.xml descriptor to it.";

    private final ExportedPathFilter filter;

    public CamelLibraryFilterProcessor() {
        Set<String> paths = new HashSet<>();
        ModuleLoader moduleLoader = ModuleLoader.forClass(CamelLibraryFilterProcessor.class);
        try {
            for (String moduleName : CAMEL_BASE_MODULES) {
                paths.addAll(moduleLoader.loadModule(moduleName).getExportedPaths());
            }
            this.filter = new ExportedPathFilter(Collections.unmodifiableSet(paths));
        } catch (ModuleLoadException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit depUnit = phaseContext.getDeploymentUnit();
        CamelDeploymentSettings depSettings = depUnit.getAttachment(CamelDeploymentSettings.ATTACHMENT_KEY);

        // Skip filtering if this is not a recognized Camel deployment or processing an EAR
        if (!depSettings.isEnabled() || DeploymentTypeMarker.isType(DeploymentType.EAR, depUnit)) {
            return;
        }

        // Check whether anything within the deployment contains package paths which
        // overlap with those exported from the camel subsystem
        AttachmentList<ResourceRoot> resourceRoots = depUnit.getAttachment(Attachments.RESOURCE_ROOTS);
        if (resourceRoots != null) {
            for (ResourceRoot root : resourceRoots) {
                checkForOverlappingPaths(root.getAttachment(Attachments.ANNOTATION_INDEX), root);
            }
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {
    }

    private void checkForOverlappingPaths(final Index index, final ResourceRoot root) {
        boolean warnOverlappingPath = false;

        // Search in order of longest path first for better accuracy
        List<String> PackageNames = index.getKnownClasses().stream()
            .map(classInfo -> classInfo.name().toString())
            .sorted(Comparator.comparingInt(String::length).reversed())
            .collect(Collectors.toList());

        for (String packageName : PackageNames) {
            String path = packageNameToPath(packageName);
            boolean accept = filter.accept(path);

            if (!accept) {
                if (path.startsWith("org/apache/camel")) {
                    // Prevent further deployment processing as this resource contains Camel package paths exported by the subsystem
                    throw new IllegalStateException(String.format(MESSAGE_CAMEL_LIBS_DETECTED, root.getRootName()));
                } else {
                    // Else just log a warning that some other overlapping path was found
                    warnOverlappingPath = true;
                }
            }
        }

        if (warnOverlappingPath) {
            LOGGER.warn(MESSAGE_OVERLAPPING_PATHS_DETECTED, root.getRootName());
        }
    }

    private String packageNameToPath(String packageName) {
        String path = packageName.replace(".", "/");
        int lastSlash = path.lastIndexOf("/");
        return lastSlash > -1 ? path.substring(0, lastSlash) : path;
    }

    private class ExportedPathFilter {

        private final Set<String> exportedPaths;
        private final Set<String> ignoredPaths = new HashSet<>();

        private ExportedPathFilter(Set<String> exportedPaths) {
            ignoredPaths.add("org/apache/activemq");
            ignoredPaths.add("org/apache/camel");
            ignoredPaths.add("org/apache/camel/component");
            ignoredPaths.add("org/apache/camel/converter");
            ignoredPaths.add("org/apache/cxf");
            ignoredPaths.add("org/springframework");
            this.exportedPaths = exportedPaths;
        }

        boolean accept(String exportedPath) {
            // Allow exported path if it's one we're not interested in
            if (!exportedPath.contains("/") || exportedPath.startsWith("META-INF") || ignoredPaths.contains(exportedPath)) {
                return true;
            }

            // Disallow exported path if it's one already exported by the camel subsystem
            if (exportedPaths.contains(exportedPath)) {
                return false;
            }

            return true;
        }
    }
}
