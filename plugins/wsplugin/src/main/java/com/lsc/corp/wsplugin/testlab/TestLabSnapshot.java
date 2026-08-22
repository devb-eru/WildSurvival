package com.lsc.corp.wsplugin.testlab;

import com.lsc.corp.wsplugin.run.RunSnapshot;

public final class TestLabSnapshot {
    public int schemaVersion = 1;
    public String snapshotId;
    public String runId;
    public String actorUuid;
    public String reason;
    public long sequence;
    public long createdAtEpochMs;
    public RunSnapshot run;
    public TestPlayerBackup player;
}
