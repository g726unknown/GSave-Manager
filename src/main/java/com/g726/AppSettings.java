package com.g726;

import java.util.HashMap;
import java.util.Map;

public class AppSettings {
    private boolean saveToLatestBranch = true;
    private Map<String, String> branchLimits = new HashMap<>();

    public boolean isSaveToLatestBranch() {
        return saveToLatestBranch;
    }

    public void setSaveToLatestBranch(boolean saveToLatestBranch) {
        this.saveToLatestBranch = saveToLatestBranch;
    }

    public Map<String, String> getBranchLimits() {
        return branchLimits;
    }

    public void setBranchLimits(Map<String, String> branchLimits) {
        this.branchLimits = branchLimits == null ? new HashMap<>() : branchLimits;
    }
}
