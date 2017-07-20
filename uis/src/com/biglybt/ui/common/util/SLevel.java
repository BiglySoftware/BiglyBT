/* Written and copyright 2001-2003 Tobias Minich.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 *
 *
 * SLevel.java
 *
 * Created on 16. August 2003, 23:16
 */

package com.biglybt.ui.common.util;

import org.apache.log4j.Level;
/**
 *
 * @author  Tobias Minich
 */
public class SLevel extends Level {

  public final static int INT_CORE_WARNING = 11100;
  public final static int INT_CORE_INFO = 11000;
  public final static int INT_THREAD = 10001;
  public final static int INT_HTTP = 12000;
  public final static int INT_ACCESS_VIOLATION = 35000;

  public static final Level CORE_WARNING = new SLevel(INT_CORE_WARNING, "CORE WARNING", 6);
  public static final Level CORE_INFO = new SLevel(INT_CORE_INFO, "CORE INFO", 6);
  public static final Level THREAD = new SLevel(INT_THREAD, "THREAD", 6);
  public static final Level HTTP = new SLevel(INT_HTTP, "HTTP", 6);
  public static final Level ACCESS_VIOLATION = new SLevel(INT_ACCESS_VIOLATION, "ACCESS VIOLATION", 6);

  SLevel(int c, String a, int b) {super(c,a,b);}

  public static Level toLevel(int val) {
    return toLevel(val, Level.DEBUG);
  }

  public static Level toLevel(int val, Level defaultLevel) {
    switch (val) {
      case INT_CORE_WARNING: return SLevel.CORE_WARNING;
      case INT_CORE_INFO: return SLevel.CORE_INFO;
      case INT_THREAD: return SLevel.THREAD;
      case INT_HTTP: return SLevel.HTTP;
      case INT_ACCESS_VIOLATION: return SLevel.ACCESS_VIOLATION;
      default: return Level.toLevel(val, defaultLevel);
    }
  }
}
