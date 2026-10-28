package com.hobozoo.aiqq.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * @author hbz
 */
public class DateUtil {

    static DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    static DateTimeFormatter dtf2 = DateTimeFormatter.ISO_DATE;
    static DateTimeFormatter dtf3 = DateTimeFormatter.ofPattern("uuuu年MM月dd日 HH时mm分ss秒");

    public static void m1(){
        String format = LocalDateTime.now().format(dtf3);
        LocalDateTime parse = LocalDateTime.parse("2020年05月01日 10时01分31秒", dtf3);
    }

    public static void main(String[] args) {
        // 查看时区
        ZoneId defaultZone = TimeZone.getDefault().toZoneId();
        TimeZone tz = TimeZone.getTimeZone(defaultZone);
        // 设置时区
        DateTimeFormatter dtf3 = DateTimeFormatter.ofPattern("uuuu年MM月dd日 HH时mm分ss秒");
        String format = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(dtf3);

        LocalDateTime parse = LocalDateTime.parse("2020年05月01日 10时01分31秒", dtf3);
        System.out.println(format);
    }
}
