package com.qualcomm.robotcore.hardware;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Stand-in for the FTC SDK class; only what Second Loop uses. Tests override tryGet. */
public class HardwareMap {
    public DeviceMapping<VoltageSensor> voltageSensor = new DeviceMapping<VoltageSensor>();

    public <T> T tryGet(Class<? extends T> classOrInterface, String deviceName) {
        return null;
    }

    /** Stand-in for the SDK's per-type device list. */
    public static class DeviceMapping<T> implements Iterable<T> {
        private final List<T> devices = new ArrayList<T>();

        public void put(String deviceName, T device) {
            devices.add(device);
        }

        @Override
        public Iterator<T> iterator() {
            return devices.iterator();
        }
    }
}
