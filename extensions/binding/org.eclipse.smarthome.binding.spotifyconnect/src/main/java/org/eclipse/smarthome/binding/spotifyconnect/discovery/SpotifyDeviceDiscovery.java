package org.eclipse.smarthome.binding.spotifyconnect.discovery;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.binding.spotifyconnect.SpotifyConnectBindingConstants;
import org.eclipse.smarthome.binding.spotifyconnect.handler.SpotifyConnectHandler;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpotifyDeviceDiscovery extends AbstractDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(SpotifyDeviceDiscovery.class);

    private final static Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections
            .singleton(SpotifyConnectBindingConstants.THING_TYPE_DEVICE);
    private final static int DISCOVERY_TIME_SECONDS = 5;

    private SpotifyConnectHandler coordinatorHandler = null;

    public SpotifyDeviceDiscovery(SpotifyConnectHandler coordinatorHandler) {
        super(SUPPORTED_THING_TYPES_UIDS, DISCOVERY_TIME_SECONDS);
        this.coordinatorHandler = coordinatorHandler;
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return Collections.singleton(SpotifyConnectBindingConstants.THING_TYPE_DEVICE);
    }

    @Override
    protected void startScan() {
        logger.debug("Starting Spotify Device discovery !");

        String jsonData = coordinatorHandler.listDevices();
        JSONObject jsonObj = null;
        JSONArray devices = null;
        try {
            jsonObj = new JSONObject(jsonData);
            devices = jsonObj.optJSONArray("devices");

            if (devices != null && devices.length() > 0) {
                for (int i = 0; i < devices.length(); i++) {

                    String id = devices.getJSONObject(i).getString("id");
                    boolean isActive = devices.getJSONObject(i).getBoolean("is_active");
                    boolean isRestricted = devices.getJSONObject(i).getBoolean("is_restricted");
                    String type = devices.getJSONObject(i).getString("type");
                    String name = devices.getJSONObject(i).getString("name");
                    int volumePercent = 0;
                    if (!devices.getJSONObject(i).isNull("volume_percent")) {
                        volumePercent = devices.getJSONObject(i).getInt("volume_percent");
                    }

                    // SpotifyDevice dev = new SpotifyDevice(id, isActive, isRestricted, type, name,
                    // volumePercent);

                    Map<String, Object> devConf = new HashMap<String, Object>();
                    devConf.put("id", id);
                    devConf.put("name", name);
                    devConf.put("is_active", isActive);
                    devConf.put("is_restricted", isRestricted);
                    devConf.put("type", type);
                    devConf.put("volumePercent", volumePercent);

                    ThingUID device = new ThingUID(SpotifyConnectBindingConstants.THING_TYPE_DEVICE,
                            coordinatorHandler.getThing().getUID(), id);

                    DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(device)
                            .withBridge(coordinatorHandler.getThing().getUID()).withProperties(devConf).withLabel(name)
                            .build();

                    thingDiscovered(discoveryResult);
                }
            }

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            logger.debug("Exception", e);
        }

    }
}
