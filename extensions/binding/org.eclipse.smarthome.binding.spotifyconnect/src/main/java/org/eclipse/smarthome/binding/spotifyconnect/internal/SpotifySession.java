package org.eclipse.smarthome.binding.spotifyconnect.internal;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.smarthome.binding.spotifyconnect.SpotifyConnectBindingConstants;
import org.eclipse.smarthome.binding.spotifyconnect.handler.SpotifyConnectHandler;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class SpotifySession implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(SpotifySession.class);

    // Instantiate and configure the SslContextFactory
    private SslContextFactory sslContextFactory = new SslContextFactory();

    // Instantiate HttpClient with the SslContextFactory
    private HttpClient httpClient = new HttpClient(sslContextFactory);
    private static HashMap<SpotifyConnectHandler, SpotifySession> playerSession = new HashMap<>();

    private String clientId = null;
    private String clientSecret = null;
    private String refreshToken = null;

    private SpotifyConnectHandler spotifyPlayer = null;
    private String accessToken = null;
    private int tokenValidity = 3600;
    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);

    @SuppressWarnings({ "rawtypes" })
    private ScheduledFuture future = null;

    private SpotifySession(SpotifyConnectHandler spotifyPlayer, String clientId, String clientSecret,
            String refreshToken) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;

        httpClient.setFollowRedirects(true);
        if (!httpClient.isStarted()) {
            try {
                httpClient.start();
            } catch (Exception e) {
                logger.error("Error starting HttpClient", e);
            }

        }
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

    /**
     *
     */
    public void dispose() {
        if (future != null) {
            future.cancel(true);
        }

        // TODO: Not really need? Causing bad exceptions when refreshing SpotifySession in Controller check later....
        // if (httpClient != null && httpClient.isStarted()) {
        // try {
        // httpClient.stop();
        // } catch (Exception e) {
        // logger.error("Error stopping HttpClient", e);
        // }
        // }

        // TODO: remove session from playerSession hashtable
    }

    public SpotifyWebAPIAuthResult authenticate(String callbackUrl, String reqCode) {

        final String authString = Base64.getEncoder()
                .encodeToString(String.format("%s:%s", clientId, clientSecret).getBytes());

        logger.debug("Spotfy API request..");

        String content = String.format("grant_type=authorization_code&code=%s&redirect_uri=%s", reqCode, callbackUrl);

        String contentType = "application/x-www-form-urlencoded";

        ContentResponse response = null;
        try {
            String url = "https://accounts.spotify.com/api/token";
            // String url = "http://localhost:8081/api/token";
            response = httpClient.POST(url).header("Authorization", "Basic " + authString)
                    .content(new StringContentProvider(content), contentType).timeout(10, TimeUnit.SECONDS).send();

            // Properties headers = new Properties();
            // headers.setProperty("Authorization", "Basic " + authString);
            // ByteArrayInputStream contentStream = new ByteArrayInputStream(content.getBytes());
            // String oldResp = HttpUtil.executeUrl("POST", url, headers, contentStream, contentType, 5000);

            logger.debug("Response: {}", response.getContentAsString());

            Gson gson = new Gson();
            SpotifyWebAPIAuthResult test = gson.fromJson(response.getContentAsString(), SpotifyWebAPIAuthResult.class);
            accessToken = test.accessToken;
            tokenValidity = test.getExpiresIn();
            return test;

        } catch (InterruptedException | TimeoutException | ExecutionException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            logger.debug("Error refreshing spotify web api token!", e1);
        }
        return null;
    }

    private void refreshToken() {
        final String authString = Base64.getEncoder()
                .encodeToString(String.format("%s:%s", clientId, clientSecret).getBytes());
        logger.debug("Spotfy API request..");

        String content = "grant_type=refresh_token&refresh_token=" + refreshToken;
        String contentType = "application/x-www-form-urlencoded";

        ContentResponse response = null;
        try {
            String url = "https://accounts.spotify.com/api/token";
            // String url = "http://localhost:8081/api/token";
            response = httpClient.POST(url).header("Authorization", "Basic " + authString)
                    .content(new StringContentProvider(content), contentType).timeout(10, TimeUnit.SECONDS).send();

            logger.debug("Response: {}", response.getContentAsString());

            Gson gson = new Gson();
            SpotifyWebAPIRefreshResult test = gson.fromJson(response.getContentAsString(),
                    SpotifyWebAPIRefreshResult.class);
            accessToken = test.accessToken;
            tokenValidity = test.getExpiresIn();

        } catch (InterruptedException | TimeoutException | ExecutionException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            logger.debug("Error refreshing spotify web api token!", e1);
        }
    }

    public boolean scheduleAccessTokenRefresh() {
        if (future != null && !future.isCancelled()) {
            future.cancel(true);
        }
        // TODO: Find a more suitable to retrieve token validity if it changes after being scheduled? Scheduling refresh
        // 10 seconds before expiring.
        try {
            refreshToken();
        } catch (java.lang.NullPointerException npe) {
            // ignore
        }

        if (accessToken == null) {
            return false;
        }

        tokenValidity -= 10;
        future = scheduledExecutorService.scheduleAtFixedRate(this, tokenValidity, tokenValidity, TimeUnit.SECONDS);

        return true;
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

    /*
     * Spotify WebAPI calls
     */

    public void playTrack(String trackId) {
        String url = "https://api.spotify.com/v1/me/player/play";
        String jsonRequest = "{\"context_uri\":\"%s\",\"offset\":{\"position\":0}}";
        callWebAPI("PUT", url, String.format(jsonRequest, trackId));
    }

    public void playTrack(String deviceId, String trackId) {
        String url = "https://api.spotify.com/v1/me/player/play?device_id=%s";
        String jsonRequest = "{\"context_uri\":\"%s\",\"offset\":{\"position\":0}}";
        callWebAPI("PUT", String.format(url, deviceId), String.format(jsonRequest, trackId));
    }

    public void playActiveTrack() {
        String url = "https://api.spotify.com/v1/me/player/play";
        callWebAPI("PUT", url, "");
    }

    public void playActiveTrack(String deviceId) {
        String url = "https://api.spotify.com/v1/me/player/play?device_id=%s";
        String jsonRequest = "";
        callWebAPI("PUT", String.format(url, deviceId), jsonRequest);
    }

    public void pauseActiveTrack() {
        String url = "https://api.spotify.com/v1/me/player/pause";
        callWebAPI("PUT", url, "");
    }

    public void pauseActiveTrack(String deviceId) {
        String url = "https://api.spotify.com/v1/me/player/pause?device_id=%s";
        callWebAPI("PUT", String.format(url, deviceId), "");
    }

    public void nextTrack() {
        String url = "https://api.spotify.com/v1/me/player/next";
        callWebAPI("PUT", url, "");
    }

    public void nextTrack(String deviceId) {
        String url = "https://api.spotify.com/v1/me/player/next?device_id=%s";
        callWebAPI("PUT", String.format(url, deviceId), "");
    }

    public void previousTrack() {
        String url = "https://api.spotify.com/v1/me/player/previous";
        callWebAPI("PUT", url, "");
    }

    public void previousTrack(String deviceId) {
        String url = "https://api.spotify.com/v1/me/player/previous?device_id=%s";
        callWebAPI("PUT", String.format(url, deviceId), "");
    }

    public void setVolume(int volume) {
        String url = "https://api.spotify.com/v1/me/player/volume?volume_percent=%1d";
        String jsonRequest = "";
        callWebAPI("PUT", String.format(url, volume), jsonRequest);

    }

    public void setDeviceVolume(String deviceId, int volume) {
        String url = "https://api.spotify.com/v1/me/player/volume?device_id=%s&volume_percent=%1d";
        String jsonRequest = "";
        callWebAPI("PUT", String.format(url, deviceId, volume), jsonRequest);

    }

    public void setShuffleState(String state) {
        String url = "https://api.spotify.com/v1/me/player/shuffle?state=%s";
        String jsonRequest = "";
        callWebAPI("PUT", String.format(url, state), jsonRequest);
    }

    public void setShuffleState(String deviceId, String state) {
        String url = "https://api.spotify.com/v1/me/player/shuffle?state=%s&device_id=%s";
        String jsonRequest = "";
        callWebAPI("PUT", String.format(url, state, deviceId), jsonRequest);
    }

    public List<SpotifyWebAPIDeviceList.Device> listDevices() {
        String url = "https://api.spotify.com/v1/me/player/devices";
        String result = callWebAPI("GET", url, "");
        Gson gson = new Gson();
        SpotifyWebAPIDeviceList deviceList = gson.fromJson(result, SpotifyWebAPIDeviceList.class);
        return deviceList.getDevices();
    }

    public SpotifyWebAPIPlayerInfo getPlayerInfo() {
        String url = "https://api.spotify.com/v1/me/player";
        String result = callWebAPI("GET", url, "");
        Gson gson = new Gson();
        SpotifyWebAPIPlayerInfo playerInfo = gson.fromJson(result, SpotifyWebAPIPlayerInfo.class);
        return playerInfo;
    }

    /**
     * This method is a simple wrapper for Spotify WebAPI calls
     *
     * @param method the http method to use (GET, PUT, POST ..)
     * @param url the WebAPI url to call
     * @param requestData the body of the request, if any.
     * @return response from call
     */
    private String callWebAPI(String method, String url, String requestData) {

        Properties headers = new Properties();
        headers.setProperty("Authorization", "Bearer " + accessToken);
        headers.setProperty("Accept", "application/json");

        String contentType = "application/json";
        ContentResponse response = null;

        try {
            response = httpClient.newRequest(url).method(HttpMethod.fromString(method))
                    .header("Authorization", "Bearer " + accessToken).header("Accept", "application/json")
                    .content(new StringContentProvider(requestData), contentType).timeout(10, TimeUnit.SECONDS).send();

            logger.debug("Response: {}", response);
            return response.getContentAsString();

            // response = HttpUtil.executeUrl(method, url, headers, contentStream, contentType, 10000);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            logger.debug("Error refreshing spotify web api token!", e);
        }

        return null;
    }

    /**
     * This class and its inner classes represents the SpotifyWebAPI response of an authorization request
     *
     * @author Andreas Stenlund
     *
     */
    public class SpotifyWebAPIAuthResult {

        @SerializedName("access_token")
        @Expose
        private String accessToken;
        @SerializedName("refresh_token")
        @Expose
        private String refreshToken;
        @SerializedName("token_type")
        @Expose
        private String tokenType;
        @SerializedName("scope")
        @Expose
        private String scope;
        @SerializedName("expires_in")
        @Expose
        private Integer expiresIn;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public Integer getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(Integer expiresIn) {
            this.expiresIn = expiresIn;
        }

    }

    public class SpotifyWebAPIRefreshResult {

        @SerializedName("access_token")
        @Expose
        private String accessToken;
        @SerializedName("token_type")
        @Expose
        private String tokenType;
        @SerializedName("scope")
        @Expose
        private String scope;
        @SerializedName("expires_in")
        @Expose
        private Integer expiresIn;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public Integer getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(Integer expiresIn) {
            this.expiresIn = expiresIn;
        }

    }

    /**
     * This class and its inner classes represents the SpotifyWebAPI response with Player Information
     *
     * @author Andreas Stenlund
     *
     */
    public class SpotifyWebAPIPlayerInfo {

        @SerializedName("timestamp")
        @Expose
        private Long timestamp;
        @SerializedName("progress_ms")
        @Expose
        private Long progressMs;
        @SerializedName("is_playing")
        @Expose
        private Boolean isPlaying;
        @SerializedName("item")
        @Expose
        private Item item;
        @SerializedName("context")
        @Expose
        private Object context;
        @SerializedName("device")
        @Expose
        private Device device;
        @SerializedName("repeat_state")
        @Expose
        private String repeatState;
        @SerializedName("shuffle_state")
        @Expose
        private Boolean shuffleState;

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }

        public Long getProgressMs() {
            return progressMs;
        }

        public void setProgressMs(Long progressMs) {
            this.progressMs = progressMs;
        }

        public Boolean getIsPlaying() {
            return isPlaying;
        }

        public void setIsPlaying(Boolean isPlaying) {
            this.isPlaying = isPlaying;
        }

        public Item getItem() {
            return item;
        }

        public void setItem(Item item) {
            this.item = item;
        }

        public Object getContext() {
            return context;
        }

        public void setContext(Object context) {
            this.context = context;
        }

        public Device getDevice() {
            return device;
        }

        public void setDevice(Device device) {
            this.device = device;
        }

        public String getRepeatState() {
            return repeatState;
        }

        public void setRepeatState(String repeatState) {
            this.repeatState = repeatState;
        }

        public Boolean getShuffleState() {
            return shuffleState;
        }

        public void setShuffleState(Boolean shuffleState) {
            this.shuffleState = shuffleState;
        }

        /*
         * Inner classes of the SpotifyWebAPIPlayerInfo
         */
        public class Album {

            @SerializedName("album_type")
            @Expose
            private String albumType;
            @SerializedName("artists")
            @Expose
            private List<Artist> artists = null;
            @SerializedName("available_markets")
            @Expose
            private List<String> availableMarkets = null;
            @SerializedName("external_urls")
            @Expose
            private ExternalUrls externalUrls;
            @SerializedName("href")
            @Expose
            private String href;
            @SerializedName("id")
            @Expose
            private String id;
            @SerializedName("images")
            @Expose
            private List<Image> images = null;
            @SerializedName("name")
            @Expose
            private String name;
            @SerializedName("type")
            @Expose
            private String type;
            @SerializedName("uri")
            @Expose
            private String uri;

            public String getAlbumType() {
                return albumType;
            }

            public void setAlbumType(String albumType) {
                this.albumType = albumType;
            }

            public List<Artist> getArtists() {
                return artists;
            }

            public void setArtists(List<Artist> artists) {
                this.artists = artists;
            }

            public List<String> getAvailableMarkets() {
                return availableMarkets;
            }

            public void setAvailableMarkets(List<String> availableMarkets) {
                this.availableMarkets = availableMarkets;
            }

            public ExternalUrls getExternalUrls() {
                return externalUrls;
            }

            public void setExternalUrls(ExternalUrls externalUrls) {
                this.externalUrls = externalUrls;
            }

            public String getHref() {
                return href;
            }

            public void setHref(String href) {
                this.href = href;
            }

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public List<Image> getImages() {
                return images;
            }

            public void setImages(List<Image> images) {
                this.images = images;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getUri() {
                return uri;
            }

            public void setUri(String uri) {
                this.uri = uri;
            }

        }

        public class Artist {

            @SerializedName("external_urls")
            @Expose
            private ExternalUrls externalUrls;
            @SerializedName("href")
            @Expose
            private String href;
            @SerializedName("id")
            @Expose
            private String id;
            @SerializedName("name")
            @Expose
            private String name;
            @SerializedName("type")
            @Expose
            private String type;
            @SerializedName("uri")
            @Expose
            private String uri;

            public ExternalUrls getExternalUrls() {
                return externalUrls;
            }

            public void setExternalUrls(ExternalUrls externalUrls) {
                this.externalUrls = externalUrls;
            }

            public String getHref() {
                return href;
            }

            public void setHref(String href) {
                this.href = href;
            }

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getUri() {
                return uri;
            }

            public void setUri(String uri) {
                this.uri = uri;
            }

        }

        public class Device {

            @SerializedName("id")
            @Expose
            private String id;
            @SerializedName("is_active")
            @Expose
            private Boolean isActive;
            @SerializedName("is_restricted")
            @Expose
            private Boolean isRestricted;
            @SerializedName("name")
            @Expose
            private String name;
            @SerializedName("type")
            @Expose
            private String type;
            @SerializedName("volume_percent")
            @Expose
            private Integer volumePercent;

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public Boolean getIsActive() {
                return isActive;
            }

            public void setIsActive(Boolean isActive) {
                this.isActive = isActive;
            }

            public Boolean getIsRestricted() {
                return isRestricted;
            }

            public void setIsRestricted(Boolean isRestricted) {
                this.isRestricted = isRestricted;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public Integer getVolumePercent() {
                return volumePercent;
            }

            public void setVolumePercent(Integer volumePercent) {
                this.volumePercent = volumePercent;
            }

        }

        public class ExternalIds {

            @SerializedName("isrc")
            @Expose
            private String isrc;

            public String getIsrc() {
                return isrc;
            }

            public void setIsrc(String isrc) {
                this.isrc = isrc;
            }

        }

        public class ExternalUrls {

            @SerializedName("spotify")
            @Expose
            private String spotify;

            public String getSpotify() {
                return spotify;
            }

            public void setSpotify(String spotify) {
                this.spotify = spotify;
            }

        }

        public class Image {

            @SerializedName("height")
            @Expose
            private Integer height;
            @SerializedName("url")
            @Expose
            private String url;
            @SerializedName("width")
            @Expose
            private Integer width;

            public Integer getHeight() {
                return height;
            }

            public void setHeight(Integer height) {
                this.height = height;
            }

            public String getUrl() {
                return url;
            }

            public void setUrl(String url) {
                this.url = url;
            }

            public Integer getWidth() {
                return width;
            }

            public void setWidth(Integer width) {
                this.width = width;
            }

        }

        public class Item {

            @SerializedName("album")
            @Expose
            private Album album;
            @SerializedName("artists")
            @Expose
            private List<Artist> artists = null;
            @SerializedName("available_markets")
            @Expose
            private List<String> availableMarkets = null;
            @SerializedName("disc_number")
            @Expose
            private Integer discNumber;
            @SerializedName("duration_ms")
            @Expose
            private Integer durationMs;
            @SerializedName("explicit")
            @Expose
            private Boolean explicit;
            @SerializedName("external_ids")
            @Expose
            private ExternalIds externalIds;
            @SerializedName("external_urls")
            @Expose
            private ExternalUrls externalUrls;
            @SerializedName("href")
            @Expose
            private String href;
            @SerializedName("id")
            @Expose
            private String id;
            @SerializedName("name")
            @Expose
            private String name;
            @SerializedName("popularity")
            @Expose
            private Integer popularity;
            @SerializedName("preview_url")
            @Expose
            private String previewUrl;
            @SerializedName("track_number")
            @Expose
            private Integer trackNumber;
            @SerializedName("type")
            @Expose
            private String type;
            @SerializedName("uri")
            @Expose
            private String uri;

            public Album getAlbum() {
                return album;
            }

            public void setAlbum(Album album) {
                this.album = album;
            }

            public List<Artist> getArtists() {
                return artists;
            }

            public void setArtists(List<Artist> artists) {
                this.artists = artists;
            }

            public List<String> getAvailableMarkets() {
                return availableMarkets;
            }

            public void setAvailableMarkets(List<String> availableMarkets) {
                this.availableMarkets = availableMarkets;
            }

            public Integer getDiscNumber() {
                return discNumber;
            }

            public void setDiscNumber(Integer discNumber) {
                this.discNumber = discNumber;
            }

            public Integer getDurationMs() {
                return durationMs;
            }

            public void setDurationMs(Integer durationMs) {
                this.durationMs = durationMs;
            }

            public Boolean getExplicit() {
                return explicit;
            }

            public void setExplicit(Boolean explicit) {
                this.explicit = explicit;
            }

            public ExternalIds getExternalIds() {
                return externalIds;
            }

            public void setExternalIds(ExternalIds externalIds) {
                this.externalIds = externalIds;
            }

            public ExternalUrls getExternalUrls() {
                return externalUrls;
            }

            public void setExternalUrls(ExternalUrls externalUrls) {
                this.externalUrls = externalUrls;
            }

            public String getHref() {
                return href;
            }

            public void setHref(String href) {
                this.href = href;
            }

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public Integer getPopularity() {
                return popularity;
            }

            public void setPopularity(Integer popularity) {
                this.popularity = popularity;
            }

            public String getPreviewUrl() {
                return previewUrl;
            }

            public void setPreviewUrl(String previewUrl) {
                this.previewUrl = previewUrl;
            }

            public Integer getTrackNumber() {
                return trackNumber;
            }

            public void setTrackNumber(Integer trackNumber) {
                this.trackNumber = trackNumber;
            }

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getUri() {
                return uri;
            }

            public void setUri(String uri) {
                this.uri = uri;
            }

        }
    }

    /**
     *
     * @author Andreas Stenlund
     *
     */
    public class SpotifyWebAPIDeviceList {

        public class Device {

            @SerializedName("id")
            @Expose
            private String id;
            @SerializedName("is_active")
            @Expose
            private Boolean isActive;
            @SerializedName("is_restricted")
            @Expose
            private Boolean isRestricted;
            @SerializedName("name")
            @Expose
            private String name;
            @SerializedName("type")
            @Expose
            private String type;
            @SerializedName("volume_percent")
            @Expose
            private Integer volumePercent;

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public Boolean getIsActive() {
                return isActive;
            }

            public void setIsActive(Boolean isActive) {
                this.isActive = isActive;
            }

            public Boolean getIsRestricted() {
                return isRestricted;
            }

            public void setIsRestricted(Boolean isRestricted) {
                this.isRestricted = isRestricted;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public Integer getVolumePercent() {
                return volumePercent;
            }

            public void setVolumePercent(Integer volumePercent) {
                this.volumePercent = volumePercent;
            }

        }

        @SerializedName("devices")
        @Expose
        private List<Device> devices = null;

        public List<Device> getDevices() {
            return devices;
        }

        public void setDevices(List<Device> devices) {
            this.devices = devices;
        }

    }

}
