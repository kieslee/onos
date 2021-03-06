/*
 * Copyright 2018-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.openstackvtap.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.VlanId;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.core.GroupId;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostLocation;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.ExtensionTreatmentResolver;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.DefaultDriverData;
import org.onosproject.net.driver.DefaultDriverHandler;
import org.onosproject.net.driver.Driver;
import org.onosproject.net.driver.DriverHandler;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleOperations;
import org.onosproject.net.flow.FlowRuleOperationsContext;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.instructions.ExtensionTreatment;
import org.onosproject.net.flow.instructions.ExtensionTreatmentType;
import org.onosproject.net.group.DefaultGroupBucket;
import org.onosproject.net.group.DefaultGroupDescription;
import org.onosproject.net.group.DefaultGroupKey;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupKey;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.openstackvtap.api.OpenstackVtap;
import org.onosproject.openstackvtap.api.OpenstackVtapAdminService;
import org.onosproject.openstackvtap.api.OpenstackVtapCriterion;
import org.onosproject.openstackvtap.api.OpenstackVtapEvent;
import org.onosproject.openstackvtap.api.OpenstackVtapListener;
import org.onosproject.openstackvtap.api.OpenstackVtapService;
import org.onosproject.openstackvtap.api.OpenstackVtapStore;
import org.onosproject.openstackvtap.api.OpenstackVtapStoreDelegate;
import org.onosproject.openstackvtap.api.OpenstackVtap.Type;
import org.onosproject.openstackvtap.api.OpenstackVtapId;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.onlab.packet.Ethernet.TYPE_IPV4;
import static org.onlab.packet.IPv4.PROTOCOL_ICMP;
import static org.onlab.packet.IPv4.PROTOCOL_TCP;
import static org.onlab.packet.IPv4.PROTOCOL_UDP;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.openstacknetworking.api.Constants.DHCP_ARP_TABLE;
import static org.onosproject.openstacknetworking.api.Constants.FLAT_TABLE;
import static org.onosproject.openstacknetworking.api.Constants.FORWARDING_TABLE;
import static org.onosproject.openstacknetworking.api.Constants.VTAP_FLAT_OUTBOUND_GROUP_TABLE;
import static org.onosproject.openstacknetworking.api.Constants.VTAP_FLAT_OUTBOUND_MIRROR_TABLE;
import static org.onosproject.openstacknetworking.api.Constants.VTAP_FLAT_OUTBOUND_TABLE;
import static org.onosproject.openstacknetworking.api.Constants.VTAP_INBOUND_GROUP_TABLE;
import static org.onosproject.openstacknetworking.api.Constants.VTAP_INBOUND_MIRROR_TABLE;
import static org.onosproject.openstacknetworking.api.Constants.VTAP_INBOUND_TABLE;
import static org.onosproject.openstacknetworking.api.Constants.VTAP_OUTBOUND_GROUP_TABLE;
import static org.onosproject.openstacknetworking.api.Constants.VTAP_OUTBOUND_MIRROR_TABLE;
import static org.onosproject.openstacknetworking.api.Constants.VTAP_OUTBOUND_TABLE;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provides basic implementation of the user APIs.
 */
@Component(immediate = true)
@Service
public class OpenstackVtapManager
        extends AbstractListenerManager<OpenstackVtapEvent, OpenstackVtapListener>
        implements OpenstackVtapService, OpenstackVtapAdminService {

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LeadershipService leadershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DriverService driverService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected OpenstackVtapStore store;

    public static final String APP_ID = "org.onosproject.openstackvtap";

    public static final String VTAP_ID_NULL = "OpenstackVtap ID cannot be null";
    public static final String VTAP_DESC_NULL = "OpenstackVtap fields cannot be null";
    public static final String DEVICE_ID_NULL = "Device ID cannot be null";

    private static final int PRIORITY_VTAP_RULE = 50000;
    private static final int PRIORITY_VTAP_OUTPORT_RULE = 1000;
    private static final int PRIORITY_VTAP_DROP = 0;

    private static final int NONE_TABLE = -1;
    private static final int INBOUND_NEXT_TABLE = DHCP_ARP_TABLE;
    private static final int FLAT_OUTBOUND_NEXT_TABLE = FLAT_TABLE;
    private static final int OUTBOUND_NEXT_TABLE = FORWARDING_TABLE;

    private static final String VTAP_GROUP_KEY = "VTAP_GROUP_KEY";

    private final DeviceListener deviceListener = new InternalDeviceListener();
    private final HostListener hostListener = new InternalHostListener();

    private OpenstackVtapStoreDelegate delegate = new InternalStoreDelegate();

    private ApplicationId appId;
    private NodeId localNodeId;
    private ScheduledExecutorService eventExecutor;


    @Activate
    public void activate(ComponentContext context) {
        appId = coreService.registerApplication(APP_ID);
        localNodeId = clusterService.getLocalNode().id();
        leadershipService.runForLeadership(appId.name());

        eventExecutor = newSingleThreadScheduledExecutor(
                groupedThreads(this.getClass().getSimpleName(), "event-handler", log));

        store.setDelegate(delegate);
        eventDispatcher.addSink(OpenstackVtapEvent.class, listenerRegistry);

        deviceService.addListener(deviceListener);
        hostService.addListener(hostListener);

        log.info("Started {} - {}", appId.name(), this.getClass().getSimpleName());
    }

    @Deactivate
    public void deactivate() {
        hostService.removeListener(hostListener);
        deviceService.removeListener(deviceListener);

        eventDispatcher.removeSink(OpenstackVtapEvent.class);
        store.unsetDelegate(delegate);

        eventExecutor.shutdown();
        leadershipService.withdraw(appId.name());

        log.info("Stopped {} - {}", appId.name(), this.getClass().getSimpleName());
    }

    @Override
    public int getVtapCount(Type type) {
        return store.getVtapCount(type);
    }

    @Override
    public Set<OpenstackVtap> getVtaps(Type type) {
        return store.getVtaps(type);
    }

    @Override
    public OpenstackVtap getVtap(OpenstackVtapId vTapId) {
        checkNotNull(vTapId, VTAP_ID_NULL);
        return store.getVtap(vTapId);
    }

    @Override
    public Set<OpenstackVtap> getVtapsByDeviceId(OpenstackVtap.Type type, DeviceId deviceId) {
        checkNotNull(deviceId, DEVICE_ID_NULL);
        return store.getVtapsByDeviceId(type, deviceId);
    }

    private Set<DeviceId> getEdgeDevice(Type type, OpenstackVtapCriterion vTapCriterionOpenstack) {
        Set<DeviceId> deviceIds = Sets.newConcurrentHashSet();
        StreamSupport.stream(hostService.getHosts().spliterator(), true)
                .forEach(host -> {
                    if (host.ipAddresses().stream().anyMatch(ipAddress ->
                            (type.isValid(Type.VTAP_TX) &&
                                    vTapCriterionOpenstack.srcIpPrefix().contains(ipAddress)) ||
                                    (type.isValid(Type.VTAP_RX) &&
                                            vTapCriterionOpenstack.dstIpPrefix().contains(ipAddress)))) {
                        deviceIds.addAll(host.locations().stream()
                                .map(HostLocation::deviceId)
                                .collect(Collectors.toSet()));
                    }
                });
        return deviceIds;
    }

    @Override
    public OpenstackVtap createVtap(Type type, OpenstackVtapCriterion vTapCriterionOpenstack) {
        checkNotNull(vTapCriterionOpenstack, VTAP_DESC_NULL);

        Set<DeviceId> txDevices = type.isValid(Type.VTAP_TX) ?
                getEdgeDevice(type, vTapCriterionOpenstack) : ImmutableSet.of();
        Set<DeviceId> rxDevices = type.isValid(Type.VTAP_RX) ?
                getEdgeDevice(type, vTapCriterionOpenstack) : ImmutableSet.of();

        OpenstackVtap description = DefaultOpenstackVtap.builder()
                                                        .id(OpenstackVtapId.vTapId())
                                                        .type(type)
                                                        .vTapCriterion(vTapCriterionOpenstack)
                                                        .txDeviceIds(txDevices)
                                                        .rxDeviceIds(rxDevices)
                                                        .build();
        return store.createOrUpdateVtap(description.id(), description, true);
    }

    @Override
    public OpenstackVtap updateVtap(OpenstackVtapId vTapId, OpenstackVtap vTap) {
        checkNotNull(vTapId, VTAP_ID_NULL);
        checkNotNull(vTap, VTAP_DESC_NULL);

        if (store.getVtap(vTapId) == null) {
            return null;
        }

        Set<DeviceId> txDevices = vTap.type().isValid(Type.VTAP_TX) ?
                getEdgeDevice(vTap.type(), vTap.vTapCriterion()) : ImmutableSet.of();
        Set<DeviceId> rxDevices = vTap.type().isValid(Type.VTAP_RX) ?
                getEdgeDevice(vTap.type(), vTap.vTapCriterion()) : ImmutableSet.of();

        DefaultOpenstackVtap description = DefaultOpenstackVtap.builder()
                                                                .id(vTapId)
                                                                .type(vTap.type())
                                                                .vTapCriterion(vTap.vTapCriterion())
                                                                .txDeviceIds(txDevices)
                                                                .rxDeviceIds(rxDevices)
                                                                .build();
        return store.createOrUpdateVtap(vTapId, description, true);
    }

    @Override
    public OpenstackVtap removeVtap(OpenstackVtapId vTapId) {
        checkNotNull(vTapId, VTAP_ID_NULL);
        return store.removeVtapById(vTapId);
    }

    @Override
    public void setVtapOutput(DeviceId deviceId, OpenstackVtap.Type type, PortNumber portNumber, VlanId vlanId) {
        // Make output table
        if (type.isValid(Type.VTAP_TX)) {
            createOutputTable(deviceId, VTAP_INBOUND_MIRROR_TABLE, portNumber, vlanId);
        }
        if (type.isValid(Type.VTAP_RX)) {
            createOutputTable(deviceId, VTAP_FLAT_OUTBOUND_MIRROR_TABLE, portNumber, vlanId);
            createOutputTable(deviceId, VTAP_OUTBOUND_MIRROR_TABLE, portNumber, vlanId);
        }
    }

    @Override
    public void setVtapOutput(DeviceId deviceId, Type type, PortNumber portNumber, int vni) {
        // TODO: need to provide implementation
    }

    // Triggers driver setup when a host is (re)detected.
    private class InternalHostListener implements HostListener {
        @Override
        public boolean isRelevant(HostEvent event) {
            // do not allow to proceed without leadership
            NodeId leader = leadershipService.getLeader(appId.name());
            return Objects.equals(localNodeId, leader);
        }

        @Override
        public void event(HostEvent event) {
            HostEvent.Type type = event.type();
            Host host = event.subject();
            log.trace("InternalHostListener hostId={}, type={}", host.id(), type);

            switch (type) {
                case HOST_ADDED:
                    eventExecutor.execute(() -> updateHost(host, null));
                    break;

                case HOST_REMOVED:
                    eventExecutor.execute(() -> updateHost(null, host));
                    break;

                case HOST_UPDATED:
                case HOST_MOVED:
                    eventExecutor.execute(() -> updateHost(host, event.prevSubject()));
                    break;
                default:
                    break;
            }
        }
    }

    private boolean hostDifference(Host host1, Host host2, IpPrefix ipPrefix) {
        return ((host1 != null && host1.ipAddresses().stream().anyMatch(ipPrefix::contains)) &&
                (host2 == null || host2.ipAddresses().stream().noneMatch(ipPrefix::contains)));
    }

    private void updateHost(Host newHost, Host oldHost) {
        // update devices for vTap tx
        getVtaps(Type.VTAP_TX).parallelStream().forEach(vTap -> {
            if (hostDifference(oldHost, newHost, vTap.vTapCriterion().srcIpPrefix())) {
                oldHost.locations().stream()
                        .map(HostLocation::deviceId)
                        .forEach(deviceId ->
                                store.removeDeviceFromVtap(vTap.id(),
                                        Type.VTAP_TX, oldHost.location().deviceId()));
            }
            if (hostDifference(newHost, oldHost, vTap.vTapCriterion().srcIpPrefix())) {
                newHost.locations().stream()
                        .map(HostLocation::deviceId)
                        .forEach(deviceId ->
                                store.addDeviceToVtap(vTap.id(), Type.VTAP_TX,
                                        newHost.location().deviceId()));
            }
        });

        // update devices for vTap rx
        getVtaps(Type.VTAP_RX).parallelStream().forEach(vTap -> {
            if (hostDifference(oldHost, newHost, vTap.vTapCriterion().dstIpPrefix())) {
                oldHost.locations().stream()
                        .map(HostLocation::deviceId)
                        .forEach(deviceId ->
                                store.removeDeviceFromVtap(vTap.id(), Type.VTAP_RX,
                                        oldHost.location().deviceId()));
            }
            if (hostDifference(newHost, oldHost, vTap.vTapCriterion().dstIpPrefix())) {
                newHost.locations().stream()
                        .map(HostLocation::deviceId)
                        .forEach(deviceId ->
                                store.addDeviceToVtap(vTap.id(), Type.VTAP_RX,
                                        newHost.location().deviceId()));
            }
        });
    }

    // Triggers driver setup when a device is (re)detected.
    private class InternalDeviceListener implements DeviceListener {
        @Override
        public boolean isRelevant(DeviceEvent event) {
            // do not allow to proceed without Mastership
            DeviceId deviceId = event.subject().id();
            return mastershipService.isLocalMaster(deviceId);
        }

        @Override
        public void event(DeviceEvent event) {
            DeviceEvent.Type type = event.type();
            DeviceId deviceId = event.subject().id();
            log.trace("InternalDeviceListener deviceId={}, type={}", deviceId, type);

            switch (type) {
                case DEVICE_ADDED:
                    eventExecutor.execute(() -> updateDevice(deviceId));
                    break;
                default:
                    break;
            }
        }
    }

    private void updateDevice(DeviceId deviceId) {
        // Make vTap pipeline
        // TODO: need to selective creation by store device consistentMap
        createVtapPipeline(deviceId);

        // Install tx filter
        getVtapsByDeviceId(Type.VTAP_TX, deviceId).forEach(vTap -> {
            connectTables(deviceId,
                    VTAP_INBOUND_TABLE, NONE_TABLE, VTAP_INBOUND_GROUP_TABLE,
                    vTap.vTapCriterion(), PRIORITY_VTAP_RULE, true);
        });

        // Install rx filter
        getVtapsByDeviceId(Type.VTAP_RX, deviceId).forEach(vTap -> {
            connectTables(deviceId,
                    VTAP_FLAT_OUTBOUND_TABLE, NONE_TABLE, VTAP_FLAT_OUTBOUND_GROUP_TABLE,
                    vTap.vTapCriterion(), PRIORITY_VTAP_RULE, true);
            connectTables(deviceId,
                    VTAP_OUTBOUND_TABLE, NONE_TABLE, VTAP_OUTBOUND_GROUP_TABLE,
                    vTap.vTapCriterion(), PRIORITY_VTAP_RULE, true);
        });
    }

    private void clearRules() {
        flowRuleService.removeFlowRulesById(appId);
        deviceService.getDevices().forEach(device -> {
            groupService.getGroups(device.id(), appId).forEach(group -> {
                groupService.removeGroup(device.id(), group.appCookie(), appId);
            });
        });
        log.info("OpenstackVtap rules are cleared.");
    }

    private void installFilterRule(Set<DeviceId> txDeviceIds, Set<DeviceId> rxDeviceIds,
                                   OpenstackVtapCriterion vTapCriterionOpenstack, boolean install) {
        final int inbound = 0;
        final int flatOutbound = 1;
        final int outbound = 2;

        BiFunction<Set<DeviceId>, Integer, Void> installFlow = (deviceIds, table) -> {
            int inTable = (table == inbound ? VTAP_INBOUND_TABLE :
                    (table == flatOutbound ? VTAP_FLAT_OUTBOUND_TABLE : VTAP_OUTBOUND_TABLE));
            int outGroup = (table == inbound ? VTAP_INBOUND_GROUP_TABLE :
                    (table == flatOutbound ? VTAP_FLAT_OUTBOUND_GROUP_TABLE : VTAP_OUTBOUND_GROUP_TABLE));
            deviceIds.stream()
                    .filter(deviceId -> mastershipService.isLocalMaster(deviceId))
                    .forEach(deviceId -> {
                        connectTables(deviceId,
                                inTable, NONE_TABLE, outGroup,
                                vTapCriterionOpenstack, PRIORITY_VTAP_RULE, install);
                    });
            return null;
        };

        installFlow.apply(txDeviceIds, inbound);
        installFlow.apply(rxDeviceIds, flatOutbound);
        installFlow.apply(rxDeviceIds, outbound);
    }

    // Store delegate to re-post events emitted from the store.
    private class InternalStoreDelegate implements OpenstackVtapStoreDelegate {
        @Override
        public void notify(OpenstackVtapEvent event) {
            OpenstackVtapEvent.Type type = event.type();
            OpenstackVtap vTap = event.subject();
            log.trace("VTapStoreDelegate vTap={}, type={}", vTap, type);

            switch (type) {
                case VTAP_ADDED:
                    eventExecutor.execute(() -> {
                        // Add new devices
                        installFilterRule(vTap.txDeviceIds(), vTap.rxDeviceIds(),
                                vTap.vTapCriterion(), true);
                    });
                    break;

                case VTAP_UPDATED:
                    OpenstackVtap oldOpenstackVtap = event.prevSubject();
                    eventExecutor.execute(() -> {
                        // Remove excluded devices
                        installFilterRule(Sets.difference(oldOpenstackVtap.txDeviceIds(), vTap.txDeviceIds()),
                                Sets.difference(oldOpenstackVtap.rxDeviceIds(), vTap.rxDeviceIds()),
                                oldOpenstackVtap.vTapCriterion(), false);

                        // Add new devices
                        installFilterRule(Sets.difference(vTap.txDeviceIds(), oldOpenstackVtap.txDeviceIds()),
                                Sets.difference(vTap.rxDeviceIds(), oldOpenstackVtap.rxDeviceIds()),
                                vTap.vTapCriterion(), true);
                    });
                    break;

                case VTAP_REMOVED:
                    eventExecutor.execute(() -> {
                        // Remove excluded devices
                        installFilterRule(vTap.txDeviceIds(), vTap.rxDeviceIds(),
                                vTap.vTapCriterion(), false);
                    });
                    break;
                default:
                    break;
            }
            post(event);
        }
    }

    private void applyFlowRule(FlowRule flowRule, boolean install) {
        FlowRuleOperations.Builder flowOpsBuilder = FlowRuleOperations.builder();

        flowOpsBuilder = install ? flowOpsBuilder.add(flowRule) : flowOpsBuilder.remove(flowRule);

        flowRuleService.apply(flowOpsBuilder.build(new FlowRuleOperationsContext() {
            @Override
            public void onSuccess(FlowRuleOperations ops) {
                log.trace("Provisioned vni or forwarding table");
                //log.trace("{}", ops.toString());
            }

            @Override
            public void onError(FlowRuleOperations ops) {
                log.error("Failed to privision vni or forwarding table");
                //log.error("{}", ops.toString());
            }
        }));
    }

    private void connectTables(DeviceId deviceId, int fromTable, int toTable, int toGroup,
                               OpenstackVtapCriterion vTapCriterionOpenstack, int rulePriority,
                               boolean install) {
        log.trace("Table Transition: table[{}] -> table[{}] or group[{}]", fromTable, toTable, toGroup);

        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder()
                .matchEthType(TYPE_IPV4)
                .matchIPSrc(vTapCriterionOpenstack.srcIpPrefix())
                .matchIPDst(vTapCriterionOpenstack.dstIpPrefix());

        switch (vTapCriterionOpenstack.ipProtocol()) {
            case PROTOCOL_TCP:
                selectorBuilder.matchIPProtocol(vTapCriterionOpenstack.ipProtocol());
                if (vTapCriterionOpenstack.srcTpPort().toInt() > 0) {
                    selectorBuilder.matchTcpSrc(vTapCriterionOpenstack.srcTpPort());
                }
                if (vTapCriterionOpenstack.dstTpPort().toInt() > 0) {
                    selectorBuilder.matchTcpDst(vTapCriterionOpenstack.dstTpPort());
                }
                break;
            case PROTOCOL_UDP:
                selectorBuilder.matchIPProtocol(vTapCriterionOpenstack.ipProtocol());
                if (vTapCriterionOpenstack.srcTpPort().toInt() > 0) {
                    selectorBuilder.matchUdpSrc(vTapCriterionOpenstack.srcTpPort());
                }
                if (vTapCriterionOpenstack.dstTpPort().toInt() > 0) {
                    selectorBuilder.matchUdpDst(vTapCriterionOpenstack.dstTpPort());
                }
                break;
            case PROTOCOL_ICMP:
                selectorBuilder.matchIPProtocol(vTapCriterionOpenstack.ipProtocol());
                break;
            default:
                break;
        }

        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        if (toTable != NONE_TABLE) {
            treatmentBuilder.transition(toTable);
        } else if (toGroup != NONE_TABLE) {
            treatmentBuilder.group(GroupId.valueOf(toGroup));
        } else {
            log.warn("Not specified toTable or toGroup value");
            return;
        }

        FlowRule flowRule = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .withSelector(selectorBuilder.build())
                .withTreatment(treatmentBuilder.build())
                .withPriority(rulePriority)
                .fromApp(appId)
                .makePermanent()
                .forTable(fromTable)
                .build();

        applyFlowRule(flowRule, install);
    }

    private void createOutputTable(DeviceId deviceId, int tableId,
                                   PortNumber outPort, VlanId vlanId) {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();

        // Set output port & vlan
        int priority = PRIORITY_VTAP_DROP;
        if (vlanId != null) {
            treatment.pushVlan().setVlanId(vlanId);
        }
        if (outPort != null) {
            treatment.setOutput(outPort);
            priority = PRIORITY_VTAP_OUTPORT_RULE;
        }

        FlowRule flowRule = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .withSelector(selector.build())
                .withTreatment(treatment.build())
                .withPriority(priority)
                .makePermanent()
                .forTable(tableId)
                .fromApp(appId)
                .build();
        applyFlowRule(flowRule, true);
    }

    private GroupKey getGroupKey(int groupId) {
        return new DefaultGroupKey((VTAP_GROUP_KEY + Integer.toString(groupId)).getBytes());
    }

    private ExtensionTreatment buildNiciraExtenstion(DeviceId id, int tableId) {
        Driver driver = driverService.getDriver(id);
        DriverHandler driverHandler = new DefaultDriverHandler(new DefaultDriverData(driver, id));
        ExtensionTreatmentResolver resolver = driverHandler.behaviour(ExtensionTreatmentResolver.class);

        ExtensionTreatment extensionInstruction = resolver.getExtensionInstruction(
                ExtensionTreatmentType.ExtensionTreatmentTypes.NICIRA_RESUBMIT_TABLE.type());

        try {
            extensionInstruction.setPropertyValue("table", ((short) tableId));
        } catch (Exception e) {
            log.error("Failed to set extension treatment for resubmit table {}", id);
        }

        return extensionInstruction;
    }

    private void createGroupTable(DeviceId deviceId, int groupId,
                                  List<Integer> tableIds, List<PortNumber> ports) {
        List<GroupBucket> buckets = Lists.newArrayList();
        tableIds.forEach(tableId -> {
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                    .extension(buildNiciraExtenstion(deviceId, tableId), deviceId);
            GroupBucket bucket = DefaultGroupBucket
                    .createAllGroupBucket(treatment.build());
            buckets.add(bucket);
        });
        ports.forEach(port -> {
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                    .setOutput(port);
            GroupBucket bucket = DefaultGroupBucket
                    .createAllGroupBucket(treatment.build());
            buckets.add(bucket);
        });

        GroupDescription groupDescription = new DefaultGroupDescription(deviceId,
                GroupDescription.Type.ALL,
                new GroupBuckets(buckets),
                getGroupKey(groupId),
                groupId,
                appId);
        groupService.addGroup(groupDescription);
    }

    private void createVtapPipeline(DeviceId deviceId) {
        // Clear all flow rules & group tables
        clearRules();

        // Make output table
        createOutputTable(deviceId, VTAP_INBOUND_MIRROR_TABLE, null, null);
        createOutputTable(deviceId, VTAP_FLAT_OUTBOUND_MIRROR_TABLE, null, null);
        createOutputTable(deviceId, VTAP_OUTBOUND_MIRROR_TABLE, null, null);

        // Make tx group table
        createGroupTable(deviceId, VTAP_INBOUND_GROUP_TABLE,
                ImmutableList.of(INBOUND_NEXT_TABLE, VTAP_INBOUND_MIRROR_TABLE),
                ImmutableList.of());

        // Make rx group table
        createGroupTable(deviceId, VTAP_FLAT_OUTBOUND_GROUP_TABLE,
                ImmutableList.of(FLAT_OUTBOUND_NEXT_TABLE, VTAP_FLAT_OUTBOUND_MIRROR_TABLE),
                ImmutableList.of());
        createGroupTable(deviceId, VTAP_OUTBOUND_GROUP_TABLE,
                ImmutableList.of(OUTBOUND_NEXT_TABLE, VTAP_OUTBOUND_MIRROR_TABLE),
                ImmutableList.of());
    }
}
