package com.amazonaws.iot.fleetwise.vehiclesimulator.cli;

import picocli.CommandLine;

public final class VersionProvider implements CommandLine.IVersionProvider {
    public String[] getVersion() {
        return new String[]{PropertiesProvider.INSTANCE.getVersion() + " built at " + PropertiesProvider.INSTANCE.getBuildTime()};
    }
}