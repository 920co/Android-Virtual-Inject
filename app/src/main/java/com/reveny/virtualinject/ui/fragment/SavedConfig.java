package com.reveny.virtualinject.ui.fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SavedConfig {
    public String packageName;
    public List<String> libraryPaths;

    public SavedConfig(String packageName, List<String> libraryPaths) {
        this.packageName = packageName;
        this.libraryPaths = new ArrayList<>(libraryPaths);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("packageName", packageName);
        JSONArray arr = new JSONArray();
        for (String path : libraryPaths) arr.put(path);
        obj.put("libraryPaths", arr);
        return obj;
    }

    public static SavedConfig fromJson(JSONObject obj) throws JSONException {
        String pkg = obj.getString("packageName");
        JSONArray arr = obj.getJSONArray("libraryPaths");
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) paths.add(arr.getString(i));
        return new SavedConfig(pkg, paths);
    }
}
