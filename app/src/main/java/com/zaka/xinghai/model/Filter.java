package com.zaka.xinghai.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 筛选条件（分类页的「类型 / 地区 / 年份」这些）。 */
public class Filter {

    public String key = "";
    public String name = "";
    public List<String> values = new ArrayList<String>();

    public Filter() {
    }

    public Filter(String key, String name, List<String> values) {
        this.key = key;
        this.name = name;
        this.values = values;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("key", key);
            o.put("name", name);
            JSONArray a = new JSONArray();
            for (String v : values) a.put(v);
            o.put("values", a);
        } catch (Throwable ignored) {
        }
        return o;
    }

    public static Filter from(JSONObject o) {
        Filter f = new Filter();
        f.key = o.optString("key", "");
        f.name = o.optString("name", "");
        JSONArray a = o.optJSONArray("values");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) f.values.add(a.optString(i));
        }
        return f;
    }
}
