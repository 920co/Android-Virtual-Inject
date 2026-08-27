package com.reveny.virtualinject.ui.fragment;

import org.json.JSONException;
import org.json.JSONObject;

public class SavedConfig {
    public String packageName;
    public String libraryPath;

    public SavedConfig(String packageName, String libraryPath) {
        this.packageName = packageName;
        this.libraryPath = libraryPath;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("packageName", packageName);
        obj.put("libraryPath", libraryPath);
        return obj;
    }

    public static SavedConfig fromJson(JSONObject obj) throws JSONException {
        return new SavedConfig(
            obj.getString("packageName"),
            obj.getString("libraryPath")
        );
    }
}
