package com.zaka.xinghai.net;

import com.zaka.xinghai.model.Site;
import com.zaka.xinghai.util.U;

/**
 * 脚本站（JS/PY）扩展入口。
 * 纯自研客户端不嵌入 V8/Python 运行时：JS/PY 源若 api 仍是 http 采集地址则走 CMS；
 * 纯脚本文件会提示需要后续运行时，避免假解析。
 */
public final class Spider {

    private Spider() {
    }

    public static boolean canUseAsCms(Site site) {
        if (site == null || U.empty(site.api)) return false;
        String a = site.api.toLowerCase();
        // 常见采集路径
        if (a.contains("provide/vod") || a.contains("api.php") || a.contains("/api/")) return true;
        if (site.isJs() || site.isPy()) {
            // 纯脚本文件无法本地执行
            if (a.endsWith(".js") || a.endsWith(".py")) return false;
        }
        return a.startsWith("http") && !site.isScript();
    }

    public static String unsupportedHint(Site site) {
        if (site == null) return "站点无效";
        if (site.isJs()) return "「" + site.name + "」是 JS 源，当前版未内置 JS 引擎，请换 CMS 接口或等后续版本";
        if (site.isPy()) return "「" + site.name + "」是 PY 源，当前版未内置 Python 运行时，请换 CMS 接口";
        if (site.isScript()) return "「" + site.name + "」是脚本/CSP 源，纯自研端暂不执行第三方脚本";
        return "该站点暂不支持";
    }
}
