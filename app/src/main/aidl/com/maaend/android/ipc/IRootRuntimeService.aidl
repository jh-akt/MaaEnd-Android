package com.maaend.android.ipc;

interface IRootRuntimeService {
    String ping();
    boolean prepareRuntime();
    boolean startRun(String runRequestJson);
    void stopRun();
    String getState();
    String exportDiagnostics();
    oneway void destroy();
}
