/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.binding.spotifyconnect.internal;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.smarthome.binding.spotifyconnect.handler.SpotifyConnectHandler;
import org.eclipse.smarthome.binding.spotifyconnect.internal.SpotifySession.SpotifyWebAPIAuthResult;
import org.eclipse.smarthome.core.thing.ThingRegistry;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

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
    protected List<SpotifyConnectHandler> handlers = new ArrayList<SpotifyConnectHandler>();
    protected Map<String, SpotifyConnectHandler> cookieHandler = new HashMap<String, SpotifyConnectHandler>();

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
                SpotifyConnectHandler authHandler = cookieHandler.get(state.getValue());

                if (authHandler != null) {
                    SpotifyWebAPIAuthResult result = authHandler.getSpotifySession().authenticate(callbackUrl, reqCode);

                    String clientId = (String) authHandler.getThing().getConfiguration().get("clientId");
                    String clientSecret = (String) authHandler.getThing().getConfiguration().get("clientSecret");
                    resp.setContentType("text/html");
                    resp.setCharacterEncoding("UTF-8");
                    resp.setStatus(200);

                    PrintWriter wrout = resp.getWriter();
                    wrout.println(
                            "<html><head><title>Authenticated: Eclipse Smarthome Spotify Connect Bridge</title></head><body>");
                    wrout.println("<h1>Authenticated with Spotify!</h1>");
                    wrout.println("<p>Client ID: <b>" + clientId + "</b></p>");
                    wrout.println("<p>Client Secret: <b>" + clientSecret + "</b></p>");
                    wrout.println("<p>Access Token: <b>" + result.getAccessToken() + "</b></p>");
                    wrout.println("<p>Refresh Token: <b>" + result.getRefreshToken() + "</b><p>");
                    wrout.println("<p>Token Type: <b>" + result.getTokenType() + "</b></p>");
                    wrout.println("<p>Validity: <b>" + result.getExpiresIn() + "</b> seconds</p>");
                    wrout.println("<p>Allowed scopes: <b>" + result.getScope() + "</b></p>");

                    String scheme = req.getScheme();
                    String host = req.getServerName();
                    int port = req.getServerPort();

                    wrout.println("<p><a href=\"" + scheme + "://" + host + ":" + port + SERVLET_NAME
                            + "/\">Back to start page<a/></p>");
                    wrout.println("</body></html>");
                    wrout.flush();

                    authHandler.initializeSession(clientId, clientSecret, result.getRefreshToken());
                    handlers.remove(authHandler);

                } else {
                    RequestDispatcher dispatcher = getServletContext()
                            .getRequestDispatcher(WEBAPP_ALIAS + "/unkownHandler.html");
                    dispatcher.forward(req, resp);
                }

            }
        } else if (url.equals(SERVLET_NAME + "/list")) {
            logger.debug("Spotify auth callback servlet received GET /list request");
            List<Player> players = new ArrayList<Player>();
            for (SpotifyConnectHandler handler : handlers) {
                Player player = new Player();
                player.setId(handler.getThing().getUID().getAsString());
                player.setLabel(handler.getThing().getLabel());
                player.setClientId((String) handler.getThing().getConfiguration().get("clientId"));
                players.add(player);
            }
            Gson gson = new Gson();
            PrintWriter wrout = resp.getWriter();
            wrout.println(gson.toJson(players));

        } else if (url.equals(SERVLET_NAME + "/login")) {
            String spotifyHandlerId = req.getParameter("playerId");

            logger.debug("Spotify auth callback servlet received GET /login request for {}.", spotifyHandlerId);

            SpotifyConnectHandler authHandler = null;
            for (SpotifyConnectHandler handler : handlers) {
                if (handler.getThing().getUID().getAsString().equals(spotifyHandlerId)) {
                    authHandler = handler;
                    break;
                }
            }

            if (authHandler != null) {

                final String stateValue = generateRandomStateString(16);
                Cookie state = new Cookie(stateKey, stateValue);
                resp.addCookie(state);

                cookieHandler.put(stateValue, authHandler);

                String reqScope = new String();
                for (String scope : scopes) {
                    reqScope += scope + "%20";
                }

                String clientId = (String) authHandler.getThing().getConfiguration().get("clientId");

                String queryString = String.format("client_id=%s&response_type=code&redirect_uri=%s&state=%s&scope=%s",
                        clientId, URLEncoder.encode(callbackUrl, "UTF-8"), stateValue, reqScope);

                resp.sendRedirect(String.format("https://accounts.spotify.com/authorize/?%s", queryString));
            } else {
                RequestDispatcher dispatcher = getServletContext()
                        .getRequestDispatcher(WEBAPP_ALIAS + "/unkownHandler.html");
                dispatcher.forward(req, resp);

            }
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
        if (!handlers.contains(handler)) {
            handlers.add(handler);
        }
    }

    protected void setThingRegistry(ThingRegistry thingRegistry) {
        this.thingRegistry = thingRegistry;
    }

    protected void unsetThingRegistry(ThingRegistry thingRegistry) {
        this.thingRegistry = null;
    }

    public class Player {
        @SerializedName("id")
        @Expose
        private String id;
        @SerializedName("label")
        @Expose
        private String label;
        @SerializedName("clientId")
        @Expose
        private String clientId;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
    }

    public class Players {

        @SerializedName("player")
        @Expose
        private List<Player> players = null;

        public List<Player> getPlayers() {
            return players;
        }

        public void setPlayers(List<Player> players) {
            this.players = players;
        }

    }
}