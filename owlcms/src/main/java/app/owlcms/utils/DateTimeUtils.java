/*******************************************************************************
 * Copyright (c) 2009-2021 Jean-François Lamy
 *
 * Licensed under the Non-Profit Open Software License version 3.0  ("NPOSL-3.0")
 * License text at https://opensource.org/licenses/NPOSL-3.0
 *******************************************************************************/
package app.owlcms.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;

import org.slf4j.LoggerFactory;

import app.owlcms.i18n.Translator;
import app.owlcms.init.OwlcmsSession;
import ch.qos.logback.classic.Logger;

public class DateTimeUtils {
    
    static Logger logger = (Logger) LoggerFactory.getLogger(DateTimeUtils.class);

    public static LocalDateTime parseExcelFractionalDate(String competitionTime) throws NumberFormatException {
        //logger.debug("parseExcelDateTime {} {}", competitionTime);
        double doubleDays = Double.parseDouble(competitionTime);
        //logger.debug("parseExcelDateTime {} {}", competitionTime, doubleDays);
        long minutes = (long) (doubleDays * 24 * 60);
        return LocalDateTime
                .of(1899, Month.DECEMBER, 30, 0, 0) // Specify epoch reference date used by *some* versions of Excel.
                                                    // Beware: Some versions use a 1904 epoch reference
                .plusMinutes(minutes);
    }

    public static LocalDate parseLocalizedOrISO8601Date(String content) throws Exception {
        Locale locale = OwlcmsSession.getLocale();
        // try local date format but force 4-digit years.
        String shortPattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT,
                null,
                IsoChronology.INSTANCE,
                locale);
        // force 4 digit year.
        if (shortPattern.contains("y") && !shortPattern.contains("yy")) {
            shortPattern = shortPattern.replace("y", "yyyy");
        } else if (shortPattern.contains("yy") && !shortPattern.contains("yyy")) {
            shortPattern = shortPattern.replace("yy", "yyyy");
        }
        DateTimeFormatter shortStyleFormatter = DateTimeFormatter.ofPattern(shortPattern, locale);
        try {
            // try as a local date
            LocalDate parse = LocalDate.parse(content, shortStyleFormatter);
            return parse;
        } catch (DateTimeParseException e1) {
            try {
                // try as a ISO Date
                LocalDate parse = LocalDate.parse(content, DateTimeFormatter.ISO_LOCAL_DATE);
                return parse;
            } catch (DateTimeParseException e2) {
                LocalDate sampleDate = LocalDate.of(1961, 02, 28);
                String message = Translator.translate(
                        "Upload.WrongDateFormat",
                        content,
                        locale.getDisplayName(locale),
                        shortPattern,
                        shortStyleFormatter.format(sampleDate),
                        "yyyy-MM-dd",
                        DateTimeFormatter.ISO_LOCAL_DATE.format(sampleDate));
                throw new Exception(message);
            }
        }
    }

    /**
     * Parse an Excel date, not knowing whether a 4-digit year, an ISO format date, or a native Excel days elapsed format is present
     * 
     * @param content something that we try to interpret as a date
     * @return
     * @throws Exception
     */
    public static LocalDate parseExcelDate(String content) throws Exception { //logger.debug("parseExcelDate {}", content);
        try {
            long l = Long.parseLong(content);
            if (l < 3000) {
                // year without month and day typed directly.
                return LocalDate.of((int) l, 1, 1);
            } else {
                // an Excel date in days elapsed since 1900 format
                LocalDateTime ldt = parseExcelFractionalDate(content);
                return ldt.toLocalDate();
            }
        } catch (NumberFormatException e) {
            // not a long
            try {
                LocalDateTime ldt = parseExcelFractionalDate(content);
                LocalDate localDate = ldt.toLocalDate();
                //logger.debug("parsed date {} {}",content,localDate);
                return localDate;
            } catch (NumberFormatException e1) {
                LocalDate parse = DateTimeUtils.parseLocalizedOrISO8601Date(content);
                return parse;
            }
        }
    }
    
    public static Date dateFromLocalDate(LocalDate ld) {
        Instant instant = ld.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Date date = Date.from(instant);
        return date;
    }
    
    public static Date dateFromLocalDateTime(LocalDateTime ldt) {
        Instant instant = ldt.atZone(ZoneId.systemDefault()).toInstant();
        return Date.from(instant);
    }

}
