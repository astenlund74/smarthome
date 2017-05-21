/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.binding.spotifyconnect;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link SpotifyConnectBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Andreas Stenlund - Initial contribution
 */
public class SpotifyConnectBindingConstants {

    private static final String BINDING_ID = "spotifyconnect";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_PLAYER = new ThingTypeUID(BINDING_ID, "player");
    public static final ThingTypeUID THING_TYPE_DEVICE = new ThingTypeUID(BINDING_ID, "device");

    // List of all Channel ids
    public static final String CHANNEL_REFRESHTOKEN = "refreshToken";

    public static final String CHANNEL_TRACKID = "trackId";
    public static final String CHANNEL_TRACKHREF = "trackHref";
    public static final String CHANNEL_TRACKPLAYER = "trackPlayer";
    public static final String CHANNEL_TRACKSHUFFLE = "trackShuffle";
    public static final String CHANNEL_TRACKREPEAT = "trackRepeat";

    public static final String CHANNEL_PLAYED_TRACKID = "currentlyPlayedTrackId";
    public static final String CHANNEL_PLAYED_TRACKURI = "currentlyPlayedTrackUri";
    public static final String CHANNEL_PLAYED_TRACKHREF = "currentlyPlayedTrackHref";
    public static final String CHANNEL_PLAYED_TRACKNAME = "currentlyPlayedTrackName";
    public static final String CHANNEL_PLAYED_TRACKTYPE = "currentlyPlayedTrackType";
    public static final String CHANNEL_PLAYED_TRACKNUMBER = "currentlyPlayedTrackNumber";
    public static final String CHANNEL_PLAYED_TRACKDISCNUMBER = "currentlyPlayedTrackDiscNumber";
    public static final String CHANNEL_PLAYED_TRACKPOPULARITY = "currentlyPlayedTrackPopularity";
    public static final String CHANNEL_PLAYED_TRACKDURATION = "currentlyPlayedTrackDuration";
    public static final String CHANNEL_PLAYED_TRACKPROGRESS = "currentlyPlayedTrackProgress";

    public static final String CHANNEL_PLAYED_ALBUMID = "currentlyPlayedAlbumId";
    public static final String CHANNEL_PLAYED_ALBUMURI = "currentlyPlayedAlbumUri";
    public static final String CHANNEL_PLAYED_ALBUMHREF = "currentlyPlayedAlbumHref";
    public static final String CHANNEL_PLAYED_ALBUMNAME = "currentlyPlayedAlbumName";
    public static final String CHANNEL_PLAYED_ALBUMTYPE = "currentlyPlayedAlbumType";

    public static final String CHANNEL_PLAYED_ARTISTID = "currentlyPlayedArtistId";
    public static final String CHANNEL_PLAYED_ARTISTURI = "currentlyPlayedArtistUri";
    public static final String CHANNEL_PLAYED_ARTISTHREF = "currentlyPlayedArtistHref";
    public static final String CHANNEL_PLAYED_ARTISTNAME = "currentlyPlayedArtistName";
    public static final String CHANNEL_PLAYED_ARTISTTYPE = "currentlyPlayedArtistType";

    public static final String CHANNEL_DEVICEID = "deviceId";
    public static final String CHANNEL_DEVICENAME = "deviceName";
    public static final String CHANNEL_DEVICETYPE = "deviceType";
    public static final String CHANNEL_DEVICEACTIVE = "deviceActive";
    public static final String CHANNEL_DEVICEVOLUME = "deviceVolume";
    public static final String CHANNEL_DEVICESHUFFLE = "deviceShuffle";
    public static final String CHANNEL_DEVICEPLAY = "devicePlay";

}
