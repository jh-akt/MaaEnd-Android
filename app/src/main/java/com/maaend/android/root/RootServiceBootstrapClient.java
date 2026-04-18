package com.maaend.android.root;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

public final class RootServiceBootstrapClient {

    private RootServiceBootstrapClient() {
    }

    public static IBinder attachRemoteService(String packageName, int userId, String token, IBinder serviceBinder) {
        String authority = packageName + RootServiceBootstrapRegistry.AUTHORITY_SUFFIX;
        IBinder providerToken = new Binder();
        ActivityManagerBridge bridge = ActivityManagerBridge.create();
        Object provider = null;

        try {
            provider = bridge.getContentProviderExternal(authority, userId, providerToken, authority);
            if (provider == null) {
                return null;
            }

            Bundle extras = new Bundle();
            extras.putString(RootServiceBootstrapRegistry.KEY_TOKEN, token);
            extras.putBinder(RootServiceBootstrapRegistry.KEY_SERVICE_BINDER, serviceBinder);

            Bundle reply = RootIContentProviderCompat.call(
                    provider,
                    authority,
                    RootServiceBootstrapRegistry.METHOD_ATTACH_REMOTE_SERVICE,
                    extras
            );
            if (reply == null) {
                return null;
            }

            IBinder lifecycleBinder = reply.getBinder(RootServiceBootstrapRegistry.KEY_APP_BINDER);
            if (lifecycleBinder == null || !lifecycleBinder.pingBinder()) {
                return null;
            }
            return lifecycleBinder;
        } catch (Throwable ignored) {
            return null;
        } finally {
            bridge.removeContentProviderExternal(authority, providerToken);
        }
    }
}
