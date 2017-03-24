/*
 * Copyright 2017-present Open Networking Laboratory
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

package org.onosproject.l3vpn.netl3vpn;

import org.onosproject.net.DeviceId;
import org.onosproject.net.driver.DriverService;
import org.onosproject.yang.model.ModelObjectData;

import java.util.LinkedList;
import java.util.List;

/**
 * Representation of standard device model, with interface, instance and its
 * respective device id.
 */
public class DeviceInfo {

    /**
     * Device id of the device.
     */
    private final DeviceId deviceId;

    /**
     * BGP information of the device.
     */
    private BgpInfo bgpInfo;

    /**
     * List of interface names of the device.
     */
    private List<String> ifNames;

    /**
     * List of network access of the device.
     */
    private List<AccessInfo> accesses;

    /**
     * Constructs device info with a device id.
     *
     * @param d device id
     */
    public DeviceInfo(DeviceId d) {
        deviceId = d;
    }

    /**
     * Returns the device id.
     *
     * @return device id
     */
    DeviceId deviceId() {
        return deviceId;
    }

    /**
     * Adds a interface name to the list.
     *
     * @param ifName interface name
     */
    void addIfName(String ifName) {
        if (ifNames == null) {
            ifNames = new LinkedList<>();
        }
        ifNames.add(ifName);
    }

    /**
     * Returns the list of interface name.
     *
     * @return interface names
     */
    List<String> ifNames() {
        return ifNames;
    }

    /**
     * Sets the list of interface name.
     *
     * @param ifNames interface names
     */
    void ifNames(List<String> ifNames) {
        this.ifNames = ifNames;
    }

    /**
     * Returns the BGP information.
     *
     * @return BGP info
     */
    BgpInfo bgpInfo() {
        return bgpInfo;
    }

    /**
     * Sets the BGP information.
     *
     * @param bgpInfo BGP info
     */
    void bgpInfo(BgpInfo bgpInfo) {
        this.bgpInfo = bgpInfo;
    }

    /**
     * Returns the list of network accesses.
     *
     * @return network accesses
     */
    List<AccessInfo> accesses() {
        return accesses;
    }

    /**
     * Sets the list of network accesses.
     *
     * @param accesses network accesses
     */
    void accesses(List<AccessInfo> accesses) {
        this.accesses = accesses;
    }

    /**
     * Adds a access info to the network accesses list.
     *
     * @param accessInfo access info
     */
    void addAccessInfo(AccessInfo accessInfo) {
        if (accesses == null) {
            accesses = new LinkedList<>();
        }
        accesses.add(accessInfo);
    }

    /**
     * Processes the creation of VPN instance to the driver with the model
     * object data of standard device model. It returns the VPN instance of
     * driver constructed model object data.
     *
     * @param driverSvc driver service
     * @param modelData std device model object data
     * @return driver instance model object data
     */
    ModelObjectData processCreateInstance(DriverService driverSvc,
                                          ModelObjectData modelData) {
        // TODO: Need to call the behaviour.
        return null;
    }

    /**
     * Processes the creation of interface to the driver with the model
     * object data of standard device model. It returns the interface of driver
     * constructed model object data.
     *
     * @param driverSvc driver service
     * @param modData   std device model object data
     * @return driver interface model object data
     */
    ModelObjectData processCreateInterface(DriverService driverSvc,
                                           ModelObjectData modData) {
        // TODO: Need to call the behaviour.
        return null;
    }

    /**
     * Processes the creation of BGP info to the driver with the BGP info and
     * the BGP driver configuration. It returns the BGP info of driver
     * constructed model object data.
     *
     * @param driverSvc  driver service
     * @param bgpInfo    BGP info
     * @param driverInfo driver config details
     * @return driver BGP model object data
     */
    ModelObjectData processCreateBgpInfo(DriverService driverSvc,
                                         BgpInfo bgpInfo,
                                         BgpDriverInfo driverInfo) {
        // TODO: Need to call the behaviour.
        return null;
    }

    /**
     * Processes the deletion of VPN instance to the driver with the model
     * object data of standard device model. It returns the VPN instance of
     * driver constructed model object data.
     *
     * @param driverSvc driver service
     * @param modData   model object data
     * @return driver instance model object data
     */
    ModelObjectData processDeleteInstance(DriverService driverSvc,
                                          ModelObjectData modData) {
        // TODO: Need to call the behaviour.
        return null;
    }

    /**
     * Processes the deletion of interface to the driver with the model
     * object data of standard device model. It returns the interface of driver
     * constructed model object data.
     *
     * @param driverSvc  driver service
     * @param objectData model object data
     * @return driver interface model object data
     */
    ModelObjectData processDeleteInterface(DriverService driverSvc,
                                           ModelObjectData objectData) {
        // TODO: Need to call the behaviour.
        return null;
    }

    /**
     * Processes the deletion of BGP info to the driver with the BGP info and
     * the BGP driver configuration. It returns the BGP info of driver
     * constructed model object data.
     *
     * @param driverSvc  driver service
     * @param bgpInfo    BGP info
     * @param driverInfo driver config details
     * @return driver BGP model object data
     */
    ModelObjectData processDeleteBgpInfo(DriverService driverSvc,
                                         BgpInfo bgpInfo,
                                         BgpDriverInfo driverInfo) {
        // TODO: Need to call the behaviour.
        return null;
    }
}