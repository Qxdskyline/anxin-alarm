package cn.local.oneshotalarm;

import android.content.Context;
import android.net.Uri;

import org.json.*;

import java.io.*;
import java.net.*;

/** 调休日历文件和网络来源，只返回校验后的数据，不改变闹钟。 */
final class HolidayFiles {
    static JSONObject read(Context c, Uri uri) throws Exception {
        try (InputStream input = c.getContentResolver().openInputStream(uri)) {
            return parse(input);
        }
    }

    private static JSONObject parse(InputStream input) throws Exception {
        JSONObject file = new JSONObject(BoundedStreams.text(input, 1024 * 1024, "日历文件超过 1 MB"));
        HolidayCalendar.parseImport(file);
        return file;
    }

    static JSONObject download(int year) throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(
                                        "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/"
                                                + year
                                                + ".json")
                                .openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        try {
            int status = connection.getResponseCode();
            if (status == 404) throw new IOException("尚未获取到 " + year + " 年完整安排，请稍后再试");
            if (status != 200) throw new IOException("日历服务暂不可用（" + status + "）");
            JSONObject file;
            try (InputStream input = connection.getInputStream()) {
                file = parse(input);
            }
            if (file.getInt("year") != year) throw new IOException("日历年份不匹配");
            JSONArray papers = file.optJSONArray("papers");
            boolean official = false;
            if (papers != null) {
                for (int i = 0; i < papers.length(); i++) {
                    String host = new URI(papers.getString(i)).getHost();
                    if (host != null && (host.equals("gov.cn") || host.endsWith(".gov.cn")))
                        official = true;
                }
            }
            if (!official) throw new IOException("尚无正式文件依据，保留现有日历");
            return file;
        } finally {
            connection.disconnect();
        }
    }
}
