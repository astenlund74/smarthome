/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.binding.spotifyconnect.internal;

import org.eclipse.smarthome.binding.spotifyconnect.SpotifyConnectBindingConstants;
import org.eclipse.smarthome.binding.spotifyconnect.handler.SpotifyConnectHandler;
import org.eclipse.smarthome.binding.spotifyconnect.handler.SpotifyDeviceHandler;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;

/**
 * The {@link SpotifyConnectHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Andreas Stenlund - Initial contribution
 */
public class SpotifyConnectHandlerFactory extends BaseThingHandlerFactory {

    private SpotifyAuthService authService = null;

    public SpotifyAuthService getSpotifyAuthService() {
        return authService;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {

        if (thingTypeUID.equals(SpotifyConnectBindingConstants.THING_TYPE_PLAYER)) {
            return true;
        }
        if (thingTypeUID.equals(SpotifyConnectBindingConstants.THING_TYPE_DEVICE)) {
            return true;
        }
        return false;
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (thingTypeUID.equals(SpotifyConnectBindingConstants.THING_TYPE_PLAYER)) {
            return new SpotifyConnectHandler((Bridge) thing, this);
        }
        if (thingTypeUID.equals(SpotifyConnectBindingConstants.THING_TYPE_DEVICE)) {
            return new SpotifyDeviceHandler(thing);
        }

        return null;
    }

    public void bindAuthService(SpotifyAuthService service) {
        this.authService = service;
    }

    public void unbindAuthService(SpotifyAuthService service) {
        this.authService = null;
    }
}
