package com.antisahir.app;

public class AppInfo {
    private final String appName;
    private final String packageName;
    private boolean selected;

    public AppInfo(String appName, String packageName, boolean selected) {
        this.appName = appName;
        this.packageName = packageName;
        this.selected = selected;
    }

    public String getAppName()    { return appName; }
    public String getPackageName() { return packageName; }
    public boolean isSelected()   { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}
