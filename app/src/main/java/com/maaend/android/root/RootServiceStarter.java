package com.maaend.android.root;

import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

public final class RootServiceStarter {

    private static final int DESTROY_TRANSACTION_CODE = 16777115;

    private RootServiceStarter() {
    }

    public static void main(String[] args) {
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }

        RootUserService.CreatedService createdService = RootUserService.create(args);
        if (createdService == null) {
            System.exit(1);
            return;
        }

        if (!sendBinder(createdService)) {
            System.exit(1);
            return;
        }

        Looper.loop();
        System.exit(0);
    }

    private static boolean sendBinder(RootUserService.CreatedService createdService) {
        IBinder lifecycleBinder = RootServiceBootstrapClient.attachRemoteService(
                createdService.packageName(),
                createdService.userId(),
                createdService.token(),
                createdService.service()
        );
        if (lifecycleBinder == null) {
            return false;
        }

        try {
            lifecycleBinder.linkToDeath(() -> {
                destroyService(createdService.service());
                System.exit(0);
            }, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void destroyService(IBinder service) {
        if (service == null || !service.pingBinder()) {
            return;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            String descriptor = service.getInterfaceDescriptor();
            if (descriptor != null) {
                data.writeInterfaceToken(descriptor);
            }
            service.transact(DESTROY_TRANSACTION_CODE, data, reply, Binder.FLAG_ONEWAY);
        } catch (Throwable ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
