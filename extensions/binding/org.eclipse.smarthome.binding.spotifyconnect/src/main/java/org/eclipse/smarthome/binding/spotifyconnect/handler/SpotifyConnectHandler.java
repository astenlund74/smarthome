package org.eclipse.smarthome.binding.spotifyconnect.handler;

import static org.eclipse.smarthome.binding.spotifyconnect.SpotifyConnectBindingConstants.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.binding.spotifyconnect.discovery.SpotifyDeviceDiscovery;
import org.eclipse.smarthome.binding.spotifyconnect.internal.SpotifyPlayer;
import org.eclipse.smarthome.binding.spotifyconnect.internal.SpotifySession;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.NextPreviousType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PlayPauseType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.ConfigStatusBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpotifyConnectHandler extends ConfigStatusBridgeHandler implements SpotifyPlayer {

    private Logger logger = LoggerFactory.getLogger(SpotifyConnectHandler.class);
    private SpotifySession spotifySession = null;
    private SpotifyDeviceDiscovery discoveryService;
    @SuppressWarnings("rawtypes")
    private ServiceRegistration discoveryRegistration;

    private Map<String, SpotifyDeviceHandler> spotifyDevices = new HashMap<String, SpotifyDeviceHandler>();

    ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public SpotifyConnectHandler(Bridge bridge) {
        super(bridge);
    }

    public SpotifySession getSpotifySession() {
        return spotifySession;
    }

    public void setSpotifySession(SpotifySession session) {
        this.spotifySession = session;
        spotifySession.scheduleAccessTokenRefresh();

        int cnt = 0;
        while (spotifySession.getAccessToken() == null) {
            cnt++;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {

            }
            if (cnt > 100) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                        "No access token retrieved.");
                break;
            }
        }

        if (cnt <= 100) {
            updateStatus(ThingStatus.ONLINE);

            // Create the discovery service
            discoveryService = new SpotifyDeviceDiscovery(this);

            // And register it as an OSGi service
            discoveryRegistration = bundleContext.registerService(DiscoveryService.class.getName(), discoveryService,
                    new Hashtable<String, Object>());

            startPolling(10);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "Cannot connect to Spotify Web API - client parameters not set.");
        }

    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        // no messages
        Collection<ConfigStatusMessage> configStatusMessages;
        configStatusMessages = Collections.emptyList();
        return configStatusMessages;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received channel: {}, command: {}", channelUID, command);

        String channel = channelUID.getId();

        switch (channel) {
            case CHANNEL_TRACKID:
                if (command instanceof StringType) {
                    playTrack(((StringType) command).toString());
                }
                break;
            case CHANNEL_TRACKPLAYER:
                if (command instanceof PlayPauseType) {
                    if (command.equals(PlayPauseType.PLAY)) {
                        playActiveTrack();
                    } else if (command.equals(PlayPauseType.PAUSE)) {
                        pauseActiveTrack();
                    }
                }
                if (command instanceof OnOffType) {
                    if (command.equals(OnOffType.ON)) {
                        playActiveTrack();
                    } else if (command.equals(OnOffType.OFF)) {
                        pauseActiveTrack();
                    }
                }
                if (command instanceof NextPreviousType) {
                    if (command.equals(NextPreviousType.NEXT)) {
                        playActiveTrack();
                    } else if (command.equals(NextPreviousType.PREVIOUS)) {
                        previousTrack();
                    }

                }
                break;
            case CHANNEL_DEVICESHUFFLE:
                if (command instanceof OnOffType) {
                    setShuffleState(command.equals(OnOffType.OFF) ? "false" : "true");
                }
                break;
            case CHANNEL_DEVICEVOLUME:
                if (command instanceof DecimalType) {
                    DecimalType volume = (DecimalType) command;
                    setVolume(volume.intValue());
                }
                break;
        }
    }

    @Override
    public void dispose() {
        logger.debug("Handler disposed.");
        spotifySession.dispose();
        discoveryService.abortScan();
        discoveryRegistration.unregister();
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Spotify bridge handler.");

        Configuration conf = getConfig();
        String clientId = (String) conf.get("clientId");
        String clientSecret = (String) conf.get("clientSecret");
        String refreshToken = (String) conf.get("refreshToken");

        if (getConfig().get("clientId") != null) {
            if (spotifySession == null) {
                // SpotifySession.initialize(clientId, clientSecret, refreshToken);
                setSpotifySession(SpotifySession.getInstance(this, clientId, clientSecret, refreshToken));
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "Cannot connect to Spotify Web API - client parameters not set.");
        }

    }

    @Override
    public void childHandlerInitialized(ThingHandler thingHandler, Thing thing) {
        logger.debug("Initializing child {} : {} .", thingHandler.getClass().getName(), thing.getLabel());
        SpotifyDeviceHandler handler = (SpotifyDeviceHandler) thingHandler;
        handler.setController(this);
        spotifyDevices.put(handler.getDeviceId(), handler);

    }

    @Override
    public void childHandlerDisposed(ThingHandler thingHandler, Thing thing) {
        logger.debug("Disposing child {} : {} .", thingHandler.getClass().getName(), thing.getLabel());

    }

    public void playTrack(String trackId) {
        String url = "https://api.spotify.com/v1/me/player/play";
        String jsonRequest = "{\"context_uri\":\"%s\",\"offset\":{\"position\":0}}";
        spotifySession.callWebAPI("PUT", url, String.format(jsonRequest, trackId));
    }

    public void playTrack(String deviceId, String trackId) {
        String url = "https://api.spotify.com/v1/me/player/play?device_id=%s";
        String jsonRequest = "{\"context_uri\":\"%s\",\"offset\":{\"position\":0}}";
        spotifySession.callWebAPI("PUT", String.format(url, deviceId), String.format(jsonRequest, trackId));
    }

    public void playActiveTrack() {
        String url = "https://api.spotify.com/v1/me/player/play";
        spotifySession.callWebAPI("PUT", url, "");
    }

    public void playActiveTrack(String deviceId) {
        String url = "https://api.spotify.com/v1/me/player/play?device_id=%s";
        String jsonRequest = "";
        spotifySession.callWebAPI("PUT", String.format(url, deviceId), jsonRequest);
    }

    public void pauseActiveTrack() {
        String url = "https://api.spotify.com/v1/me/player/pause";
        spotifySession.callWebAPI("PUT", url, "");
    }

    public void pauseActiveTrack(String deviceId) {
        String url = "https://api.spotify.com/v1/me/player/pause?device_id=%s";
        spotifySession.callWebAPI("PUT", String.format(url, deviceId), "");
    }

    public void nextTrack() {
        String url = "https://api.spotify.com/v1/me/player/next";
        spotifySession.callWebAPI("PUT", url, "");
    }

    public void nextTrack(String deviceId) {
        String url = "https://api.spotify.com/v1/me/player/next?device_id=%s";
        spotifySession.callWebAPI("PUT", String.format(url, deviceId), "");
    }

    public void previousTrack() {
        String url = "https://api.spotify.com/v1/me/player/previous";
        spotifySession.callWebAPI("PUT", url, "");
    }

    public void previousTrack(String deviceId) {
        String url = "https://api.spotify.com/v1/me/player/previous?device_id=%s";
        spotifySession.callWebAPI("PUT", String.format(url, deviceId), "");
    }

    public void setVolume(int volume) {
        String url = "https://api.spotify.com/v1/me/player/volume?volume_percent=%1d";
        String jsonRequest = "";
        spotifySession.callWebAPI("PUT", String.format(url, volume), jsonRequest);

    }

    public void setDeviceVolume(String deviceId, int volume) {
        String url = "https://api.spotify.com/v1/me/player/volume?device_id=%s&volume_percent=%1d";
        String jsonRequest = "";
        spotifySession.callWebAPI("PUT", String.format(url, deviceId, volume), jsonRequest);

    }

    public void setShuffleState(String state) {
        String url = "https://api.spotify.com/v1/me/player/shuffle?state=%s";
        String jsonRequest = "";
        spotifySession.callWebAPI("PUT", String.format(url, state), jsonRequest);
    }

    public void setShuffleState(String deviceId, String state) {
        String url = "https://api.spotify.com/v1/me/player/shuffle?state=%s&device_id=%s";
        String jsonRequest = "";
        spotifySession.callWebAPI("PUT", String.format(url, state, deviceId), jsonRequest);
    }

    public String listDevices() {
        String url = "https://api.spotify.com/v1/me/player/devices";
        return spotifySession.callWebAPI("GET", url, "");
    }

    public String getPlayerInfo() {
        String url = "https://api.spotify.com/v1/me/player";
        return spotifySession.callWebAPI("GET", url, "");
    }

    private void startPolling(int intervall) {
        //
        Runnable task = () -> {
            logger.debug("Polling Spotify Connect for status");
            List<SpotifyDeviceHandler> activeDevices = new ArrayList<SpotifyDeviceHandler>();

            if (spotifyDevices.size() > 0) {

                String jsonData = listDevices();

                JSONObject jsonObj = null;
                JSONArray devices = null;
                try {
                    jsonObj = new JSONObject(jsonData);
                    if (jsonObj.has("devices")) {
                        devices = jsonObj.getJSONArray("devices");

                        if (devices != null && devices.length() > 0) {
                            for (int i = 0; i < devices.length(); i++) {
                                String id = devices.getJSONObject(i).getString("id");
                                SpotifyDeviceHandler device = spotifyDevices.get(id);
                                if (device != null) {
                                    activeDevices.add(device);

                                    Channel channel = null;

                                    int volumePercent = 0;
                                    if (!devices.getJSONObject(i).isNull("volume_percent")) {
                                        volumePercent = devices.getJSONObject(i).getInt("volume_percent");
                                    }
                                    boolean isActive = devices.getJSONObject(i).getBoolean("is_active");
                                    String type = devices.getJSONObject(i).getString("type");
                                    String name = devices.getJSONObject(i).getString("name");

                                    // Update Device Volume
                                    channel = device.getThing().getChannel(CHANNEL_DEVICEVOLUME);
                                    updateState(channel.getUID(), new DecimalType(volumePercent));
                                    logger.debug("Updating status of spotify device {} channel {}.",
                                            device.getThing().getLabel(), channel.getUID());

                                    // Update Device State
                                    channel = device.getThing().getChannel(CHANNEL_DEVICEACTIVE);
                                    OnOffType deviceActive = isActive ? OnOffType.ON : OnOffType.OFF;
                                    updateState(channel.getUID(), deviceActive);

                                    logger.debug("Updating status of spotify device {} channel {}.",
                                            device.getThing().getLabel(), channel.getUID());

                                    // Update Device ID
                                    channel = device.getThing().getChannel(CHANNEL_DEVICEID);
                                    updateState(channel.getUID(), new StringType(id));
                                    logger.debug("Updating status of spotify device {} channel {}.",
                                            device.getThing().getLabel(), channel.getUID());

                                    // Update Device Name
                                    channel = device.getThing().getChannel(CHANNEL_DEVICENAME);
                                    updateState(channel.getUID(), new StringType(name));
                                    logger.debug("Updating status of spotify device {} channel {}.",
                                            device.getThing().getLabel(), channel.getUID());

                                    // Update Device Type
                                    channel = device.getThing().getChannel(CHANNEL_DEVICETYPE);
                                    updateState(channel.getUID(), new StringType(type));
                                    logger.debug("Updating status of spotify device {} channel {}.",
                                            device.getThing().getLabel(), channel.getUID());

                                }
                            }
                        }

                        for (SpotifyDeviceHandler device : spotifyDevices.values()) {
                            if (activeDevices.contains(device)) {
                                if (device.getThing().getStatus().equals(ThingStatus.OFFLINE)) {
                                    device.changeStatus(ThingStatus.ONLINE);
                                    logger.debug("Taking device {} ONLINE.", device.getThing().getUID());
                                }
                            }
                            if (!activeDevices.contains(device)) {
                                if (device.getThing().getStatus().equals(ThingStatus.ONLINE)) {
                                    logger.debug("Deactivating device {}", device.getThing().getUID());
                                    Channel channel = device.getThing().getChannel(CHANNEL_DEVICEACTIVE);
                                    updateState(channel.getUID(), OnOffType.OFF);
                                    logger.debug("Taking device {} OFFLINE.", device.getThing().getUID());
                                    device.changeStatus(ThingStatus.OFFLINE);
                                }
                            }
                        }

                        jsonData = getPlayerInfo();
                        logger.debug("PlayerInfo: {}", jsonData);

                        jsonObj = new JSONObject(jsonData);

                        setChannelValue(CHANNEL_TRACKPLAYER,
                                jsonObj.getBoolean("is_playing") ? PlayPauseType.PLAY : PlayPauseType.PAUSE);
                        setChannelValue(CHANNEL_TRACKSHUFFLE,
                                jsonObj.getBoolean("shuffle_state") ? OnOffType.ON : OnOffType.OFF);
                        setChannelValue(CHANNEL_TRACKREPEAT, new StringType(jsonObj.getString("repeat_state")));

                        setChannelValue(CHANNEL_PLAYED_TRACKPROGRESS, new StringType(jsonObj.getString("progress_ms")));

                        JSONObject jsonObjItem = jsonObj.getJSONObject("item");

                        setChannelValue(CHANNEL_PLAYED_TRACKID, new StringType(jsonObjItem.getString("id")));
                        setChannelValue(CHANNEL_PLAYED_TRACKHREF, new StringType(jsonObjItem.getString("href")));
                        setChannelValue(CHANNEL_PLAYED_TRACKURI, new StringType(jsonObjItem.getString("uri")));
                        setChannelValue(CHANNEL_PLAYED_TRACKNAME, new StringType(jsonObjItem.getString("name")));
                        setChannelValue(CHANNEL_PLAYED_TRACKTYPE, new StringType(jsonObjItem.getString("type")));
                        setChannelValue(CHANNEL_PLAYED_TRACKDURATION,
                                new StringType(jsonObjItem.getString("duration_ms")));
                        setChannelValue(CHANNEL_PLAYED_TRACKNUMBER,
                                new StringType(jsonObjItem.getString("track_number")));
                        setChannelValue(CHANNEL_PLAYED_TRACKDISCNUMBER,
                                new StringType(jsonObjItem.getString("disc_number")));
                        setChannelValue(CHANNEL_PLAYED_TRACKPOPULARITY,
                                new StringType(jsonObjItem.getString("popularity")));

                        JSONObject jsonObjAlbum = jsonObjItem.getJSONObject("album");
                        setChannelValue(CHANNEL_PLAYED_ALBUMID, new StringType(jsonObjAlbum.getString("id")));
                        setChannelValue(CHANNEL_PLAYED_ALBUMHREF, new StringType(jsonObjAlbum.getString("href")));
                        setChannelValue(CHANNEL_PLAYED_ALBUMURI, new StringType(jsonObjAlbum.getString("uri")));
                        setChannelValue(CHANNEL_PLAYED_ALBUMNAME, new StringType(jsonObjAlbum.getString("name")));
                        setChannelValue(CHANNEL_PLAYED_ALBUMTYPE, new StringType(jsonObjAlbum.getString("type")));

                        JSONArray jsonArrArtists = jsonObjItem.getJSONArray("artists");
                        // TODO: Implement support for multiple artists
                        if (jsonArrArtists.length() > 0) {
                            JSONObject jsonArtist = jsonArrArtists.getJSONObject(0);
                            setChannelValue(CHANNEL_PLAYED_ARTISTID, new StringType(jsonArtist.getString("id")));
                            setChannelValue(CHANNEL_PLAYED_ARTISTHREF, new StringType(jsonArtist.getString("href")));
                            setChannelValue(CHANNEL_PLAYED_ARTISTURI, new StringType(jsonArtist.getString("uri")));
                            setChannelValue(CHANNEL_PLAYED_ARTISTNAME, new StringType(jsonArtist.getString("name")));
                            setChannelValue(CHANNEL_PLAYED_ARTISTTYPE, new StringType(jsonArtist.getString("type")));
                        } else {
                            setChannelValue(CHANNEL_PLAYED_ARTISTID, new StringType(""));
                            setChannelValue(CHANNEL_PLAYED_ARTISTHREF, new StringType(""));
                            setChannelValue(CHANNEL_PLAYED_ARTISTURI, new StringType(""));
                            setChannelValue(CHANNEL_PLAYED_ARTISTNAME, new StringType("no data"));
                            setChannelValue(CHANNEL_PLAYED_ARTISTTYPE, new StringType("no data"));
                        }

                        JSONObject jsonObjDevice = jsonObj.getJSONObject("device");
                        setChannelValue(CHANNEL_DEVICEID, new StringType(jsonObjDevice.getString("id")));
                        setChannelValue(CHANNEL_DEVICEACTIVE,
                                jsonObjDevice.getBoolean("is_active") ? OnOffType.ON : OnOffType.OFF);
                        setChannelValue(CHANNEL_DEVICENAME, new StringType(jsonObjDevice.getString("name")));
                        setChannelValue(CHANNEL_DEVICETYPE, new StringType(jsonObjDevice.getString("type")));
                        if (!jsonObjDevice.isNull("volume_percent")) {
                            setChannelValue(CHANNEL_DEVICEVOLUME,
                                    new DecimalType(jsonObjDevice.getInt("volume_percent")));
                        }
                    }
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    logger.debug("Exception", e);
                }
            } else {
                logger.debug("No devices to poll.");

            }

        };
        executor.scheduleWithFixedDelay(task, 0, intervall, TimeUnit.SECONDS);

    }

    public void setChannelValue(String CHANNEL, State state) {
        if (getThing().getStatus().equals(ThingStatus.ONLINE)) {
            Channel channel = getThing().getChannel(CHANNEL);
            updateState(channel.getUID(), state);
            logger.debug("Updating status of spotify device {} channel {}.", getThing().getLabel(), channel.getUID());
        }
    }

    public void initializeSession(String clientId, String clientSecret, String refreshToken) {

        Configuration configuration = editConfiguration();
        configuration.put("clientId", clientId);
        configuration.put("clientSecret", clientSecret);
        configuration.put("refreshToken", refreshToken);
        updateConfiguration(configuration);

        SpotifySession newSession = SpotifySession.getInstance(this, clientId, clientSecret, refreshToken);
        setSpotifySession(newSession);

    }
}
