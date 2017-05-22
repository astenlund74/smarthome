/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.binding.spotifyconnect.internal;

public class SpotifyDevice {

    // private static Logger logger = LoggerFactory.getLogger(SpotifyDevice.class);

    private String id;
    private boolean isActive;
    private boolean isRestricted;
    private String type;
    private String name;
    private int volumePercent;

    public SpotifyDevice(String id, boolean isActive, boolean isRestricted, String type, String name,
            int volumePercent) {
        this.setId(id);
        this.setActive(isActive);
        this.setRestricted(isRestricted);
        this.setType(type);
        this.setName(name);
        this.setVolumePercent(volumePercent);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public boolean isRestricted() {
        return isRestricted;
    }

    public void setRestricted(boolean isRestricted) {
        this.isRestricted = isRestricted;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getVolumePercent() {
        return volumePercent;
    }

    public void setVolumePercent(int volumePercent) {
        this.volumePercent = volumePercent;
    }

}
