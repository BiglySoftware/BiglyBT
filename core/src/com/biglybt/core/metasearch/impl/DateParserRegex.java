/*
 * Created on May 6, 2008
 * Created by Paul Gardner
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.core.metasearch.impl;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.core.util.SystemTime;

public class DateParserRegex extends DateParser {

	static boolean DEBUG = false;

	TimeZone timeZone;
	DateFormat userDateFormat;
	boolean auto;

	private static final Pattern hasLettersPattern = Pattern.compile("(?i).*[a-z]");
	private static final Pattern isAgeBasedPattern = Pattern.compile("(?i)(ago)|(min)|(hour)|(day)|(week)|(month)|(year)|([0-9](h|d|w|m|y))");
	private static final Map<String, Pattern> isAgeBasedPatternCN = new HashMap<>();
	private static final Pattern getTimeComponent = Pattern.compile("(?i)([0-9]{2}):([0-9]{2})(:([0-9]{2}))?( ?(a|p)m)?");
	private static final Pattern timeBasedDateWithLettersPattern = Pattern.compile("(?i)([0-9]{1,2})[^ ]{0,2}(?: |-)([a-z]{3,10})\\.?(?: |-)?([0-9]{2,4})?");
	private static final Pattern timeBasedDateWithLettersPatternMonthFirst = Pattern.compile("(?i)([a-z]{3,10})\\.?(?: |-)?([0-9]{1,2})[^ ]{0,2}(?: |-)([0-9]{2,4})?");
	private static final Pattern todayPattern = Pattern.compile("(?i)(t.?day)");
	private static final Pattern yesterdayPattern = Pattern.compile("(?i)(y[a-z\\-]+day)");
	private static final Pattern agoSpacerPattern = Pattern.compile("(?i)([0-9])([a-z])");
	private static final Pattern agoTimeRangePattern = Pattern.compile("(?i)([0-9.]+) ([a-z\\(\\)]+)");
	private static final Pattern numbersOnlyDatePattern = Pattern.compile("([0-9]{2,4})[ \\-\\./]([0-9]{2,4})[ \\-\\./]?([0-9]{2,4})?");

	private static final String[] MONTHS_LIST = new String[] {
		" january janvier enero januar",
		" february fevrier f\u00e9vrier febrero februar",
		" march mars marzo marz marz m\u00e4rz" ,
		" april avril abril april ",
		" may mai mayo mai",
		" june juin junio juni",
		" july juillet julio juli",
		" august aout ao\u00fbt agosto august",
		" september septembre septiembre september",
		" october octobre octubre oktober",
		" november novembre noviembre november",
		" december decembre d\u00e9cembre diciembre dezember"};

	static {
		isAgeBasedPatternCN.put("min", Pattern.compile("([0-9]+)\\s*\u5206\u949f\u524d"));
		isAgeBasedPatternCN.put("hour", Pattern.compile("([0-9]+)\\s*\u5c0f\u65f6\u524d"));
		isAgeBasedPatternCN.put("day", Pattern.compile("([0-9]+)\\s*\u5929\u524d"));
		isAgeBasedPatternCN.put("week", Pattern.compile("([0-9]+)\\s*\u5468\u524d"));
		isAgeBasedPatternCN.put("month", Pattern.compile("([0-9]+)\\s*\u4e2a\u6708\u524d"));
		isAgeBasedPatternCN.put("year", Pattern.compile("([0-9]+)\\s*\u5e74\u524d"));
	}

	public DateParserRegex() {
		this("GMT-7",true,null);
	}

	public DateParserRegex(String timeZone,boolean auto, String dateFormat) {

		this.timeZone = TimeZone.getTimeZone(timeZone);
		this.auto = auto;

		if(!auto) {
			if(dateFormat != null) {
				userDateFormat = new SimpleDateFormat(dateFormat);
				userDateFormat.setTimeZone(this.timeZone);
			} else {
				//TODO : in debug mode throw an Exception telling the user he needs to provide a dateFormat in manual mode
			}
		}

	}

	@Override
	public Date parseDate(String date) {
		Date result =  null;
		if(auto) {
			 result = parseDateInternal(date);
		} else {
			if(userDateFormat != null) {
				try {
					result = userDateFormat.parse(date);
				} catch(Exception e) {
					//TODO : in debug mode, throw an exception to tell the user that his dateFormat is invalid / didn't parse a date
				}
			}
		}
		if(DEBUG && result != null) {
			System.out.println(date + " > " + result.toString());
		}

		return result;
	}

	private Date parseDateInternal(final String input) {

		if(input == null) {
			return null;
		}

		String s = input;

		Calendar calendar = new GregorianCalendar(timeZone);

		//Find if there is any time information in the date
		Matcher matcher = getTimeComponent.matcher(s);
		//Remove the time information in order to not confuse the date parsing
		s = matcher.replaceFirst("").trim();

			// handle date with format "2009-01-12 at 03:36:38" by removing trailing " at";

		if ( s.endsWith( " at" )){

			s = s.substring(0,s.length()-3).trim();
		}


		//Find if the date contains letters
		matcher = hasLettersPattern.matcher(s);
		if(matcher.find()) {
			//We have a date with letters, could be age-based or time based (with a literal month)

			//Try to determine if it is age-based or time-based
			matcher = isAgeBasedPattern.matcher(s);
			if(matcher.find()) {
				//Age Based date

				matcher = todayPattern.matcher(s);
				if(matcher.find()) {
					//Nothing to do for today as we base our initial date on the current one
				} else {
					matcher = yesterdayPattern.matcher(s);
					if(matcher.find()) {
						calendar.add(Calendar.DATE, -1);
					} else {
						//We're in the real "ago" case, let's remove " ago" if it's there
						s = s.replaceAll("ago","").trim();
						matcher = agoSpacerPattern.matcher(s);
						s = matcher.replaceAll("$1 $2");
						matcher = agoTimeRangePattern.matcher(s);
						boolean seenHoursAsLowerCaseH = false;
						while(matcher.find()) {
							String unit = matcher.group(2);

							if(unit.equals("h")) {
								seenHoursAsLowerCaseH = true;
							}


							float value = Float.parseFloat(matcher.group(1));
							int intValue = (int) value;
							adjustDate(calendar, unit, value, intValue, seenHoursAsLowerCaseH);
						}

					}

				}

				//System.out.println(input + " > " + calendar.getTime());

			} else {

				//Time based date
				//System.out.println("DL : " + s);
				matcher = timeBasedDateWithLettersPattern.matcher(s);
				if(matcher.find()) {
					int day = Integer.parseInt(matcher.group(1));
					calendar.set(Calendar.DAY_OF_MONTH,day);

					String monthStr = " " + matcher.group(2).toLowerCase();
					int month = -1;
					for(int i = 0 ; i < MONTHS_LIST.length ; i++) {
						if(MONTHS_LIST[i].contains(monthStr)) {
							month = i;
						}
					}
					if(month > -1) {
						calendar.set(Calendar.MONTH,month);
					}

					boolean hasYear = matcher.group(3) != null;
					if(hasYear) {
						int year = Integer.parseInt(matcher.group(3));
						if(year < 100) {
							year += 2000;
						}
						calendar.set(Calendar.YEAR,year);
					}

					calendar.set(Calendar.HOUR_OF_DAY,0);
					calendar.set(Calendar.MINUTE,0);
					calendar.set(Calendar.SECOND,0);
					calendar.set(Calendar.MILLISECOND,0);

					//System.out.println(input + " > " + calendar.getTime() + "( " + calendar.getTimeZone() + " )");

				} else {
					matcher = timeBasedDateWithLettersPatternMonthFirst.matcher(s);
					if(matcher.find()) {
						int day = Integer.parseInt(matcher.group(2));
						calendar.set(Calendar.DAY_OF_MONTH,day);

						String monthStr = " " + matcher.group(1).toLowerCase();
						int month = -1;
						for(int i = 0 ; i < MONTHS_LIST.length ; i++) {
							if(MONTHS_LIST[i].contains(monthStr)) {
								month = i;
							}
						}
						if(month > -1) {
							calendar.set(Calendar.MONTH,month);
						}

						boolean hasYear = matcher.group(3) != null;
						if(hasYear) {
							int year = Integer.parseInt(matcher.group(3));
							if(year < 100) {
								year += 2000;
							}
							calendar.set(Calendar.YEAR,year);
						}

						calendar.set(Calendar.HOUR_OF_DAY,0);
						calendar.set(Calendar.MINUTE,0);
						calendar.set(Calendar.SECOND,0);
						calendar.set(Calendar.MILLISECOND,0);

						//System.out.println(input + " > " + calendar.getTime() + "( " + calendar.getTimeZone() + " )");

					} else {
						Date d = new DateParserClassic().parseDate( input );

						if ( d != null ){
							return( d );
						}

						System.err.println("DateParserRegex: Unparseable date : " + input);
					}
				}
			}
		} else {
			for (String unit: isAgeBasedPatternCN.keySet()) {
				Pattern p = isAgeBasedPatternCN.get(unit);
				matcher = p.matcher(s);
				if (matcher.find()) {
					try {
						int intValue = Integer.parseInt(matcher.group(1));
						adjustDate(calendar, unit, intValue, intValue, false);
						//System.out.println("found " + unit + ";" + intValue + ";" + new SimpleDateFormat().format(calendar.getTime()));
						return calendar.getTime();
					} catch (Throwable t) {
						t.printStackTrace();
					}
				}
			}


			//We have a date with only numbers
			//System.out.println("DN : " + s );//+ "(" + input + ")");
			//Let's assume a default order of m/d and switch if it doesn't make sense
			matcher = numbersOnlyDatePattern.matcher(s);
			if(matcher.find()) {
				try {

					String g1 = matcher.group(1);
					String g2 = matcher.group(2);
					String g3 = matcher.group(3);

					int i1 = Integer.parseInt(g1);
					int i2 = Integer.parseInt(g2);

					if(g3 != null) {
						int i3 = Integer.parseInt(g3);

						int day = i1;
						int month = i2;
						int year = i3;

						if(month > 12) {
							day = i2;
							month = i1;
						}

						if(year < 100) {
							year += 2000;
						}

						if(g1.length() == 4) {
							year = i1;
							day = i3;
						}

						calendar.set(Calendar.YEAR,year);
						calendar.set(Calendar.MONTH,month-1);
						calendar.set(Calendar.DAY_OF_MONTH,day);

					} else {
						//2 numbers only, we assume it's day and month
						int month = i1;
						int day = i2;
						if(month > 12) {
							day = i1;
							month = i2;
						}
						if(month > 12) {
							//TODO : fire an exception ?
							System.err.println("DateParserRegex: Unparseable date : " + input);
						} else {
							calendar.set(Calendar.MONTH, month-1);
							calendar.set(Calendar.DAY_OF_MONTH, day);
						}
					}

					calendar.set(Calendar.HOUR_OF_DAY,0);
					calendar.set(Calendar.MINUTE,0);
					calendar.set(Calendar.SECOND,0);
					calendar.set(Calendar.MILLISECOND,0);

				} catch (Exception e) {
					e.printStackTrace();
				}

			} else {
				try {
					long parseLong = Long.parseLong(s);
					if (parseLong < SystemTime.getCurrentTime() / 1000) {
						// likely time in seconds, but could be a time close to epoch
						parseLong *= 1000;
					}
					calendar.setTimeInMillis(parseLong);
				} catch (Throwable t) {
				}
			}

			//System.out.println(input + " > " + calendar.getTime());
		}

		//Extract the time information
		matcher = getTimeComponent.matcher(input);
		if(matcher.find()) {
			try {
				int hours = Integer.parseInt(matcher.group(1));

				int minutes = Integer.parseInt(matcher.group(2));
				calendar.set(Calendar.MINUTE,minutes);

				boolean amPMModifier = matcher.group(5) !=  null;

				boolean hasSeconds = matcher.group(4) != null;

				if(hasSeconds) {
					int seconds = Integer.parseInt(matcher.group(4));
					calendar.set(Calendar.SECOND, seconds);
				}

				if(amPMModifier) {
					String amPm = matcher.group(5).trim().toLowerCase();
					if(amPm.equals("am")) {
						calendar.set(Calendar.AM_PM,Calendar.AM);
					} else {
						calendar.set(Calendar.AM_PM,Calendar.PM);
					}
					calendar.set(Calendar.HOUR, hours);

				} else {
					calendar.set(Calendar.HOUR_OF_DAY, hours);
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}

		int nbBack = 0;
		Calendar calendarCompare = new GregorianCalendar();

//		if(calendar.after(calendarCompare)) {
//			System.err.println("maintenant ici: "+calendarCompare.getTimeInMillis()+"  --  "+calendarCompare.getTime()+"  --  "+calendarCompare);
//			System.err.println("maintenant la bas: "+calendar.getTimeInMillis()+"  --  "+calendar.getTime()+"  --  "+calendar);
//			System.err.println("CALENDARS: after? " + calendar.after(calendarCompare) + " // before? " + calendar.before(calendarCompare));
//		}
		while(calendar.after(calendarCompare) && nbBack++ < 50) {
			calendar.add(Calendar.YEAR, -1);
		}

		//calendar.setTimeZone(TimeZone.getDefault());
		return calendar.getTime();
	}


	private void adjustDate(Calendar calendar, String unit, float value, int intValue, boolean seenHoursAsLowerCaseH) {
		String lUnit = unit.toLowerCase();
		if(lUnit.startsWith("sec")) {
			calendar.add(Calendar.SECOND, -intValue);
		} else if(lUnit.startsWith("min") || (unit.equals("m") && seenHoursAsLowerCaseH)) {
			calendar.add(Calendar.MINUTE, -intValue);
			int seconds = (int) ((value - intValue)*60f);
			calendar.add(Calendar.SECOND, -seconds);
		} else if(lUnit.startsWith("h")) {
			calendar.add(Calendar.HOUR_OF_DAY, -intValue);
			int seconds = (int) ((value - intValue)*3600f);
			calendar.add(Calendar.SECOND, -seconds);
		} else if(lUnit.startsWith("d")) {
			calendar.add(Calendar.DATE, -intValue);
			int seconds = (int) ((value - intValue)*86400f);
			calendar.add(Calendar.SECOND, -seconds);
		} else if(lUnit.startsWith("w")) {
			calendar.add(Calendar.WEEK_OF_YEAR, -intValue);
			//604800 seconds in a week
			int seconds = (int) ((value - intValue)*640800f);
			calendar.add(Calendar.SECOND, -seconds);
		} //The month case when m is not a minute
		  else if(lUnit.startsWith("m")) {
			calendar.add(Calendar.MONTH, -intValue);
			//about 720 hours per month
			int hours = (int) ((value - intValue)*720f);
			calendar.add(Calendar.HOUR_OF_DAY, -hours);
		} else if(lUnit.startsWith("y")) {
			calendar.add(Calendar.YEAR, -intValue);
			//about 8760 hours per year
			int hours = (int) ((value - intValue)*8760);
			calendar.add(Calendar.HOUR_OF_DAY, -hours);
		} else {
			//System.out.println("Unit not matched : " + unit);
		}
	}

	public static void main(String args[]) {
		DEBUG = true;
		DateParserRegex dateParser = new DateParserRegex();

		dateParser.parseDate("Today 05:34");
		dateParser.parseDate("Y-Day 21:55");
		dateParser.parseDate("07-25 2006");
		dateParser.parseDate("02-01 02:53");
		dateParser.parseDate("02-01 02:53 am");
		dateParser.parseDate("02-01 02:53 pm");
		dateParser.parseDate("03 Mar 2006");
		dateParser.parseDate("0 minute ago");
		dateParser.parseDate("3 hours ago");
		dateParser.parseDate("2 days ago");
		dateParser.parseDate("10 months ago");
		dateParser.parseDate("45 mins ago");
		dateParser.parseDate("Today");
		dateParser.parseDate("Yesterday");
		dateParser.parseDate("16.9w");
		dateParser.parseDate("22.6h");
		dateParser.parseDate("1.7d");
		dateParser.parseDate("2d 7h");
		dateParser.parseDate("1w");
		dateParser.parseDate("1w 4d");
		dateParser.parseDate("1mon 1w");
		dateParser.parseDate("22.11.");		// 22 nov
		dateParser.parseDate("22 Apr 08");	//
		dateParser.parseDate("3 months");	//
		dateParser.parseDate("1 day");		//
		dateParser.parseDate("3 weeks");	//
		dateParser.parseDate("1 year");		//
		dateParser.parseDate("4 hours ago");	//
		dateParser.parseDate("yesterday");	//
		dateParser.parseDate("2 days ago");	//
		dateParser.parseDate("1 month ago");	//
		dateParser.parseDate("2 months ago");	//
		dateParser.parseDate("06/18");		// 18 Jun
		dateParser.parseDate("02:10");		// at 2:10am today
		dateParser.parseDate("2005-02-26 20:55:10");	//
		dateParser.parseDate("2005-02-26 10:55:10 PM");
		dateParser.parseDate("2005-02-26 10:55:10 AM");
		dateParser.parseDate("25-04-08");	//
		dateParser.parseDate("142 Day(s) ago");	//
		dateParser.parseDate("6 Minute(s) ago");	//
		dateParser.parseDate("1 Hour(s) ago");	//
		dateParser.parseDate("1.4h");		// 1.4 hours ago
		dateParser.parseDate("3.5d");		// 3 and a half days ago
		dateParser.parseDate("392w");		// 392 weeks ago
		dateParser.parseDate("01st Mar");	//
		dateParser.parseDate("19th Apr");	//
		dateParser.parseDate("03rd Apr");	//
		dateParser.parseDate("2nd Apr");	//
		dateParser.parseDate("3rd Nov");	//
		dateParser.parseDate("04-28");		//
		dateParser.parseDate("2007-07-14");	//
		dateParser.parseDate("2008.04.28");	//
		dateParser.parseDate("16/04/08");	//
		dateParser.parseDate("20-Dec-07");	//
		dateParser.parseDate("2009-01-12 at 03:36:38" );
		dateParser.parseDate("2013-08-11T18:30:00.000Z" );
		dateParser.parseDate("12\u5C0F\u65F6\u524D");
		dateParser.parseDate("12 \u5C0F\u65F6\u524D");
	}


}
