package dev.jxnnik.cloudcommand;

import eu.thesimplecloud.api.external.ICloudModule;

public class CloudCommand implements ICloudModule {

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public boolean isReloadable() {
        return false;
    }
}