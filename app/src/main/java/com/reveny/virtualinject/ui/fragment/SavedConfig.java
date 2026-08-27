package com.reveny.virtualinject.ui.fragment;

import org.json.JSONException;
import org.json.JSONObject;

public class SavedConfig {
    public String packageName;
    public String libraryPath;
    public String library2Path; // اختياري

    public SavedConfig(String packageName, String libraryPath, String library2Path) {
        this.packageName = packageName;
        this.libraryPath = libraryPath;
        this.library2Path = library2Path;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("packageName", packageName);
        obj.put("libraryPath", libraryPath);
        if (library2Path != null) obj.put("library2Path", library2Path);
        return obj;
    }

    public static SavedConfig fromJson(JSONObject obj) throws JSONException {
        String lib2 = obj.has("library2Path") ? obj.getString("library2Path") : null;
        return new SavedConfig(
            obj.getString("packageName"),
            obj.getString("libraryPath"),
            lib2
        );
    }
}
