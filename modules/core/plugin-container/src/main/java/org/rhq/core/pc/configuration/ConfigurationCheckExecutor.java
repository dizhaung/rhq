/*
 * RHQ Management Platform
 * Copyright (C) 2005-2013 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */
package org.rhq.core.pc.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.clientapi.agent.PluginContainerException;
import org.rhq.core.clientapi.server.configuration.ConfigurationServerService;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.ConfigurationUtility;
import org.rhq.core.domain.configuration.definition.ConfigurationDefinition;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.resource.InventoryStatus;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceCategory;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.pc.PluginContainer;
import org.rhq.core.pc.inventory.InventoryManager;
import org.rhq.core.pc.inventory.ResourceContainer;
import org.rhq.core.pc.util.FacetLockType;
import org.rhq.core.pluginapi.configuration.ConfigurationFacet;

/**
 * The original implementation had two main issues. The second a by-product of the first.  It did not chunk
 * the work so large inventories could generate a long configuration discovery/check with fairly significant
 * usage on the agent. The scans ran infrequently, once an hour by default.  Secondly, this job runs on
 * a single-threaded threadpool responsible for also processing on-demand configuration updates (from the GUI,
 * or remote clients).  These on-demand updates could get starved and possibly timeout waiting for a discovery run
 * to complete (BZ 1100300).
 * To solve these issues we now do the following; we chunk config checking by "roots".  A root can be the platform
 * or a top level server. So number of roots is TLS's+1.  We then run the checker more often, every 5 minutes by default.
 * Each run then starts operating on eligible roots, one at a time.  A root is eligible if it hasn't been checked for
 * rhq.agent.plugins.configuration-discovery.period-secs (this prop was somewhat re-purposed but in the end is sort
 * of the same.  it specifies the interval between checks, but not necessarily the interval between executions of the
 * checker).  Each run now has a time limit, 15s by default.  We check as many eligible roots as possible until we're
 * done or exceed the time limit.  It's not a timeout per se, we finish the root and then check our time. This means
 * an on-demand update should not have to wait more than about 20s and the agent chunks work, spreading out the checks.

 * @author Greg Hinkle
 * @author Jay Shaughnessy
 */
public class ConfigurationCheckExecutor implements Runnable, Callable {

    private static final Log log = LogFactory.getLog(ConfigurationCheckExecutor.class);

    private static final long CONFIGURATION_CHECK_TIMEOUT = 30000L;

    private ConfigurationServerService configurationServerService;

    private long checkInterval;
    private long timeLimit;

    /**
     * Map of resourceId to lastCheckTime.  This will include only config checking "root" resources; platform and
     * top-level server resources
     */
    private HashMap<Integer, Long> rootCheckTimeMap = new HashMap<Integer, Long>();

    /**
     * @param configurationServerService
     * @param checkInterval In seconds. The amount of time after a resource is checked before it again becomes eligible for a check.
     * @param timeLimit  In seconds. The executor checks one "root" resource at a time.  After completing a root it checks to see if the total runtime
     * has surpassed this time limit.  If so it defers the next root to the next run of the executor.
     */
    public ConfigurationCheckExecutor(ConfigurationServerService configurationServerService, long checkInterval,
        long timeLimit) {
        this.configurationServerService = configurationServerService;
        this.checkInterval = checkInterval;
        this.timeLimit = timeLimit;
    }

    public void run() {
        call();
    }

    public Object call() {
        InventoryManager inventoryManager = PluginContainer.getInstance().getInventoryManager();
        Resource platform = inventoryManager.getPlatform();
        List<Resource> eligibleRoots = getEligibleRoots(platform);

        if (eligibleRoots.isEmpty()) {
            log.debug("Skipping configuration update check, no eligible roots.");
            return null;
        }

        log.info("Starting configuration update check on [" + eligibleRoots.size() + "] roots...");
        CountTime totalCountTime = new CountTime();
        long start = System.currentTimeMillis();
        long elapsedSeconds = 0;
        int rootsChecked = 0;

        // check as many roots as possible until we either finish or exceed the allotted time.
        for (Resource root : eligibleRoots) {
            // TODO change to debug
            log.info("Configuration update check begin for root resource [" + root.getName() + "]");
            CountTime countTime = checkConfigurations(inventoryManager, root);
            // TODO change to debug
            log.info("Configuration update check   end for root resource [" + root.getName() + "]" + countTime);
            ++rootsChecked;
            totalCountTime.add(countTime);
            long now = System.currentTimeMillis();
            elapsedSeconds = (now - start) / 1000;
            rootCheckTimeMap.put(root.getId(), Long.valueOf(now));

            if (elapsedSeconds >= timeLimit) {
                // TODO: CHANGE TO DEBUG
                log.info("Stopping after [" + rootsChecked + "] of [" + eligibleRoots.size()
                    + "] because elapsed time [" + elapsedSeconds + "] >= time limit [" + timeLimit + "]");
                break;
            }
        }

        log.info("Configuration update check complete. Checked [" + rootsChecked + "] of [" + eligibleRoots.size()
            + "] eligible roots in [" + elapsedSeconds + "]s wall time. " + totalCountTime);

        return null;
    }

    /**
     * return a list of root resources that have not been checked within the last checkInterval period ordered
     * by least recently checked to most recently checked.
     *
     * @param platform
     * @return
     */
    private List<Resource> getEligibleRoots(Resource platform) {
        // the list of possible roots contains the platform and top level servers
        List<Resource> possibleRoots = new ArrayList<Resource>();
        possibleRoots.add(platform);
        for (Resource child : platform.getChildResources()) {
            if (ResourceCategory.SERVER == child.getResourceType().getCategory()) {
                possibleRoots.add(child);
            }
        }

        // now return the eligible roots, those that have not been checked for at least the checkInterval time.
        List<Resource> result = new ArrayList<Resource>(rootCheckTimeMap.size());
        long now = System.currentTimeMillis();

        // make sure the rootCheckTimeMap has entries for only the current possible roots
        HashMap<Integer, Long> tempRootCheckTimeMap = new HashMap<Integer, Long>();
        for (Resource r : possibleRoots) {
            Long lastCheckTime = rootCheckTimeMap.get(r.getId());
            if (null == lastCheckTime || lastCheckTime <= now - (checkInterval * 1000)) {
                result.add(r);
            }
            tempRootCheckTimeMap.put(Integer.valueOf(r.getId()),
                ((null != lastCheckTime) ? lastCheckTime : Long.valueOf(0L)));
        }
        rootCheckTimeMap.clear();
        rootCheckTimeMap.putAll(tempRootCheckTimeMap);

        // sort the eligible roots such that the least recently checked are done first
        Collections.sort(result, new Comparator<Resource>() {
            public int compare(Resource o1, Resource o2) {
                return rootCheckTimeMap.get(o1.getId()).compareTo(rootCheckTimeMap.get(o2.getId()));
            }
        });

        return result;
    }

    public CountTime checkConfigurations(InventoryManager inventoryManager, Resource resource) {
        ResourceContainer resourceContainer = inventoryManager.getResourceContainer(resource.getId());
        ConfigurationFacet resourceComponent = null;
        ResourceType resourceType = resource.getResourceType();

        CountTime countTime = new CountTime();
        boolean debugEnabled = log.isDebugEnabled();

        if (resourceContainer != null && resourceContainer.getAvailability() != null
            && resourceContainer.getAvailability().getAvailabilityType() == AvailabilityType.UP) {

            if (resourceContainer.supportsFacet(ConfigurationFacet.class)) {
                try {
                    resourceComponent = resourceContainer.createResourceComponentProxy(ConfigurationFacet.class,
                        FacetLockType.NONE, CONFIGURATION_CHECK_TIMEOUT, true, false, true);
                } catch (PluginContainerException e) {
                    // Expecting when the resource does not support configuration management
                    // Should never happen after above check
                }
            }

            if (resourceComponent != null) {
                // Only report availability for committed resources; don't bother with new, ignored or deleted resources.
                if (resource.getInventoryStatus() == InventoryStatus.COMMITTED
                    && resourceType.getResourceConfigurationDefinition() != null) {

                    long t1 = System.currentTimeMillis();

                    if (debugEnabled) {
                        log.debug("Checking for updated Resource configuration for " + resource + "...");
                    }

                    try {
                        Configuration liveConfiguration = resourceComponent.loadResourceConfiguration();

                        if (liveConfiguration != null) {
                            ConfigurationDefinition configurationDefinition = resourceType
                                .getResourceConfigurationDefinition();

                            // Normalize and validate the config.
                            ConfigurationUtility.normalizeConfiguration(liveConfiguration, configurationDefinition,
                                true, true);
                            List<String> errorMessages = ConfigurationUtility.validateConfiguration(liveConfiguration,
                                configurationDefinition);
                            for (String errorMessage : errorMessages) {
                                log.warn("Plugin Error: Invalid " + resourceType.getName()
                                    + " resource configuration returned by " + resourceType.getPlugin() + " plugin - "
                                    + errorMessage);
                            }

                            Configuration original = getResourceConfiguration(inventoryManager, resource);

                            if (original == null) {
                                original = loadConfigurationFromFile(inventoryManager, resource.getId());
                            }

                            if (!liveConfiguration.equals(original)) {
                                if (debugEnabled) {
                                    log.debug("New configuration version detected on resource: " + resource);
                                }
                                this.configurationServerService.persistUpdatedResourceConfiguration(resource.getId(),
                                    liveConfiguration);
                                //                                resource.setResourceConfiguration(liveConfiguration);
                                boolean persisted = persistConfigurationToFile(inventoryManager, resource.getId(),
                                    liveConfiguration, log);
                                if (persisted) {
                                    resource.setResourceConfiguration(null);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        log.warn("An error occurred while checking for an updated Resource configuration for "
                            + resource + ".", t);
                    }

                    long now = System.currentTimeMillis();
                    countTime.add(1, (now - t1));

                    // Give the agent some time to breathe
                    try {
                        Thread.sleep(750);
                    } catch (InterruptedException e) {
                        ; // We don't care
                    }
                }
            }

            // recurse on any child other than a top-level server, which is treated as a separate root resource.
            boolean isPlatform = null == resource.getParentResource();
            for (Resource child : inventoryManager.getContainerChildren(resource, resourceContainer)) {
                if (isPlatform && (ResourceCategory.SERVER == child.getResourceType().getCategory())) {
                    // TODO CHANGE TO DEBUG
                    log.info("Not Recursing on platform child (top-level-server [" + child.getName() + "])");
                    continue;
                }

                try {
                    CountTime inner = checkConfigurations(inventoryManager, child);
                    countTime.add(inner.count, inner.time);
                } catch (Exception e) {
                    log.error("Failed to check Resource configuration for " + child + ".", e);
                }
            }
        }

        return countTime;
    }

    static public Configuration getResourceConfiguration(InventoryManager inventoryManager, Resource resource) {
        Configuration result = resource.getResourceConfiguration();
        if (null == result) {
            result = loadConfigurationFromFile(inventoryManager, resource.getId());
        }
        return result;
    }

    static public boolean persistConfigurationToFile(InventoryManager inventoryManager, int resourceId,
        Configuration liveConfiguration, Log log) {

        boolean success = true;
        try {
            File baseDataDir = inventoryManager.getDataDirectory();
            String pathname = "rc/" + String.valueOf(resourceId / 1000); // Don't put too many files into one data dir
            File dataDir = new File(baseDataDir, pathname);
            if (!dataDir.exists()) {
                success = dataDir.mkdirs();
                if (!success) {
                    log.warn("Could not create data dir " + dataDir.getAbsolutePath());
                    return false;
                }
            }
            File file = new File(dataDir, String.valueOf(resourceId));
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(liveConfiguration);
            oos.flush();
            oos.close();
            fos.flush();
            fos.close();
        } catch (IOException e) {
            log.warn("Persisting failed: " + e.getMessage());
            success = false;
        }
        return success;

    }

    static private Configuration loadConfigurationFromFile(InventoryManager inventoryManager, int resourceId) {
        File baseDataDir = inventoryManager.getDataDirectory();
        String pathname = "rc/" + String.valueOf(resourceId / 1000); // Don't put too many files into one data dir
        File dataDir = new File(baseDataDir, pathname);
        File file = new File(dataDir, String.valueOf(resourceId));
        if (!file.exists()) {
            log.error("File " + file.getAbsolutePath() + " does not exist");
            return new Configuration();
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis);
            Configuration config = (Configuration) ois.readObject();
            ois.close();
            fis.close();
            return config;
        } catch (IOException e) {
            e.printStackTrace(); // TODO: Customize this generated block
        } catch (ClassNotFoundException e) {
            e.printStackTrace(); // TODO: Customize this generated block
        }

        return new Configuration();
    }

    private static class CountTime {
        private long count = 0L;
        private long time = 0L;

        private void add(long count, long time) {

            this.count += count;
            this.time += time;
        }

        private void add(CountTime countTime) {

            this.count += countTime.count;
            this.time += countTime.time;
        }

        @Override
        public String toString() {
            return "CountTime [checked resource count=" + count + ", time=" + time + "]";
        }

    }

}
