/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.binding.spotifyconnect.internal;

import org.eclipse.smarthome.binding.spotifyconnect.handler.SpotifyConnectHandler;

/**
 * The {@link SpotifyAuthService} is used to register {@link SpotifyConnectHandler} for authorization with Spotify Web
 * API
 *
 * @author Andreas Stenlund - Initial contribution
 */
public interface SpotifyAuthService {

    public void authenticateSpotifyPlayer(SpotifyConnectHandler handler);

}
