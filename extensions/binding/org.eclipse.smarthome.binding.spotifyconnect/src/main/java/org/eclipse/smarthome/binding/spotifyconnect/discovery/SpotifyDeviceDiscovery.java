/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.binding.spotifyconnect.discovery;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.binding.spotifyconnect.SpotifyConnectBindingConstants;
import org.eclipse.smarthome.binding.spotifyconnect.handler.SpotifyConnectHandler;
import org.eclipse.smarthome.binding.spotifyconnect.internal.SpotifySession;
import org.eclipse.smarthome.binding.spotifyconnect.internal.SpotifySession.SpotifyWebAPIDeviceList;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SpotifyDeviceDiscovery} queries the Spotify WebAPI for available devices.
 *
 * @author Andreas Stenlund - Initial contribution
 */
public class SpotifyDeviceDiscovery extends AbstractDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(SpotifyDeviceDiscovery.class);

    private final static Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections
            .singleton(SpotifyConnectBindingConstants.THING_TYPE_DEVICE);

    // the call to listDevices is fast - no continuous polling implemented yet
    private final static int DISCOVERY_TIME_SECONDS = 3;

    private SpotifyConnectHandler player = null;

    public SpotifyDeviceDiscovery(SpotifyConnectHandler coordinatorHandler) {
        super(SUPPORTED_THING_TYPES_UIDS, DISCOVERY_TIME_SECONDS);
        this.player = coordinatorHandler;
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return Collections.singleton(SpotifyConnectBindingConstants.THING_TYPE_DEVICE);
    }

    @Override
    protected void startScan() {
        logger.debug("Starting Spotify Device discovery !");

        List<SpotifyWebAPIDeviceList.Device> spotifyDevices = player.getSpotifySession().listDevices();

        if (spotifyDevices.size() > 0) {
            for (SpotifySession.SpotifyWebAPIDeviceList.Device device : spotifyDevices) {

                Map<String, Object> devConf = new HashMap<String, Object>();
                devConf.put("id", device.getId());
                devConf.put("name", device.getName());
                devConf.put("is_active", device.getIsActive());
                devConf.put("is_restricted", device.getIsRestricted());
                devConf.put("type", device.getType());
                devConf.put("volumePercent", device.getVolumePercent());

                ThingUID thing = new ThingUID(SpotifyConnectBindingConstants.THING_TYPE_DEVICE,
                        player.getThing().getUID(), device.getId());

                DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thing)
                        .withBridge(player.getThing().getUID()).withProperties(devConf).withLabel(device.getName())
                        .build();

                thingDiscovered(discoveryResult);
            }
        }

    }
}
