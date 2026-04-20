package com.maaend.android.ipc;

import android.view.Surface;

interface IRootRuntimeService {
    String ping();
    boolean prepareRuntime();
    boolean startRun(String runRequestJson);
    void stopRun();
    void setMonitorSurface(in Surface surface);
    boolean startWindowedGame();
    boolean touchDown(int x, int y);
    boolean touchMove(int x, int y);
    boolean touchUp(int x, int y);
    int getWindowedDisplayId();
    void stopWindowedPreview();
    String getState();
    String exportDiagnostics();
    oneway void destroy();
}
