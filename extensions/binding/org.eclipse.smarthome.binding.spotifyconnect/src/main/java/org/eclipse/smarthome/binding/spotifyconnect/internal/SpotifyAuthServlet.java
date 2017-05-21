package org.eclipse.smarthome.binding.spotifyconnect.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.smarthome.binding.spotifyconnect.handler.SpotifyConnectHandler;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingRegistry;
import org.eclipse.smarthome.io.net.http.HttpUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpotifyAuthServlet extends HttpServlet implements SpotifyAuthService {

    /**
     *
     */
    private static final long serialVersionUID = -4719613645562518231L;

    public static final String SERVLET_NAME = "/connectspotify";
    public static final String WEBAPP_ALIAS = SERVLET_NAME + "/web";
    public static final String CALLBACK_ALIAS = SERVLET_NAME + "/authorize";

    private final Logger logger = LoggerFactory.getLogger(SpotifyAuthServlet.class);

    private String[] scopes = new String[] { "playlist-read-private", "playlist-read-collaborative",
            "playlist-modify-public", "playlist-modify-private streaming", "user-follow-modify", "user-follow-read",
            "user-library-read", "user-library-modify", "user-read-private", "user-read-birthdate", "user-read-email",
            "user-top-read", "user-read-playback-state", "user-read-recently-played", "user-modify-playback-state",
            "user-read-currently-playing" };

    private ThingRegistry thingRegistry;
    protected HttpService httpService;
    protected SpotifyConnectHandler handler;
    protected SpotifyPlayer player;

    private String clientId = "c9797fd503c246399bed61ec32b77ca0";
    private String clientSecret = "fe67d85fe4ca4573b005de4f197d307b";
    private String callbackUrl = "http://localhost:8080" + CALLBACK_ALIAS;

    final String stateKey = "spotify_auth_state";

    protected void setHttpService(HttpService httpService) {
        this.httpService = httpService;

        try {
            logger.debug("Starting up the spotify auth callback servlet at " + SERVLET_NAME);
            Hashtable<String, String> props = new Hashtable<String, String>();
            httpService.registerServlet(SERVLET_NAME, this, props, createHttpContext());
            httpService.registerResources(WEBAPP_ALIAS, "web", null);

        } catch (NamespaceException e) {
            logger.error("Error during servlet startup", e);
        } catch (ServletException e) {
            logger.error("Error during servlet startup", e);
        }
    }

    protected void unsetHttpService(HttpService httpService) {
        httpService.unregister(SERVLET_NAME);
        this.httpService = null;
    }

    /**
     * Creates an {@link HttpContext}.
     *
     * @return an {@link HttpContext} that grants anonymous access
     */
    protected HttpContext createHttpContext() {
        HttpContext httpContext = httpService.createDefaultHttpContext();
        return httpContext;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        logger.debug("Spotify auth callback servlet received GET request {}.", req.getRequestURI());

        final Map<String, String> queryStrs = splitQuery(req.getQueryString());

        /*
         * GET https://accounts.spotify.com/authorize/
         * ?client_id=5fe01282e44241328a84e7c5cc169165
         * &response_type=code
         * &redirect_uri=https%3A%2F%2Fexample.com%2Fcallback
         * &scope=user-read-private%20user-read-email&state=34fFs29kd09
         *
         * var scope = 'playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private
         * streaming user-follow-modify user-follow-read user-library-read user-library-modify user-read-private
         * user-read-birthdate user-read-email user-top-read user-read-playback-state user-read-recently-played
         * user-modify-playback-state user-read-currently-playing ';
         */

        final String url = req.getRequestURI();
        if (url.startsWith(WEBAPP_ALIAS)) {
            super.doGet(req, resp);
        } else if (url.equals(SERVLET_NAME + "/")) {
            String indexPage = WEBAPP_ALIAS + "/index.html";
            RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(indexPage);
            dispatcher.forward(req, resp);
        } else if (url.equals(CALLBACK_ALIAS)) {
            logger.debug("Spotify auth callback servlet received GET /authorize request.");

            final String reqCode = queryStrs.get("code");
            final String reqState = queryStrs.get("state");
            final String reqError = queryStrs.get("error");

            Cookie state = null;
            for (Cookie cookie : req.getCookies()) {
                if (cookie.getName().equals(stateKey)) {
                    state = cookie;
                    break;
                }
            }

            if (reqError != null) {
                logger.error("Spotify auth callback servlet received GET /authorize request with error: {}", reqError);
            } else if (state == null) {
                logger.error("Spotify auth callback servlet received GET /authorize request without state cookie.");
            } else if (!state.getValue().equals(reqState)) {
                logger.error(
                        "Spotify auth callback servlet received GET /authorize request without matching state {} != {}.",
                        reqState, state.getValue());
            } else {
                // Callback request OK - initiate authorization process.
                final String authString = Base64.getEncoder()
                        .encodeToString(String.format("%s:%s", clientId, clientSecret).getBytes());
                Properties headers = new Properties();
                headers.setProperty("Authorization", String.format("Basic %s", authString));

                String content = String.format("grant_type=authorization_code&code=%s&redirect_uri=%s", reqCode,
                        URLEncoder.encode(callbackUrl, "UTF-8"));
                ByteArrayInputStream contentStream = new ByteArrayInputStream(content.getBytes());
                String contentType = "application/x-www-form-urlencoded";
                String response = null;
                try {
                    response = HttpUtil.executeUrl("POST", "https://accounts.spotify.com/api/token", headers,
                            contentStream, contentType, 5000);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    logger.debug("Exception while refreshing token", e);

                }
                logger.debug("Response: {}", response);

                try {
                    JSONObject obj = new JSONObject(response);
                    if (obj.has("error")) {
                        String error = obj.getString("error");
                        String errorDesc = obj.getString("error_description");
                        logger.error("Error when authorizing - {} : {}", error, errorDesc);
                    } else {
                        String accessToken = obj.getString("access_token");
                        String tokenType = obj.getString("token_type");
                        String refreshToken = obj.getString("refresh_token");
                        int tokenValidity = obj.getInt("expires_in");
                        String scope = obj.getString("scope");

                        resp.setContentType("text/html");
                        resp.setCharacterEncoding("UTF-8");
                        resp.setStatus(200);

                        PrintWriter wrout = resp.getWriter();
                        wrout.println(
                                "<html><head><title>Authenticate OpenHab Spotify Connect Bridge</title></head><body>");
                        wrout.println("<h1>Authenticated with Spotify</h1>");
                        wrout.println("<p>Client ID: <b>" + clientId + "</b></p>");
                        wrout.println("<p>Client Secret: <b>" + clientSecret + "</b></p>");
                        wrout.println("<p>Access Token: <b>" + accessToken + "</b></p>");
                        wrout.println("<p>Refresh Token: <b>" + refreshToken + "</b><p>");
                        wrout.println("<p>Token Type: <b>" + tokenType + "</b></p>");
                        wrout.println("<p>Validity: <b>" + tokenValidity + "</b> seconds</p>");
                        wrout.println("<p>Allowed scopes: <b>" + scope + "</b></p>");

                        wrout.println("<h2>Known Spotify Things:</h2>");

                        SpotifyConnectHandler handler = null;
                        for (Thing thing : thingRegistry.getAll()) {
                            // if
                            // (!thing.getThingTypeUID().getBindingId().equals(SpotifyConnectBindingConstants.BINDING_ID))
                            // {
                            // continue;
                            // }

                            if (thing.getHandler() instanceof SpotifyConnectHandler) {

                                handler = (SpotifyConnectHandler) thing.getHandler();

                                wrout.println("<p>" + thing.getLabel() + ":" + thing.getThingTypeUID() + " : "
                                        + thing.getStatus() + "</p>");

                            }

                        }

                        wrout.println("</body></html>");
                        wrout.flush();

                        if (handler != null) {
                            handler.initializeSession(clientId, clientSecret, refreshToken);
                        }
                    }
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    logger.debug("Error refreshing spotify web api token!", e);
                }

            }

        } else if (url.equals(SERVLET_NAME + "/login")) {
            logger.debug("Spotify auth callback servlet received GET /login request.");

            final String stateValue = generateRandomStateString(16);
            Cookie state = new Cookie(stateKey, stateValue);
            resp.addCookie(state);

            String reqScope = new String();
            for (String scope : scopes) {
                reqScope += scope + "%20";
            }

            /*
             * String queryString = String.format("client_id=%s&response_type=code&redirect_uri=%s&state=%s&scope=%s",
             * clientId, URLEncoder.encode(callbackUrl, "UTF-8"), stateValue,
             * URLEncoder.encode(reqScope, "UTF-8"));
             */
            String queryString = String.format("client_id=%s&response_type=code&redirect_uri=%s&state=%s&scope=%s",
                    clientId, URLEncoder.encode(callbackUrl, "UTF-8"), stateValue, reqScope);

            resp.sendRedirect(String.format("https://accounts.spotify.com/authorize/?%s", queryString));

        }
    }

    private static String generateRandomStateString(int length) {
        String state = new String();
        String possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

        for (int i = 0; i < length; i++) {
            state += possible.charAt((int) Math.floor(Math.random() * possible.length()));
        }
        return state;
    }

    public static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
        final Map<String, String> keyVals = new HashMap<String, String>();
        if (query != null) {
            final String[] keyValPairs = query.split("&");
            for (String keyVal : keyValPairs) {
                final int idx = keyVal.indexOf("=");
                final String key = idx > 0 ? URLDecoder.decode(keyVal.substring(0, idx), "UTF-8") : keyVal;
                final String value = idx > 0 && keyVal.length() > idx + 1
                        ? URLDecoder.decode(keyVal.substring(idx + 1), "UTF-8") : null;
                keyVals.put(key, value);
            }
        }
        return keyVals;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        logger.debug("Spotify auth callback servlet received POST request.");
    }

    @Override
    public void authenticateSpotifyPlayer(SpotifyConnectHandler handler) {
        this.handler = handler;

    }

    public void setSpotifyPlayer(SpotifyPlayer player) {
        this.player = player;
    }

    public void removeSpotifyPlayer(SpotifyPlayer player) {
        this.player = null;
    }

    protected void setThingRegistry(ThingRegistry thingRegistry) {
        this.thingRegistry = thingRegistry;
    }

    protected void unsetThingRegistry(ThingRegistry thingRegistry) {
        this.thingRegistry = null;
    }
}
