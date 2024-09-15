package me.catand.trpgbot.utils;

import me.catand.trpgbot.plugins.WordCloudPlugin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class DateUtils {
    public static boolean isUnixTimeToday(long unixTime) {
        // 将 Unix 时间戳转换为 LocalDate
        LocalDate date = Instant.ofEpochSecond(unixTime)
                                 .atZone(ZoneId.of(WordCloudPlugin.ZONE))
                                 .toLocalDate();

        // 获取今天的日期
        LocalDate today = LocalDate.now();

        // 比较两个日期是否相同
        return date.isEqual(today);
    }
}