package org.eclipse.smarthome.binding.spotifyconnect.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.binding.spotifyconnect.SpotifyConnectBindingConstants;
import org.eclipse.smarthome.binding.spotifyconnect.handler.SpotifyConnectHandler;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.io.net.http.HttpUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpotifySession implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(SpotifySession.class);

    private static HashMap<SpotifyConnectHandler, SpotifySession> playerSession = new HashMap<>();

    private String clientId = null;
    private String clientSecret = null;
    private String refreshToken = null;

    private SpotifyConnectHandler spotifyPlayer = null;
    private String accessToken = null;
    private int tokenValidity = 3600;
    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);
    @SuppressWarnings("rawtypes")
    ScheduledFuture future = null;

    private SpotifySession(SpotifyConnectHandler spotifyPlayer, String clientId, String clientSecret,
            String refreshToken) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
    }

    public static SpotifySession getInstance(SpotifyConnectHandler spotifyPlayer, String clientId, String clientSecret,
            String refreshToken) {
        // TODO: reinitiatlize session!
        if (playerSession.containsKey(spotifyPlayer)) {
            return playerSession.get(spotifyPlayer);
        }

        SpotifySession session = new SpotifySession(spotifyPlayer, clientId, clientSecret, refreshToken);
        return session;
    }

    public void dispose() {
        if (future != null) {
            future.cancel(true);
        }
        // TODO: remove session from playerSession hashtable
    }

    private void refreshToken() {
        Properties headers = new Properties();
        final String authString = Base64.getEncoder()
                .encodeToString(String.format("%s:%s", clientId, clientSecret).getBytes());
        headers.setProperty("Authorization", "Basic " + authString);
        logger.debug("Spotfy API request..");
        String content = "grant_type=refresh_token&refresh_token=" + refreshToken;
        ByteArrayInputStream contentStream = new ByteArrayInputStream(content.getBytes());
        String contentType = "application/x-www-form-urlencoded";
        String response = null;
        try {
            response = HttpUtil.executeUrl("POST", "https://accounts.spotify.com/api/token", headers, contentStream,
                    contentType, 5000);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            logger.debug("Exception while refreshing token", e);

        }
        logger.debug("Response: {}", response);

        try {
            JSONObject obj = new JSONObject(response);
            accessToken = obj.getString("access_token");
            obj.getString("token_type");
            tokenValidity = obj.getInt("expires_in");
            obj.getString("scope");
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            logger.debug("Error refreshing spotify web api token!", e);
        }
    }

    public void scheduleAccessTokenRefresh() {
        if (future != null && !future.isCancelled()) {
            future.cancel(true);
        }
        // TODO: Find a more suitable to retrieve token validity if it changes after being scheduled? Scheduling refresh
        // 10 seconds before expiring.
        refreshToken();
        tokenValidity -= 10;
        future = scheduledExecutorService.scheduleAtFixedRate(this, tokenValidity, tokenValidity, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        spotifyPlayer.setChannelValue(SpotifyConnectBindingConstants.CHANNEL_REFRESHTOKEN, OnOffType.ON);
        refreshToken();
        spotifyPlayer.setChannelValue(SpotifyConnectBindingConstants.CHANNEL_REFRESHTOKEN, OnOffType.OFF);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String callWebAPI(String method, String url, String requestData) {

        Properties headers = new Properties();
        headers.setProperty("Authorization", "Bearer " + accessToken);
        headers.setProperty("Accept", "application/json");

        String contentType = "application/json";
        String response = null;

        ByteArrayInputStream contentStream = null;
        if (requestData != null) {
            contentStream = new ByteArrayInputStream(requestData.getBytes());
        }

        try {
            response = HttpUtil.executeUrl(method, url, headers, contentStream, contentType, 10000);
        } catch (IOException e) {
            if (e.getCause() != null && e.getCause() instanceof java.util.concurrent.TimeoutException) {
                logger.debug("HTTP Timeout exception");
            } else {
                e.printStackTrace();
                logger.debug("Error while executing HTTP request", e);
            }
        }

        // logger.debug("Response: {}", response);

        return response;
    }

}
